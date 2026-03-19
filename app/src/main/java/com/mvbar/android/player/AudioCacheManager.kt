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
import com.mvbar.android.data.api.ApiClient
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

    private var cache: SimpleCache? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefetchJob: Job? = null
    private var autoCacheJob: Job? = null

    val maxCacheMb: Int get() = prefs?.getInt(KEY_MAX_CACHE_MB, 500) ?: 500
    val prefetchCount: Int get() = prefs?.getInt(KEY_PREFETCH_COUNT, 3) ?: 3
    val wifiOnlyDownload: Boolean get() = prefs?.getBoolean(KEY_WIFI_ONLY, true) ?: true
    val autoCacheFavorites: Boolean get() = prefs?.getBoolean(KEY_AUTO_CACHE_FAVORITES, false) ?: false

    fun init(context: Context) {
        if (cache != null) return
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val maxBytes = maxCacheMb.toLong() * 1024 * 1024
        val cacheDir = File(context.cacheDir, "audio_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val dbProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(cacheDir, evictor, dbProvider)
        DebugLog.i("Cache", "Audio cache initialized (${maxCacheMb}MB max)")
    }

    fun getCache(): SimpleCache? = cache

    fun getCacheSizeMb(): Long = (cache?.cacheSpace ?: 0) / (1024 * 1024)

    fun getCachedTrackCount(): Int {
        return cache?.keys?.size ?: 0
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
    }

    fun clearCache() {
        try {
            cache?.keys?.toList()?.forEach { key ->
                cache?.removeResource(key)
            }
            DebugLog.i("Cache", "Audio cache cleared")
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
            for (track in tracksToCache) {
                if (!isActive) break
                if (shouldSkipDownload()) break

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
                    cached++
                    DebugLog.d("Cache", "Auto-cached favorite: ${track.displayTitle}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.e("Cache", "Auto-cache failed: ${track.displayTitle}", e)
                }
            }
            if (cached > 0) DebugLog.i("Cache", "Auto-cached $cached favorite tracks")
        }
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
        cache?.release()
        cache = null
    }
}
