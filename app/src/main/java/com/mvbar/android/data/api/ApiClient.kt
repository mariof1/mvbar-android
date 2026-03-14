package com.mvbar.android.data.api

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

    val api: MvbarApi
        get() {
            if (_api == null) {
                val authInterceptor = Interceptor { chain ->
                    val builder = chain.request().newBuilder()
                    authToken?.let { builder.addHeader("Authorization", "Bearer $it") }
                    chain.proceed(builder.build())
                }

                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

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

    fun artUrl(trackId: Int): String = "${baseUrl}api/art/$trackId"
    fun streamUrl(trackId: Int): String = "${baseUrl}api/library/tracks/$trackId/stream"
}
