package com.mvbar.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.debug.DebugLog
import okhttp3.OkHttpClient

class MvbarApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        DebugLog.installCrashHandler()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val builder = chain.request().newBuilder()
                        ApiClient.getToken()?.let {
                            builder.addHeader("Authorization", "Bearer $it")
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
