package com.mvbar.android.data.repository

import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.*

class MusicRepository {
    private val api get() = ApiClient.api

    suspend fun getTracks(limit: Int = 100, offset: Int = 0) = api.getTracks(limit, offset)
    suspend fun getArtists() = api.getArtists()
    suspend fun getArtist(id: Int) = api.getArtist(id)
    suspend fun getArtistTracks(id: Int) = api.getArtistTracks(id)
    suspend fun getAlbums() = api.getAlbums()
    suspend fun getAlbumTracks(name: String) = api.getAlbumTracks(name)
    suspend fun getGenres() = api.getGenres()
    suspend fun getGenreTracks(name: String) = api.getGenreTracks(name)
    suspend fun getFavorites() = api.getFavorites()
    suspend fun toggleFavorite(trackId: Int) = api.toggleFavorite(trackId)
    suspend fun getHistory(limit: Int = 50) = api.getHistory(limit)
    suspend fun recordPlay(trackId: Int) = api.recordPlay(mapOf("track_id" to trackId))
    suspend fun getPlaylists() = api.getPlaylists()
    suspend fun getPlaylist(id: Int) = api.getPlaylist(id)
    suspend fun createPlaylist(name: String) = api.createPlaylist(mapOf("name" to name))
    suspend fun addToPlaylist(playlistId: Int, trackId: Int) =
        api.addToPlaylist(playlistId, mapOf("track_id" to trackId))
    suspend fun search(query: String) = api.search(query)
    suspend fun getRecommendations() = api.getRecommendations()
    suspend fun getRecentlyAdded(limit: Int = 50) = api.getRecentlyAdded(limit = limit)
}
