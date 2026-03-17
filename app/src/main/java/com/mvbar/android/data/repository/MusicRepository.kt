package com.mvbar.android.data.repository

import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.*

class MusicRepository {
    private val api get() = ApiClient.api

    suspend fun getTracks(limit: Int = 100, offset: Int = 0) = api.getTracks(limit, offset)
    suspend fun getArtists(limit: Int = 50, offset: Int = 0) = api.getArtists(limit, offset)
    suspend fun getArtistDetail(id: Int) = api.getArtistDetail(id)
    suspend fun getArtistTracks(id: Int) = api.getArtistTracks(id)
    suspend fun getAlbums(limit: Int = 50, offset: Int = 0) = api.getAlbums(limit, offset)
    suspend fun getAlbumTracks(name: String) = api.getAlbumTracks(name)
    suspend fun getGenres(limit: Int = 50, offset: Int = 0) = api.getGenres(limit, offset)
    suspend fun getGenreTracks(name: String) = api.getGenreTracks(name)
    suspend fun getFavorites() = api.getFavorites()
    suspend fun toggleFavorite(trackId: Int) = api.toggleFavorite(trackId)
    suspend fun getHistory(limit: Int = 50) = api.getHistory(limit)
    suspend fun recordPlay(trackId: Int) = api.recordPlay(trackId)
    suspend fun search(query: String) = api.search(query)
    suspend fun getRecommendations() = api.getRecommendations()
    suspend fun getRecentlyAdded(limit: Int = 50) = api.getRecentlyAdded(limit = limit)

    // Playlists
    suspend fun getPlaylists() = api.getPlaylists()
    suspend fun getPlaylistItems(id: Int) = api.getPlaylistItems(id)
    suspend fun createPlaylist(name: String) = api.createPlaylist(mapOf("name" to name))
    suspend fun addToPlaylist(playlistId: Int, trackId: Int) =
        api.addToPlaylist(playlistId, mapOf("trackId" to trackId))
    suspend fun removeFromPlaylist(playlistId: Int, trackId: Int) =
        api.removeFromPlaylist(playlistId, trackId)

    // Lyrics
    suspend fun getLyrics(trackId: Int): String? {
        val response = api.getLyrics(trackId)
        return if (response.isSuccessful && response.code() != 204) {
            response.body()?.string()
        } else null
    }
    suspend fun prefetchLyrics(trackId: Int) {
        try { api.prefetchLyrics(trackId) } catch (_: Exception) {}
    }

    // Smart Playlists
    suspend fun getSmartPlaylists() = api.getSmartPlaylists()
    suspend fun createSmartPlaylist(name: String, sort: String, filters: SmartPlaylistFilters) =
        api.createSmartPlaylist(SmartPlaylistCreateRequest(name, sort, filters))
    suspend fun getSmartPlaylist(id: Int) = api.getSmartPlaylist(id)
    suspend fun updateSmartPlaylist(id: Int, name: String, sort: String, filters: SmartPlaylistFilters) =
        api.updateSmartPlaylist(id, SmartPlaylistCreateRequest(name, sort, filters))
    suspend fun deleteSmartPlaylist(id: Int) = api.deleteSmartPlaylist(id)
    suspend fun suggestSmartPlaylist(kind: String, query: String) = api.suggestSmartPlaylist(kind, query)
}
