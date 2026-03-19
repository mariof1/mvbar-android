package com.mvbar.android.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.debug.DebugLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * BitmapLoader that fetches artwork through our authenticated OkHttp client.
 * Includes an in-memory LRU cache and negative cache for 404s.
 */
class AuthBitmapLoader : BitmapLoader {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            ApiClient.getToken()?.let { token ->
                builder.addHeader("Authorization", "Bearer $token")
                builder.addHeader("Cookie", "mvbar_token=$token")
            }
            chain.proceed(builder.build())
        }
        .build()

    // LRU bitmap cache — ~20MB budget (approx 25-50 artworks at 800x800)
    private val bitmapCache = LruCache<String, Bitmap>(20 * 1024 * 1024 / 4) // rough size in pixel count

    // Track URLs that returned errors so we don't re-fetch them (cleared on next app restart)
    private val failedUrls = ConcurrentHashMap<String, Long>()
    private val FAIL_CACHE_MS = 5 * 60 * 1000L // 5 minutes

    // In-flight requests — avoid duplicate concurrent fetches for the same URL
    private val inFlight = ConcurrentHashMap<String, SettableFuture<Bitmap>>()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) future.set(bitmap)
            else future.setException(IOException("Failed to decode bitmap"))
        } catch (e: Exception) {
            future.setException(e)
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val url = uri.toString()

        // Check memory cache first
        bitmapCache.get(url)?.let { cached ->
            val future = SettableFuture.create<Bitmap>()
            future.set(cached)
            return future
        }

        // Check negative cache
        failedUrls[url]?.let { failedAt ->
            if (System.currentTimeMillis() - failedAt < FAIL_CACHE_MS) {
                val future = SettableFuture.create<Bitmap>()
                future.setException(IOException("Cached failure for: $url"))
                return future
            } else {
                failedUrls.remove(url)
            }
        }

        // Deduplicate in-flight requests
        inFlight[url]?.let { existing ->
            if (!existing.isDone) return existing
        }

        val future = SettableFuture.create<Bitmap>()
        inFlight[url] = future

        DebugLog.d("BitmapLoader", "Loading artwork: $url")
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                DebugLog.e("BitmapLoader", "Failed to load artwork: $url", e)
                failedUrls[url] = System.currentTimeMillis()
                inFlight.remove(url)
                future.setException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "HTTP ${resp.code} loading artwork: $url"
                        DebugLog.e("BitmapLoader", msg)
                        failedUrls[url] = System.currentTimeMillis()
                        inFlight.remove(url)
                        future.setException(IOException(msg))
                        return
                    }
                    val bytes = resp.body?.bytes()
                    if (bytes == null) {
                        inFlight.remove(url)
                        future.setException(IOException("Empty body for artwork: $url"))
                        return
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        DebugLog.d("BitmapLoader", "Loaded artwork ${bitmap.width}x${bitmap.height}")
                        bitmapCache.put(url, bitmap)
                        inFlight.remove(url)
                        future.set(bitmap)
                    } else {
                        inFlight.remove(url)
                        future.setException(IOException("Failed to decode artwork"))
                    }
                }
            }
        })
        return future
    }
}
