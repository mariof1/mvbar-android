package com.mvbar.android.wear.ui

import android.content.Context
import com.mvbar.android.wear.net.Episode
import com.mvbar.android.wear.net.MvbarWearApi
import com.mvbar.android.wear.net.Playlist
import com.mvbar.android.wear.net.Podcast
import com.mvbar.android.wear.net.Track
import com.mvbar.android.wear.net.WearApiClient

/**
 * Tiny façade so screens don't pass Context everywhere — composables
 * call `Backend.get(context)` once and forward the wrapped api.
 */
class Backend private constructor(val context: Context, val api: MvbarWearApi) {

    suspend fun recentTracks(): List<Track> =
        runCatching { api.recentTracks(limit = 50).tracks }.getOrDefault(emptyList())

    suspend fun playlists(): List<Playlist> =
        runCatching { api.playlists().playlists }.getOrDefault(emptyList())

    suspend fun playlistTracks(id: Int): List<Track> =
        runCatching { api.playlistTracks(id).tracks }.getOrDefault(emptyList())

    suspend fun search(query: String): com.mvbar.android.wear.net.SearchResults =
        runCatching { api.search(query) }.getOrDefault(com.mvbar.android.wear.net.SearchResults())

    suspend fun favorites(): List<Track> =
        runCatching { api.favorites().tracks }.getOrDefault(emptyList())

    suspend fun podcasts(): List<Podcast> =
        runCatching { api.podcasts().podcasts }.getOrDefault(emptyList())

    suspend fun podcastEpisodes(id: Int): List<Episode> =
        runCatching { api.podcastDetail(id).episodes }.getOrDefault(emptyList())

    suspend fun newEpisodes(): List<Episode> =
        runCatching { api.newEpisodes().episodes }.getOrDefault(emptyList())

    suspend fun setEpisodeProgress(id: Int, positionMs: Long) {
        runCatching { api.setEpisodeProgress(id, positionMs) }
    }

    fun artworkUrl(artPath: String?): String? = WearApiClient.artworkUrl(context, artPath)

    companion object {
        fun get(context: Context): Backend =
            Backend(context.applicationContext, WearApiClient.api(context.applicationContext))
    }
}
