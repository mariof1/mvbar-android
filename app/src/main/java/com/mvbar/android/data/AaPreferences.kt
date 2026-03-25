package com.mvbar.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aaDataStore by preferencesDataStore(name = "aa_prefs")

object AaPreferences {
    private val KEY_CATEGORY_ORDER = stringPreferencesKey("category_order")
    private val KEY_SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
    private val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")

    val ALL_CATEGORIES = listOf(
        "foryou" to "For You",
        "recent" to "Recently Added",
        "favorites" to "Favorites",
        "albums" to "Albums",
        "artists" to "Artists",
        "playlists" to "Playlists",
        "genres" to "Genres",
        "languages" to "Languages",
        "podcasts" to "Podcasts",
        "audiobooks" to "Audiobooks",
        "countries" to "Countries",
    )

    private val DEFAULT_ORDER = ALL_CATEGORIES.map { it.first }

    fun categoryOrderFlow(context: Context): Flow<List<String>> {
        return context.aaDataStore.data.map { prefs ->
            val stored = prefs[KEY_CATEGORY_ORDER]
            if (stored.isNullOrBlank()) {
                DEFAULT_ORDER
            } else {
                val order = stored.split(",")
                // Include any new categories not yet in saved order
                val missing = DEFAULT_ORDER.filter { it !in order }
                order + missing
            }
        }
    }

    suspend fun getCategoryOrder(context: Context): List<String> {
        return categoryOrderFlow(context).first()
    }

    suspend fun saveCategoryOrder(context: Context, order: List<String>) {
        context.aaDataStore.edit { prefs ->
            prefs[KEY_CATEGORY_ORDER] = order.joinToString(",")
        }
    }

    fun displayName(id: String): String {
        return ALL_CATEGORIES.firstOrNull { it.first == id }?.second ?: id
    }

    // Shuffle/Repeat persistence

    suspend fun getShuffleEnabled(context: Context): Boolean {
        return context.aaDataStore.data.first()[KEY_SHUFFLE_ENABLED] ?: false
    }

    suspend fun saveShuffleEnabled(context: Context, enabled: Boolean) {
        context.aaDataStore.edit { it[KEY_SHUFFLE_ENABLED] = enabled }
    }

    suspend fun getRepeatMode(context: Context): Int {
        return context.aaDataStore.data.first()[KEY_REPEAT_MODE] ?: 0
    }

    suspend fun saveRepeatMode(context: Context, mode: Int) {
        context.aaDataStore.edit { it[KEY_REPEAT_MODE] = mode }
    }
}
