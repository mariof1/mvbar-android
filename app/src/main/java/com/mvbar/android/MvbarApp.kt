package com.mvbar.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mvbar.android.data.ActivityQueue
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.repository.AuthRepository
import com.mvbar.android.data.sync.SyncManager
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.AudioCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MvbarApp : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        DebugLog.installCrashHandler()
        AudioCacheManager.init(this)
        NetworkMonitor.init(this)

        // Restore API session early so PlaybackService (Android Auto) can access the server
        appScope.launch {
            try {
                val auth = AuthRepository(this@MvbarApp)
                auth.restoreSession()
            } catch (e: Exception) {
                DebugLog.e("App", "Failed to restore session at startup", e)
            }
        }

        // Initialize Room database
        MvbarDatabase.getInstance(this)

        // Initialize offline-resilient activity queue (flushes on reconnect)
        ActivityQueue.init(this)

        // Initialize sync manager and schedule periodic sync
        SyncManager.init(this)
        SyncManager.schedulePeriodic(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val builder = chain.request().newBuilder()
                        ApiClient.getToken()?.let { token ->
                            builder.addHeader("Authorization", "Bearer $token")
                            // /api/art/* is routed through Next.js which only reads cookies
                            builder.addHeader("Cookie", "mvbar_token=$token")
                        }
                        chain.proceed(builder.build())
                    }
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
