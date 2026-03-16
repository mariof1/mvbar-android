package com.mvbar.android.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

/**
 * BitmapLoader that fetches artwork through our authenticated OkHttp client.
 * Art endpoints go via Caddy → Next.js which requires Cookie auth.
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
        val future = SettableFuture.create<Bitmap>()
        val url = uri.toString()
        DebugLog.d("BitmapLoader", "Loading artwork: $url")

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                DebugLog.e("BitmapLoader", "Failed to load artwork: $url", e)
                future.setException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "HTTP ${resp.code} loading artwork: $url"
                        DebugLog.e("BitmapLoader", msg)
                        future.setException(IOException(msg))
                        return
                    }
                    val bytes = resp.body?.bytes()
                    if (bytes == null) {
                        future.setException(IOException("Empty body for artwork: $url"))
                        return
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        DebugLog.d("BitmapLoader", "Loaded artwork ${bitmap.width}x${bitmap.height}")
                        future.set(bitmap)
                    } else {
                        future.setException(IOException("Failed to decode artwork"))
                    }
                }
            }
        })
        return future
    }
}
