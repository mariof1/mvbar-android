package com.mvbar.android.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.debug.DebugLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * ContentProvider that proxies artwork requests with authentication.
 * Android Auto's gearhead can load content:// URIs but cannot add auth headers,
 * so this provider fetches the image with the Bearer token and pipes it back.
 *
 * URI format: content://com.mvbar.android.artwork/<url-encoded-path>
 * e.g. content://com.mvbar.android.artwork/api%2Fart%2Fsome-hash
 */
class ArtworkProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.mvbar.android.artwork"

        /** Build a content:// URI for a given artwork URL */
        fun buildUri(artUrl: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendQueryParameter("url", artUrl)
                .build()
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                ApiClient.getToken()?.let { token ->
                    builder.addHeader("Authorization", "Bearer $token")
                    builder.addHeader("Cookie", "mvbar_token=$token")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    private val cacheDir by lazy {
        File(context!!.cacheDir, "artwork").also { it.mkdirs() }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val artUrl = uri.getQueryParameter("url") ?: return null

        // Use a hash-based filename for caching
        val cacheKey = md5(artUrl)
        val cacheFile = File(cacheDir, "$cacheKey.img")

        // Serve from disk cache if available
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // Fetch with auth
        try {
            val request = Request.Builder().url(artUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                DebugLog.e("ArtworkProvider", "HTTP ${response.code} for $artUrl")
                response.close()
                return null
            }
            val bytes = response.body?.bytes()
            response.close()
            if (bytes == null || bytes.isEmpty()) return null

            FileOutputStream(cacheFile).use { it.write(bytes) }
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            DebugLog.e("ArtworkProvider", "Failed to fetch artwork: $artUrl", e)
            return null
        }
    }

    override fun getType(uri: Uri): String = "image/*"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<out String>?): Int = 0

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
