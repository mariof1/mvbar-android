package com.mvbar.android.wear

import android.content.Context

/**
 * Tiny wrapper around SharedPreferences for the auth token + server
 * URL replicated from the phone. Used by future standalone-playback
 * code; today nothing on the watch consumes the token, but we already
 * persist it so when we add direct network access the bootstrap is
 * trivial.
 */
object AuthTokenStore {
    private const val PREFS = "mvbar_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_SERVER = "server_url"

    fun save(context: Context, token: String, serverUrl: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (token.isNotEmpty()) putString(KEY_TOKEN, token) else remove(KEY_TOKEN)
            if (serverUrl.isNotEmpty()) putString(KEY_SERVER, serverUrl) else remove(KEY_SERVER)
            apply()
        }
    }

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun serverUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVER, null)
}
