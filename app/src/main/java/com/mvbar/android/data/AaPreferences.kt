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
    private val KEY_LAST_QUEUE = stringPreferencesKey("last_queue")
    private val KEY_LAST_INDEX = intPreferencesKey("last_index")
    private val KEY_LAST_POSITION = stringPreferencesKey("last_position_ms")

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

    // Last playback state persistence (for AA reconnect auto-resume)

    /**
     * Save the current queue (media IDs + metadata), index, and position so playback
     * can be restored after the service restarts or AA reconnects.
     * Format per entry: mediaId\ttitle\tartist\talbum\tartUri
     */
    suspend fun savePlaybackState(
        context: Context,
        items: List<QueueEntry>,
        index: Int,
        positionMs: Long
    ) {
        context.aaDataStore.edit { prefs ->
            prefs[KEY_LAST_QUEUE] = items.joinToString("\n") { entry ->
                "${entry.mediaId}\t${entry.title.orEmpty()}\t${entry.artist.orEmpty()}\t${entry.album.orEmpty()}\t${entry.artUri.orEmpty()}"
            }
            prefs[KEY_LAST_INDEX] = index
            prefs[KEY_LAST_POSITION] = positionMs.toString()
        }
    }

    data class QueueEntry(
        val mediaId: String,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val artUri: String? = null
    )

    data class SavedPlaybackState(
        val entries: List<QueueEntry>,
        val index: Int,
        val positionMs: Long
    )

    suspend fun getSavedPlaybackState(context: Context): SavedPlaybackState? {
        val prefs = context.aaDataStore.data.first()
        val queueStr = prefs[KEY_LAST_QUEUE] ?: return null
        if (queueStr.isBlank()) return null
        val entries = queueStr.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null
            QueueEntry(
                mediaId = parts[0],
                title = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                artist = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                album = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                artUri = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            )
        }
        if (entries.isEmpty()) return null
        val index = prefs[KEY_LAST_INDEX] ?: 0
        val posMs = prefs[KEY_LAST_POSITION]?.toLongOrNull() ?: 0L
        return SavedPlaybackState(entries, index, posMs)
    }

    suspend fun clearPlaybackState(context: Context) {
        context.aaDataStore.edit { prefs ->
            prefs.remove(KEY_LAST_QUEUE)
            prefs.remove(KEY_LAST_INDEX)
            prefs.remove(KEY_LAST_POSITION)
        }
    }
}
