package com.mvbar.android.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.GoogleTokenRequest
import com.mvbar.android.data.model.LoginRequest
import com.mvbar.android.data.model.User
import com.mvbar.android.data.sync.SyncManager
import com.mvbar.android.social.SocialRealtimeManager
import com.mvbar.android.wearbridge.WearStatePublisher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mvbar_prefs")

class AuthRepository(private val context: Context) {
    companion object {
        private val KEY_SERVER = stringPreferencesKey("server_url")
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_EMAIL = stringPreferencesKey("user_email")
        private val KEY_ROLE = stringPreferencesKey("user_role")
        private val KEY_AVATAR_PATH = stringPreferencesKey("user_avatar_path")
    }

    suspend fun getSavedServer(): String? =
        context.dataStore.data.map { it[KEY_SERVER] }.first()

    suspend fun getSavedToken(): String? =
        context.dataStore.data.map { it[KEY_TOKEN] }.first()

    suspend fun getSavedUser(): User? {
        val preferences = context.dataStore.data.first()
        val email = preferences[KEY_EMAIL] ?: return null
        return User(
            id = preferences[KEY_USER_ID].orEmpty(),
            email = email,
            role = preferences[KEY_ROLE] ?: "user",
            avatarPath = preferences[KEY_AVATAR_PATH]
        )
    }

    suspend fun refreshCurrentUser(): Result<User> {
        return try {
            val response = ApiClient.api.getCurrentUser()
            val user = response.body()?.user
            if (response.isSuccessful && user != null) {
                saveUserProfile(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Unable to load current user: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveUserProfile(user: User) {
        context.dataStore.edit { preferences ->
            if (user.id.isNotBlank()) preferences[KEY_USER_ID] = user.id else preferences.remove(KEY_USER_ID)
            preferences[KEY_EMAIL] = user.email
            preferences[KEY_ROLE] = user.role
            if (!user.avatarPath.isNullOrBlank()) {
                preferences[KEY_AVATAR_PATH] = user.avatarPath
            } else {
                preferences.remove(KEY_AVATAR_PATH)
            }
        }
    }

    suspend fun login(serverUrl: String, email: String, password: String): Result<User> {
        return try {
            ApiClient.configure(serverUrl)
            val response = ApiClient.api.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                val body = response.body()
                val token = body?.token
                    ?: response.headers()["set-cookie"]
                        ?.split(";")?.firstOrNull()
                        ?.substringAfter("=")
                    ?: ""

                if (token.isNotEmpty()) {
                    ApiClient.setToken(token)
                    val user = body?.user ?: User(email = email)
                    context.dataStore.edit { prefs ->
                        prefs[KEY_SERVER] = serverUrl
                        prefs[KEY_TOKEN] = token
                        if (user.id.isNotBlank()) prefs[KEY_USER_ID] = user.id
                        prefs[KEY_EMAIL] = user.email
                        prefs[KEY_ROLE] = user.role
                        if (!user.avatarPath.isNullOrBlank()) prefs[KEY_AVATAR_PATH] = user.avatarPath
                    }
                    WearStatePublisher.publishAuth(context)
                    SocialRealtimeManager.start(context)
                    SyncManager.checkSocialNotificationsNow(context)
                    Result.success(user)
                } else {
                    Result.failure(Exception("No token received"))
                }
            } else {
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun googleSignIn(serverUrl: String, idToken: String): Result<User> {
        return try {
            ApiClient.configure(serverUrl)
            val response = ApiClient.api.googleSignIn(GoogleTokenRequest(idToken))
            if (response.isSuccessful) {
                val body = response.body()
                val token = body?.token ?: ""

                if (token.isNotEmpty()) {
                    ApiClient.setToken(token)
                    val user = body?.user ?: User()
                    context.dataStore.edit { prefs ->
                        prefs[KEY_SERVER] = serverUrl
                        prefs[KEY_TOKEN] = token
                        if (user.id.isNotBlank()) prefs[KEY_USER_ID] = user.id
                        prefs[KEY_EMAIL] = user.email
                        prefs[KEY_ROLE] = user.role
                        if (!user.avatarPath.isNullOrBlank()) prefs[KEY_AVATAR_PATH] = user.avatarPath
                    }
                    WearStatePublisher.publishAuth(context)
                    SocialRealtimeManager.start(context)
                    SyncManager.checkSocialNotificationsNow(context)
                    Result.success(user)
                } else {
                    Result.failure(Exception("No token received"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                val msg = when {
                    errorBody.contains("pending") -> "Account pending admin approval"
                    errorBody.contains("rejected") -> "Account rejected"
                    errorBody.contains("not configured") -> "Google OAuth not configured on server"
                    else -> "Google sign-in failed: ${response.code()}"
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class GoogleAuthInfo(val enabled: Boolean, val clientId: String?)

    suspend fun checkGoogleAuth(serverUrl: String): GoogleAuthInfo {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("${url}api/auth/google/enabled")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val enabled = body.contains("\"enabled\":true")
                    val clientId = Regex("\"clientId\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    GoogleAuthInfo(enabled, clientId)
                } else GoogleAuthInfo(false, null)
            } catch (_: Exception) {
                GoogleAuthInfo(false, null)
            }
        }
    }

    suspend fun restoreSession(): Boolean {
        val server = getSavedServer() ?: return false
        val token = getSavedToken() ?: return false
        ApiClient.configure(server, token)
        SocialRealtimeManager.start(context)
        return true
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
        SocialRealtimeManager.stop()
        ApiClient.setToken(null)
    }
}
