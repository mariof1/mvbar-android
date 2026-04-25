package com.mvbar.android.wear.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DataSource
import com.mvbar.android.wear.AuthTokenStore
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Single shared media cache (capped at 2 GB, LRU eviction). Used both for
 * streaming-with-cache (`CacheDataSource.Factory`) and for explicit
 * "Download" actions which preload bytes into the same cache so they
 * survive eviction only by recency, not by source.
 */
object MediaCache {

    const val MAX_BYTES: Long = 2L * 1024 * 1024 * 1024

    @Volatile private var simpleCache: SimpleCache? = null
    @Volatile private var http: OkHttpClient? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        simpleCache?.let { return it }
        val dir = File(context.applicationContext.filesDir, "media-cache").apply { mkdirs() }
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_BYTES)
        val db = StandaloneDatabaseProvider(context.applicationContext)
        return SimpleCache(dir, evictor, db).also { simpleCache = it }
    }

    fun usageBytes(context: Context): Long = get(context).cacheSpace

    fun clear(context: Context) {
        val cache = get(context)
        cache.keys.toList().forEach { cache.removeResource(it) }
    }

    private fun http(context: Context): OkHttpClient {
        http?.let { return it }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = AuthTokenStore.token(context.applicationContext)
                val req = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $token")
                        addHeader("Cookie", "mvbar_token=$token")
                    }
                }.build()
                chain.proceed(req)
            }
            .build()
            .also { http = it }
    }

    /** DataSource.Factory for ExoPlayer that auth-injects + writes to cache. */
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val upstream = OkHttpDataSource.Factory(http(context))
        return CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
