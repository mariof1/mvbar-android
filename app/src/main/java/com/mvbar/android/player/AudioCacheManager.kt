package com.mvbar.android.player

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.model.Track
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioCacheManager {

    private const val PREFS_NAME = "audio_cache_prefs"
    private const val KEY_MAX_CACHE_MB = "max_cache_mb"
    private const val KEY_PREFETCH_COUNT = "prefetch_count"
    private const val KEY_WIFI_ONLY = "wifi_only_download"
    private const val KEY_AUTO_CACHE_FAVORITES = "auto_cache_favorites"
    private const val KEY_AUTO_CACHE_PODCASTS = "auto_cache_podcasts"

    private var cache: SimpleCache? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefetchJob: Job? = null
    private var autoCacheJob: Job? = null
    private var podcastCacheJob: Job? = null

    val maxCacheMb: Int get() = prefs?.getInt(KEY_MAX_CACHE_MB, 500) ?: 500
    val prefetchCount: Int get() = prefs?.getInt(KEY_PREFETCH_COUNT, 3) ?: 3
    val wifiOnlyDownload: Boolean get() = prefs?.getBoolean(KEY_WIFI_ONLY, true) ?: true
    val autoCacheFavorites: Boolean get() = prefs?.getBoolean(KEY_AUTO_CACHE_FAVORITES, false) ?: false
    val autoCachePodcasts: Boolean get() = prefs?.getBoolean(KEY_AUTO_CACHE_PODCASTS, false) ?: false

    fun init(context: Context) {
        if (cache != null) return
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val maxBytes = maxCacheMb.toLong() * 1024 * 1024
        val newDir = File(context.filesDir, "audio_cache")
        // Migrate from cacheDir to filesDir (one-time)
        val oldDir = File(context.cacheDir, "audio_cache")
        if (oldDir.exists() && !newDir.exists()) {
            if (oldDir.renameTo(newDir)) {
                DebugLog.i("Cache", "Migrated audio cache from cacheDir to filesDir")
            }
        }
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val dbProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(newDir, evictor, dbProvider)
        DebugLog.i("Cache", "Audio cache initialized (${maxCacheMb}MB max)")
    }

    fun getCache(): SimpleCache? = cache

    /** Check whether a music track's audio is cached (fully or partially) */
    fun isTrackCached(trackId: Int): Boolean {
        val url = ApiClient.streamUrl(trackId)
        val c = cache ?: return false
        // isCached with MAX_VALUE fails when content-length metadata is missing
        if (c.isCached(url, 0, Long.MAX_VALUE)) return true
        // Fallback: check if any cached bytes exist for this key
        return c.getCachedBytes(url, 0, Long.MAX_VALUE) > 0
    }

    fun getCacheSizeMb(): Long = (cache?.cacheSpace ?: 0) / (1024 * 1024)

    fun getCachedTrackCount(): Int {
        return cache?.keys?.size ?: 0
    }

    /** Returns IDs of tracks whose audio is in the ExoPlayer cache. */
    fun getCachedTrackIds(): List<Int> {
        val keys = cache?.keys ?: return emptyList()
        val prefix = "api/library/tracks/"
        val suffix = "/stream"
        return keys.mapNotNull { key ->
            val start = key.indexOf(prefix)
            if (start < 0 || !key.endsWith(suffix)) return@mapNotNull null
            val idStr = key.substring(start + prefix.length, key.length - suffix.length)
            idStr.toIntOrNull()
        }
    }

    /**
     * Returns all cached content keys. Each key is a stream URL that can be
     * parsed back to a track/episode/chapter ID.
     */
    fun getCachedKeys(): List<String> {
        return cache?.keys?.toList() ?: emptyList()
    }

    /**
     * Remove a single cached item by its stream URL key.
     */
    fun removeCachedItem(key: String) {
        try {
            cache?.removeResource(key)
            DebugLog.d("Cache", "Removed cached item: $key")
        } catch (e: Exception) {
            DebugLog.e("Cache", "Failed to remove cached item", e)
        }
    }

    /**
     * Get the cached size in bytes for a specific key (stream URL).
     */
    fun getCachedSizeBytes(key: String): Long {
        val contentMetadata = cache?.getContentMetadata(key) ?: return 0L
        return androidx.media3.datasource.cache.ContentMetadata.getContentLength(contentMetadata)
            .takeIf { it > 0 } ?: cache?.getCachedBytes(key, 0, Long.MAX_VALUE) ?: 0L
    }

    fun setMaxCacheMb(mb: Int) {
        prefs?.edit()?.putInt(KEY_MAX_CACHE_MB, mb)?.apply()
        // Cache evictor will enforce on next write
    }

    fun setPrefetchCount(count: Int) {
        prefs?.edit()?.putInt(KEY_PREFETCH_COUNT, count)?.apply()
    }

    fun setWifiOnlyDownload(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_WIFI_ONLY, enabled)?.apply()
    }

    fun setAutoCacheFavorites(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_CACHE_FAVORITES, enabled)?.apply()
        if (enabled) reCacheFavorites()
    }

    /**
     * Re-cache all favorite tracks from Room DB.
     * Called when toggle is enabled, after cache clear, and from SyncWorker.
     */
    fun reCacheFavorites() {
        if (!autoCacheFavorites) return
        val ctx = appContext ?: return
        val c = cache ?: return
        autoCacheJob?.cancel()
        autoCacheJob = prefetchScope.launch {
            try {
                val db = MvbarDatabase.getInstance(ctx)
                val favTracks = db.favoriteDao().getFavorites()
                if (favTracks.isEmpty()) return@launch
                var cached = 0
                for (track in favTracks) {
                    if (!isActive) break
                    if (shouldSkipDownload()) break
                    val url = ApiClient.streamUrl(track.id)
                    if (cache?.isCached(url, 0, Long.MAX_VALUE) == true) continue
                    try {
                        cacheUrl(c, url)
                        cached++
                        DebugLog.d("Cache", "Auto-cached favorite: ${track.title}")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { DebugLog.e("Cache", "Auto-cache failed: ${track.title}", e) }
                }
                if (cached > 0) DebugLog.i("Cache", "Auto-cached $cached favorite tracks")
                // Pre-cache artwork for favorites
                for (track in favTracks) {
                    precacheArtworkById(track.id)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { DebugLog.e("Cache", "reCacheFavorites failed", e) }
        }
    }

    /** Cache a single track by ID (used when adding a new favorite). */
    fun cacheTrackById(trackId: Int) {
        val c = cache ?: return
        prefetchScope.launch {
            if (shouldSkipDownload()) return@launch
            val url = ApiClient.streamUrl(trackId)
            if (cache?.isCached(url, 0, Long.MAX_VALUE) == true) return@launch
            try {
                cacheUrl(c, url)
                precacheArtworkById(trackId)
                DebugLog.d("Cache", "Auto-cached new favorite track $trackId")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { DebugLog.e("Cache", "Cache track $trackId failed", e) }
        }
    }

    fun setAutoCachePodcasts(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_CACHE_PODCASTS, enabled)?.apply()
    }

    fun clearCache() {
        try {
            cache?.keys?.toList()?.forEach { key ->
                cache?.removeResource(key)
            }
            DebugLog.i("Cache", "Audio cache cleared")
            // Re-cache favorites if auto-cache is enabled
            reCacheFavorites()
        } catch (e: Exception) {
            DebugLog.e("Cache", "Clear cache failed", e)
        }
    }

    /**
     * Create a CacheDataSource.Factory that reads from cache first, writes through to cache.
     */
    fun createCacheDataSourceFactory(upstreamFactory: OkHttpDataSource.Factory): CacheDataSource.Factory {
        val c = cache ?: return CacheDataSource.Factory().setUpstreamDataSourceFactory(upstreamFactory)
        return CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Prefetch next N tracks in the queue from the given index.
     */
    fun prefetchNext(queue: List<Track>, currentIndex: Int) {
        prefetchJob?.cancel()
        val c = cache ?: return
        val count = prefetchCount
        if (count <= 0 || queue.isEmpty()) return

        val tracksToCache = queue.drop(currentIndex + 1).take(count)
        if (tracksToCache.isEmpty()) return

        prefetchJob = prefetchScope.launch {
            // Pre-cache artwork for upcoming tracks
            precacheArtwork(tracksToCache)
            for (track in tracksToCache) {
                if (!isActive) break
                if (shouldSkipDownload()) break
                if (track.id <= 0) continue  // skip invalid IDs (podcasts/audiobooks)

                val url = ApiClient.streamUrl(track.id)
                // Skip if already fully cached
                val key = url
                if (cache?.isCached(key, 0, Long.MAX_VALUE) == true) continue

                try {
                    DebugLog.d("Cache", "Prefetching track ${track.id}: ${track.displayTitle}")
                    val okClient = OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val builder = chain.request().newBuilder()
                            ApiClient.getToken()?.let {
                                builder.addHeader("Authorization", "Bearer $it")
                            }
                            chain.proceed(builder.build())
                        }
                        .build()
                    val dataSourceFactory = OkHttpDataSource.Factory(okClient)
                    val cacheDataSourceFactory = CacheDataSource.Factory()
                        .setCache(c)
                        .setUpstreamDataSourceFactory(dataSourceFactory)

                    val dataSpec = DataSpec.Builder()
                        .setUri(url)
                        .setKey(key)
                        .build()
                    val cacheWriter = CacheWriter(
                        cacheDataSourceFactory.createDataSource(),
                        dataSpec,
                        null,
                        null
                    )
                    cacheWriter.cache()
                    DebugLog.d("Cache", "Prefetched track ${track.id}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.e("Cache", "Prefetch failed for track ${track.id}", e)
                }
            }
        }
    }

    /**
     * Cache a list of tracks in the background (for favorites auto-cache).
     */
    fun cacheTracks(tracks: List<Track>) {
        val c = cache ?: return
        autoCacheJob?.cancel()
        autoCacheJob = prefetchScope.launch {
            var cached = 0
            for (track in tracks) {
                if (!isActive) break
                if (shouldSkipDownload()) break

                val url = ApiClient.streamUrl(track.id)
                if (cache?.isCached(url, 0, Long.MAX_VALUE) == true) continue

                try {
                    cacheUrl(c, url)
                    cached++
                    DebugLog.d("Cache", "Auto-cached favorite: ${track.displayTitle}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.e("Cache", "Auto-cache failed: ${track.displayTitle}", e)
                }
            }
            if (cached > 0) DebugLog.i("Cache", "Auto-cached $cached favorite tracks")
            // Pre-cache artwork for all tracks in the batch
            precacheArtwork(tracks)
        }
    }

    /** Download a URL into the cache. Must be called from a coroutine on IO. */
    private fun cacheUrl(c: SimpleCache, url: String) {
        val okClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                ApiClient.getToken()?.let {
                    builder.addHeader("Authorization", "Bearer $it")
                }
                chain.proceed(builder.build())
            }
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(okClient)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(dataSourceFactory)
        val dataSpec = DataSpec.Builder()
            .setUri(url)
            .setKey(url)
            .build()
        CacheWriter(cacheDataSourceFactory.createDataSource(), dataSpec, null, null).cache()
    }

    /**
     * Cache podcast episodes in the background (unplayed episodes from subscribed podcasts).
     * Each entry is a pair of (episodeId, streamUrl).
     */
    fun cacheEpisodes(episodes: List<Pair<Int, String>>) {
        val c = cache ?: return
        podcastCacheJob?.cancel()
        podcastCacheJob = prefetchScope.launch {
            var cached = 0
            for ((epId, url) in episodes) {
                if (!isActive) break
                if (shouldSkipDownload()) break

                if (cache?.isCached(url, 0, Long.MAX_VALUE) == true) continue

                try {
                    cacheUrl(c, url)
                    cached++
                    DebugLog.d("Cache", "Auto-cached episode $epId")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.e("Cache", "Episode cache failed: ep $epId", e)
                }
            }
            if (cached > 0) DebugLog.i("Cache", "Auto-cached $cached podcast episodes")
        }
    }

    /** Check whether a podcast episode is cached */
    fun isEpisodeCached(episodeId: Int): Boolean {
        val url = ApiClient.episodeStreamUrl(episodeId)
        val c = cache ?: return false
        if (c.isCached(url, 0, Long.MAX_VALUE)) return true
        return c.getCachedBytes(url, 0, Long.MAX_VALUE) > 0
    }

    /** Check whether an audiobook chapter is cached */
    fun isChapterCached(audiobookId: Int, chapterId: Int): Boolean {
        val url = ApiClient.audiobookChapterStreamUrl(audiobookId, chapterId)
        val c = cache ?: return false
        if (c.isCached(url, 0, Long.MAX_VALUE)) return true
        return c.getCachedBytes(url, 0, Long.MAX_VALUE) > 0
    }

    private fun shouldSkipDownload(): Boolean {
        if (!wifiOnlyDownload) return false
        val ctx = appContext ?: return false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun release() {
        prefetchJob?.cancel()
        autoCacheJob?.cancel()
        podcastCacheJob?.cancel()
        cache?.release()
        cache = null
    }

    /** Returns the artwork URL the UI would use for a given track. */
    private fun trackArtworkUrl(track: Track): String =
        track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)

    /** Pre-cache artwork into Coil's disk cache so it's available offline. */
    fun precacheArtwork(tracks: List<Track>) {
        val ctx = appContext ?: return
        val imageLoader = ctx.imageLoader
        var enqueued = 0
        for (track in tracks) {
            val request = ImageRequest.Builder(ctx)
                .data(trackArtworkUrl(track))
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()
            imageLoader.enqueue(request)
            enqueued++
        }
        if (enqueued > 0) DebugLog.d("Cache", "Pre-caching artwork for $enqueued tracks")
    }

    /** Pre-cache artwork for a single track by ID. */
    fun precacheArtworkById(trackId: Int) {
        val ctx = appContext ?: return
        val request = ImageRequest.Builder(ctx)
            .data(ApiClient.trackArtUrl(trackId))
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        ctx.imageLoader.enqueue(request)
    }
}
