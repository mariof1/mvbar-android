package com.mvbar.android.wear.net

import android.content.Context
import com.mvbar.android.wear.AuthTokenStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton HTTP entry-point for the watch.
 *
 * Reads token + base URL from AuthTokenStore on every interceptor invocation,
 * so credential rotation pushed from the phone is picked up immediately
 * without rebuilding Retrofit.
 */
object WearApiClient {

    private const val DEFAULT_BASE_URL = "http://localhost/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var lastBase: String = ""
    @Volatile private var apiRef: MvbarWearApi? = null

    fun api(context: Context): MvbarWearApi {
        val ctx = context.applicationContext
        val store = AuthTokenStore.get(ctx)
        val base = store.serverUrl?.takeIf { it.isNotBlank() }?.let {
            if (it.endsWith("/")) it else "$it/"
        } ?: DEFAULT_BASE_URL

        val cached = apiRef
        if (cached != null && base == lastBase) return cached

        val authInterceptor = Interceptor { chain ->
            val token = AuthTokenStore.get(ctx).token
            val req = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                    addHeader("Cookie", "mvbar_token=$token")
                }
            }.build()
            chain.proceed(req)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val r = Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        retrofit = r
        lastBase = base
        val a = r.create(MvbarWearApi::class.java)
        apiRef = a
        return a
    }

    /** True once the watch has both a server URL and a token. */
    fun isConfigured(context: Context): Boolean {
        val s = AuthTokenStore.get(context.applicationContext)
        return !s.token.isNullOrBlank() && !s.serverUrl.isNullOrBlank()
    }

    fun baseUrl(context: Context): String =
        AuthTokenStore.get(context.applicationContext).serverUrl ?: ""

    /**
     * Build an absolute artwork URL with the auth token baked into the path
     * for image loaders that don't reuse our OkHttp client.
     */
    fun artworkUrl(context: Context, artPath: String?): String? {
        if (artPath.isNullOrBlank()) return null
        val store = AuthTokenStore.get(context.applicationContext)
        val base = store.serverUrl ?: return null
        val token = store.token ?: return null
        val sep = if (artPath.contains("?")) "&" else "?"
        val p = if (artPath.startsWith("/")) artPath.drop(1) else artPath
        val rooted = if (base.endsWith("/")) "$base$p" else "$base/$p"
        return "$rooted${sep}token=$token"
    }
}
