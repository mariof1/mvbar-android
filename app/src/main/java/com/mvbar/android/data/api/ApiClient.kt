package com.mvbar.android.data.api

import com.mvbar.android.debug.DebugLog
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl: String = "http://localhost/"
    private var authToken: String? = null
    private var _api: MvbarApi? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun configure(serverUrl: String, token: String? = null) {
        baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        authToken = token
        _api = null
    }

    fun setToken(token: String?) {
        authToken = token
        _api = null
    }

    fun getBaseUrl(): String = baseUrl
    fun getToken(): String? = authToken

    /** Force rebuild of the HTTP client (call after toggling debug mode) */
    fun rebuild() { _api = null }

    val api: MvbarApi
        get() {
            if (_api == null) {
                val authInterceptor = Interceptor { chain ->
                    val builder = chain.request().newBuilder()
                    authToken?.let { builder.addHeader("Authorization", "Bearer $it") }
                    chain.proceed(builder.build())
                }

                val debugInterceptor = Interceptor { chain ->
                    val request = chain.request()
                    val method = request.method
                    val url = request.url.toString()
                    DebugLog.d("HTTP", "→ $method $url")

                    val startMs = System.currentTimeMillis()
                    try {
                        val response = chain.proceed(request)
                        val elapsed = System.currentTimeMillis() - startMs
                        val code = response.code
                        val size = response.body?.contentLength() ?: -1

                        // Peek at error response bodies
                        if (code >= 400) {
                            val body = response.peekBody(4096).string()
                            DebugLog.e("HTTP", "← $code $method $url (${elapsed}ms) body=$body")
                        } else {
                            DebugLog.d("HTTP", "← $code $method $url (${elapsed}ms, ${size}b)")
                        }
                        response
                    } catch (e: Exception) {
                        val elapsed = System.currentTimeMillis() - startMs
                        DebugLog.e("HTTP", "✗ $method $url (${elapsed}ms)", e)
                        throw e
                    }
                }

                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }

                val clientBuilder = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(debugInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)

                val client = clientBuilder.build()

                val contentType = "application/json".toMediaType()
                _api = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory(contentType))
                    .build()
                    .create(MvbarApi::class.java)
            }
            return _api!!
        }

    fun trackArtUrl(trackId: Int): String = "${baseUrl}api/library/tracks/$trackId/art"
    fun artistArtUrl(artistId: Int): String = "${baseUrl}api/artists/$artistId/art"
    fun artPathUrl(artPath: String): String = "${baseUrl}api/art/$artPath"
    fun streamUrl(trackId: Int): String = "${baseUrl}api/library/tracks/$trackId/stream"
}
