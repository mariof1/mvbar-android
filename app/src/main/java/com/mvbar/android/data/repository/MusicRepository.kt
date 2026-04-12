package com.mvbar.android.data.repository

import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.*
import com.mvbar.android.data.model.*
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepository(private val db: MvbarDatabase? = null) {
    private val api get() = ApiClient.api

    // ── Cache-first reads (return from DB, fallback to API) ──

    fun tracksFlow(): Flow<List<Track>>? =
        db?.trackDao()?.allFlow()?.map { list -> list.map { it.toModel() } }

    fun trackCountFlow(): Flow<Int>? = db?.trackDao()?.countFlow()

    fun favoritesFlow(): Flow<List<Track>>? =
        db?.favoriteDao()?.favoritesFlow()?.map { list -> list.map { it.toModel() } }

    fun historyFlow(): Flow<List<Track>>? =
        db?.historyDao()?.historyFlow()?.map { list -> list.map { it.toModel() } }

    fun playlistsFlow(): Flow<List<Playlist>>? =
        db?.playlistDao()?.allFlow()?.map { list -> list.map { it.toModel() } }

    fun recommendationsFlow(): Flow<List<RecBucket>>? =
        db?.recommendationDao()?.allFlow()?.map { list -> list.map { it.toModel() } }

    fun artistsFlow(): Flow<List<Artist>>? =
        db?.browseDao()?.allArtistsFlow()?.map { list -> list.map { it.toModel() } }

    fun albumsFlow(): Flow<List<Album>>? =
        db?.browseDao()?.allAlbumsFlow()?.map { list -> list.map { it.toModel() } }

    fun genresFlow(): Flow<List<Genre>>? =
        db?.browseDao()?.allGenresFlow()?.map { list -> list.map { it.toModel() } }

    fun countriesFlow(): Flow<List<Country>>? =
        db?.browseDao()?.allCountriesFlow()?.map { list -> list.map { it.toModel() } }

    fun languagesFlow(): Flow<List<Language>>? =
        db?.browseDao()?.allLanguagesFlow()?.map { list -> list.map { it.toModel() } }

    fun podcastsFlow(): Flow<List<Podcast>>? =
        db?.podcastDao()?.allPodcastsFlow()?.map { list -> list.map { it.toModel() } }

    // ── Cached page reads ──

    suspend fun getCachedArtists(limit: Int, offset: Int): List<Artist>? =
        db?.browseDao()?.getArtists(limit, offset)?.map { it.toModel() }

    suspend fun getCachedAlbums(limit: Int, offset: Int): List<Album>? =
        db?.browseDao()?.getAlbums(limit, offset)?.map { it.toModel() }

    suspend fun getCachedGenres(limit: Int, offset: Int): List<Genre>? =
        db?.browseDao()?.getGenres(limit, offset)?.map { it.toModel() }

    suspend fun getCachedCountries(limit: Int, offset: Int): List<Country>? =
        db?.browseDao()?.getCountries(limit, offset)?.map { it.toModel() }

    suspend fun getCachedLanguages(limit: Int, offset: Int): List<Language>? =
        db?.browseDao()?.getLanguages(limit, offset)?.map { it.toModel() }

    suspend fun getCachedArtistCount(): Int = db?.browseDao()?.artistCount() ?: 0
    suspend fun getCachedAlbumCount(): Int = db?.browseDao()?.albumCount() ?: 0
    suspend fun getCachedGenreCount(): Int = db?.browseDao()?.genreCount() ?: 0
    suspend fun getCachedCountryCount(): Int = db?.browseDao()?.countryCount() ?: 0
    suspend fun getCachedLanguageCount(): Int = db?.browseDao()?.languageCount() ?: 0

    suspend fun getCachedTracksPage(limit: Int, offset: Int): List<Track>? =
        db?.trackDao()?.getPage(limit, offset)?.map { it.toModel() }

    suspend fun getCachedTrackCount(): Int = db?.trackDao()?.count() ?: 0

    suspend fun getTracksByIds(ids: List<Int>): List<Track>? =
        db?.trackDao()?.getByIds(ids)?.map { it.toModel() }

    suspend fun getCachedRecentlyAdded(limit: Int): List<Track>? =
        db?.trackDao()?.getRecentlyAdded(limit)?.map { it.toModel() }

    suspend fun getCachedFavorites(): List<Track>? =
        db?.favoriteDao()?.getFavorites()?.map { it.toModel() }

    suspend fun getCachedHistory(): List<Track>? =
        db?.historyDao()?.getHistory()?.map { it.toModel() }

    suspend fun getCachedRecommendations(): List<RecBucket>? =
        db?.recommendationDao()?.getAll()?.map { it.toModel() }

    suspend fun getCachedPlaylists(): List<Playlist>? =
        db?.playlistDao()?.getAll()?.map { it.toModel() }

    suspend fun getCachedPlaylistItems(playlistId: Int): List<PlaylistItem>? {
        val items = db?.playlistDao()?.getItems(playlistId) ?: return null
        return items.map { item ->
            val track = db.trackDao().getById(item.trackId)?.toModel()
            item.toModel(track)
        }
    }

    // Audiobooks
    suspend fun getCachedAudiobooks(): List<Audiobook>? =
        db?.audiobookDao()?.getAllAudiobooks()?.map { it.toModel() }

    suspend fun getCachedAudiobookChapters(audiobookId: Int): List<AudiobookChapter>? =
        db?.audiobookDao()?.getChapters(audiobookId)?.map { it.toModel() }

    // ── API calls (unchanged) ──

    suspend fun getTracks(limit: Int = 100, offset: Int = 0, sort: String? = null) = api.getTracks(limit, offset, sort)
    suspend fun getArtists(limit: Int = 50, offset: Int = 0) = api.getArtists(limit, offset)
    suspend fun getArtistDetail(id: Int) = api.getArtistDetail(id)
    suspend fun getArtistTracks(id: Int, limit: Int = 50, offset: Int = 0) = api.getArtistTracks(id, limit, offset)
    suspend fun getAlbums(limit: Int = 50, offset: Int = 0) = api.getAlbums(limit, offset)
    suspend fun getAlbumTracks(name: String) = api.getAlbumTracks(name)
    suspend fun getGenres(limit: Int = 50, offset: Int = 0) = api.getGenres(limit, offset)
    suspend fun getGenreTracks(name: String, limit: Int = 50, offset: Int = 0) = api.getGenreTracks(name, limit, offset)
    suspend fun getCountries(limit: Int = 50, offset: Int = 0) = api.getCountries(limit, offset)
    suspend fun getCountryTracks(name: String, limit: Int = 50, offset: Int = 0) = api.getCountryTracks(name, limit, offset)
    suspend fun getLanguages(limit: Int = 50, offset: Int = 0) = api.getLanguages(limit, offset)
    suspend fun getLanguageTracks(name: String, limit: Int = 50, offset: Int = 0) = api.getLanguageTracks(name, limit, offset)
    suspend fun getFavorites() = api.getFavorites()
    suspend fun addFavorite(trackId: Int) = api.addFavorite(trackId)
    suspend fun removeFavorite(trackId: Int) = api.removeFavorite(trackId)
    suspend fun getHistory(limit: Int = 50, offset: Int = 0) = api.getHistory(limit, offset)
    suspend fun recordPlay(trackId: Int) = api.recordPlay(trackId)
    suspend fun search(query: String, limit: Int = 50, offset: Int = 0) = api.search(query, limit, offset)
    suspend fun getRecommendations() = api.getRecommendations()
    suspend fun getSimilarTracks(trackId: Int, exclude: String? = null) = api.getSimilarTracks(trackId, exclude)
    suspend fun getRecentlyAdded(limit: Int = 50, offset: Int = 0) = api.getRecentlyAdded(limit = limit, offset = offset)

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
    suspend fun resolveArtistIds(ids: List<Int>) =
        api.suggestSmartPlaylist(kind = "artist", ids = ids.joinToString(","))

    companion object {
        @Volatile private var INSTANCE: MusicRepository? = null

        fun getInstance(db: MvbarDatabase?): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository(db).also { INSTANCE = it }
            }
        }

        fun getInstance(): MusicRepository =
            INSTANCE ?: MusicRepository(null)
    }
}
