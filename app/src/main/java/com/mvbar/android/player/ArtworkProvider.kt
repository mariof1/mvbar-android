package com.mvbar.android.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
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
        /** URLs that returned 404 — skip retrying within this session */
        private val notFoundCache = java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        )

        /**
         * Build a content:// URI for a given artwork URL.
         *
         * Keep both the path-encoded and query-encoded forms for compatibility:
         * Android Auto is more reliable with a concrete path segment, while older
         * saved state may still contain the legacy ?url= form.
         */
        fun buildUri(artUrl: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .encodedPath("/${Uri.encode(artUrl)}")
                .appendQueryParameter("url", artUrl)
                .build()
        }

        /**
         * Build a content:// URI that composites multiple artwork images
         * into a 2×2 grid (like the app's ArtGrid). Used for "For You" buckets.
         */
        fun buildGridUri(artUrls: List<String>): Uri {
            val builder = Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("grid")
            artUrls.forEachIndexed { i, url ->
                builder.appendQueryParameter("art$i", url)
            }
            return builder.build()
        }

        /** Accept both legacy query-based and newer path-based artwork URIs. */
        fun extractArtUrl(uri: Uri): String? {
            val queryUrl = uri.getQueryParameter("url")
            if (!queryUrl.isNullOrBlank()) return queryUrl

            val encodedPath = uri.encodedPath?.removePrefix("/")
            return encodedPath
                ?.takeIf { it.isNotBlank() }
                ?.let(Uri::decode)
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
        File(context!!.filesDir, "artwork").also { it.mkdirs() }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // Grid artwork: composite multiple images into a 2×2 grid
        if (uri.pathSegments.firstOrNull() == "grid") {
            return openGridFile(uri)
        }

        val artUrl = extractArtUrl(uri) ?: return null

        // Use a hash-based filename for caching
        val cacheKey = md5(artUrl)
        val cacheFile = File(cacheDir, "$cacheKey.img")

        // Serve from disk cache if available
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // Skip URLs known to be missing
        if (artUrl in notFoundCache) return null

        // Fetch with auth
        try {
            val request = Request.Builder().url(artUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    notFoundCache.add(artUrl)
                    DebugLog.d("ArtworkProvider", "Art not found (cached 404): $artUrl")
                } else {
                    DebugLog.e("ArtworkProvider", "HTTP ${response.code} for $artUrl")
                }
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

    /** Composite up to 4 images into a 2×2 grid bitmap */
    private fun openGridFile(uri: Uri): ParcelFileDescriptor? {
        val artUrls = (0..3).mapNotNull { uri.getQueryParameter("art$it") }
        if (artUrls.isEmpty()) return null

        val cacheKey = md5("grid:" + artUrls.joinToString("|"))
        val cacheFile = File(cacheDir, "$cacheKey.png")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        val bitmaps = artUrls.mapNotNull { url -> fetchBitmap(url) }
        if (bitmaps.isEmpty()) return null

        val gridSize = 400
        val output = Bitmap.createBitmap(gridSize, gridSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val half = gridSize / 2

        when (bitmaps.size) {
            1 -> drawScaled(canvas, bitmaps[0], Rect(0, 0, gridSize, gridSize))
            2 -> {
                drawScaled(canvas, bitmaps[0], Rect(0, 0, half, gridSize))
                drawScaled(canvas, bitmaps[1], Rect(half, 0, gridSize, gridSize))
            }
            3 -> {
                drawScaled(canvas, bitmaps[0], Rect(0, 0, half, gridSize))
                drawScaled(canvas, bitmaps[1], Rect(half, 0, gridSize, half))
                drawScaled(canvas, bitmaps[2], Rect(half, half, gridSize, gridSize))
            }
            else -> {
                drawScaled(canvas, bitmaps[0], Rect(0, 0, half, half))
                drawScaled(canvas, bitmaps[1], Rect(half, 0, gridSize, half))
                drawScaled(canvas, bitmaps[2], Rect(0, half, half, gridSize))
                drawScaled(canvas, bitmaps[3], Rect(half, half, gridSize, gridSize))
            }
        }
        bitmaps.forEach { it.recycle() }

        FileOutputStream(cacheFile).use { output.compress(Bitmap.CompressFormat.PNG, 90, it) }
        output.recycle()
        return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun drawScaled(canvas: Canvas, src: Bitmap, dst: Rect) {
        // Center-crop into destination rect
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = dst.width().toFloat() / dst.height()
        val srcRect = if (srcRatio > dstRatio) {
            val w = (src.height * dstRatio).toInt()
            val offset = (src.width - w) / 2
            Rect(offset, 0, offset + w, src.height)
        } else {
            val h = (src.width / dstRatio).toInt()
            val offset = (src.height - h) / 2
            Rect(0, offset, src.width, offset + h)
        }
        canvas.drawBitmap(src, srcRect, dst, null)
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val cacheKey = md5(url)
        val cacheFile = File(cacheDir, "$cacheKey.img")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return BitmapFactory.decodeFile(cacheFile.absolutePath)
        }
        if (url in notFoundCache) return null
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) notFoundCache.add(url)
                response.close()
                return null
            }
            val bytes = response.body?.bytes()
            response.close()
            if (bytes == null || bytes.isEmpty()) return null
            FileOutputStream(cacheFile).use { it.write(bytes) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            DebugLog.e("ArtworkProvider", "Grid: failed to fetch $url", e)
            null
        }
    }
}
