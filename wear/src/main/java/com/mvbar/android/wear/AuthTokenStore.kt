package com.mvbar.android.wear

import android.content.Context

/**
 * Tiny wrapper around SharedPreferences for the auth token + server
 * URL replicated from the phone.
 */
object AuthTokenStore {
    private const val PREFS = "mvbar_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_SERVER = "server_url"

    data class Snapshot(val token: String?, val serverUrl: String?)

    fun save(context: Context, token: String, serverUrl: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (token.isNotEmpty()) putString(KEY_TOKEN, token) else remove(KEY_TOKEN)
            if (serverUrl.isNotEmpty()) putString(KEY_SERVER, serverUrl) else remove(KEY_SERVER)
            apply()
        }
    }

    fun get(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            token = prefs.getString(KEY_TOKEN, null),
            serverUrl = prefs.getString(KEY_SERVER, null)
        )
    }

    fun token(context: Context): String? = get(context).token
    fun serverUrl(context: Context): String? = get(context).serverUrl
}

