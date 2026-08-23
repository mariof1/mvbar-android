package com.mvbar.android.player

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class CacheDownloadPhase { DOWNLOADING, COMPLETE, FAILED }

data class CacheDownloadState(
    val phase: CacheDownloadPhase,
    val progress: Float? = null,
    val bytesCached: Long = 0,
    val contentLength: Long? = null,
    val error: String? = null
)

internal fun isCompleteCacheEntry(contentLength: Long?, cachedBytes: Long, rangeCached: Boolean): Boolean =
    contentLength != null && contentLength > 0 && cachedBytes >= contentLength && rangeCached

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioCacheManager {

    private const val PREFS_NAME = "audio_cache_prefs"
    private const val KEY_MAX_CACHE_MB = "max_cache_mb"
    private const val KEY_PREFETCH_COUNT = "prefetch_count"
    private const val KEY_WIFI_ONLY = "wifi_only_download"
    private const val KEY_AUTO_CACHE_FAVORITES = "auto_cache_favorites"
    private const val KEY_AUTO_CACHE_PODCASTS = "auto_cache_podcasts"

    @Volatile
    private var cache: SimpleCache? = null
    @Volatile
    private var prefs: SharedPreferences? = null
    @Volatile
    private var appContext: Context? = null
    private var prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefetchJob: Job? = null
    private var autoCacheJob: Job? = null
    private var podcastCacheJob: Job? = null
    private val manualDownloadJobs = ConcurrentHashMap<String, Job>()
    private val _downloadStates = MutableStateFlow<Map<String, CacheDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, CacheDownloadState>> = _downloadStates.asStateFlow()
    private val _cacheRevision = MutableStateFlow(0L)
    val cacheRevision: StateFlow<Long> = _cacheRevision.asStateFlow()

    val maxCacheMb: Int get() = prefs?.getInt(KEY_MAX_CACHE_MB, 500) ?: 500
    val prefetchCount: Int get() = prefs?.getInt(KEY_PREFETCH_COUNT, 3) ?: 3
    val wifiOnlyDownload: Boolean get() = prefs?.getBoolean(KEY_WIFI_ONLY, true) ?: true
    val autoCacheFavorites: Boolean get() = prefs?.getBoolean(KEY_AUTO_CACHE_FAVORITES, false) ?: false
    val autoCachePodcasts: Boolean get() = prefs?.getBoolean(KEY_AUTO_CACHE_PODCASTS, false) ?: false

    @Synchronized
    fun initPrefs(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    fun init(context: Context) {
        if (cache != null) return
        initPrefs(context)
        val ctx = appContext ?: context.applicationContext

        val maxBytes = maxCacheMb.toLong() * 1024 * 1024
        val newDir = File(ctx.filesDir, "audio_cache")
        // Migrate from cacheDir to filesDir (one-time)
        val oldDir = File(ctx.cacheDir, "audio_cache")
        if (oldDir.exists() && !newDir.exists()) {
            if (oldDir.renameTo(newDir)) {
                DebugLog.i("Cache", "Migrated audio cache from cacheDir to filesDir")
            }
        }
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val dbProvider = StandaloneDatabaseProvider(ctx)
        cache = SimpleCache(newDir, evictor, dbProvider)
        DebugLog.i("Cache", "Audio cache initialized (${maxCacheMb}MB max)")
    }

    fun warmUp(context: Context) {
        val ctx = context.applicationContext
        initPrefs(ctx)
        prefetchScope.launch {
            try {
                init(ctx)
            } catch (e: Exception) {
                DebugLog.e("Cache", "Audio cache warm-up failed", e)
            }
        }
    }

    fun getCache(): SimpleCache? = cache

    /** Check whether a music track's complete audio file is cached. */
    fun isTrackCached(trackId: Int): Boolean {
        return isUrlFullyCached(ApiClient.streamUrl(trackId))
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
        return keys.filter(::isUrlFullyCached).mapNotNull { key ->
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
        return cache?.keys?.filter(::isUrlFullyCached) ?: emptyList()
    }

    /**
     * Remove a single cached item by its stream URL key.
     */
    fun removeCachedItem(key: String) {
        try {
            manualDownloadJobs.remove(key)?.cancel()
            cache?.removeResource(key)
            _downloadStates.update { it - key }
            notifyCacheChanged()
            DebugLog.d("Cache", "Removed cached item: $key")
        } catch (e: Exception) {
            DebugLog.e("Cache", "Failed to remove cached item", e)
        }
    }

    /**
     * Get the cached size in bytes for a specific key (stream URL).
     */
    fun getCachedSizeBytes(key: String): Long {
        return cache?.getCachedSpans(key)?.sumOf { it.length } ?: 0L
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
                    if (isTrackCached(track.id)) continue
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
            if (isTrackCached(trackId)) return@launch
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
            manualDownloadJobs.values.forEach { it.cancel() }
            manualDownloadJobs.clear()
            cache?.keys?.toList()?.forEach { key ->
                cache?.removeResource(key)
            }
            _downloadStates.value = emptyMap()
            notifyCacheChanged()
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

    fun createCacheDataSourceFactory(
        context: Context,
        upstreamFactory: OkHttpDataSource.Factory
    ): DataSource.Factory {
        val ctx = context.applicationContext
        initPrefs(ctx)
        return DataSource.Factory {
            val c = try {
                init(ctx)
                cache
            } catch (e: Exception) {
                DebugLog.e("Cache", "Falling back to direct stream; cache unavailable", e)
                null
            }
            if (c != null) {
                CacheDataSource.Factory()
                    .setCache(c)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
            } else {
                upstreamFactory.createDataSource()
            }
        }
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
                if (isTrackCached(track.id)) continue
                val key = url

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
                if (isTrackCached(track.id)) continue

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
    private fun cacheUrl(
        c: SimpleCache,
        url: String,
        progressListener: CacheWriter.ProgressListener? = null
    ) {
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
        CacheWriter(cacheDataSourceFactory.createDataSource(), dataSpec, null, progressListener).cache()
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

                if (isEpisodeCached(epId)) continue

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

    /** Check whether a complete podcast episode is cached. */
    fun isEpisodeCached(episodeId: Int): Boolean {
        return isUrlFullyCached(ApiClient.episodeStreamUrl(episodeId))
    }

    /** Check whether an audiobook chapter is cached */
    fun isChapterCached(audiobookId: Int, chapterId: Int): Boolean {
        return isUrlFullyCached(ApiClient.audiobookChapterStreamUrl(audiobookId, chapterId))
    }

    fun trackDownloadKey(trackId: Int): String = ApiClient.streamUrl(trackId)

    fun episodeDownloadKey(episodeId: Int): String = ApiClient.episodeStreamUrl(episodeId)

    /** Explicit user download. Manual downloads intentionally ignore the Wi-Fi-only auto-cache setting. */
    fun downloadTrack(trackId: Int) = startManualDownload(trackDownloadKey(trackId), "track $trackId")

    /** Explicit user download. Manual downloads intentionally ignore the Wi-Fi-only auto-cache setting. */
    fun downloadEpisode(episodeId: Int) = startManualDownload(episodeDownloadKey(episodeId), "episode $episodeId")

    fun removeTrackDownload(trackId: Int) = removeCachedItem(trackDownloadKey(trackId))

    fun removeEpisodeDownload(episodeId: Int) = removeCachedItem(episodeDownloadKey(episodeId))

    private fun startManualDownload(url: String, label: String) {
        if (manualDownloadJobs[url]?.isActive == true) return
        val ctx = appContext ?: return
        val job = prefetchScope.launch {
            try {
                init(ctx)
                val c = cache ?: error("Audio cache is unavailable")
                if (isUrlFullyCached(url)) {
                    _downloadStates.update {
                        it + (url to CacheDownloadState(CacheDownloadPhase.COMPLETE, progress = 1f))
                    }
                    notifyCacheChanged()
                    return@launch
                }
                _downloadStates.update {
                    it + (url to CacheDownloadState(CacheDownloadPhase.DOWNLOADING, progress = 0f))
                }
                cacheUrl(c, url) { requestLength, bytesCached, _ ->
                    val length = requestLength.takeIf { it > 0 }
                    val progress = length?.let { (bytesCached.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
                    _downloadStates.update {
                        it + (url to CacheDownloadState(
                            phase = CacheDownloadPhase.DOWNLOADING,
                            progress = progress,
                            bytesCached = bytesCached,
                            contentLength = length
                        ))
                    }
                }
                if (!isUrlFullyCached(url)) {
                    error("Download ended before the complete file was cached")
                }
                val length = contentLength(url)
                _downloadStates.update {
                    it + (url to CacheDownloadState(
                        phase = CacheDownloadPhase.COMPLETE,
                        progress = 1f,
                        bytesCached = length ?: 0,
                        contentLength = length
                    ))
                }
                notifyCacheChanged()
                DebugLog.i("Cache", "Manual download complete: $label (${length ?: 0} bytes)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _downloadStates.update {
                    it + (url to CacheDownloadState(
                        phase = CacheDownloadPhase.FAILED,
                        error = e.message ?: "Download failed"
                    ))
                }
                notifyCacheChanged()
                DebugLog.e("Cache", "Manual download failed: $label", e)
            } finally {
                manualDownloadJobs.remove(url)
            }
        }
        manualDownloadJobs[url] = job
    }

    private fun contentLength(url: String): Long? {
        val c = cache ?: return null
        return androidx.media3.datasource.cache.ContentMetadata
            .getContentLength(c.getContentMetadata(url))
            .takeIf { it > 0 }
    }

    private fun isUrlFullyCached(url: String): Boolean {
        val c = cache ?: return false
        val length = contentLength(url) ?: return false
        return isCompleteCacheEntry(
            contentLength = length,
            cachedBytes = c.getCachedBytes(url, 0, length),
            rangeCached = c.isCached(url, 0, length)
        )
    }

    private fun notifyCacheChanged() {
        _cacheRevision.value = _cacheRevision.value + 1
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
        manualDownloadJobs.values.forEach { it.cancel() }
        manualDownloadJobs.clear()
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
