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

    private fun splitMetadataValues(value: String?): List<String> =
        value
            ?.split(';', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinctBy { it.lowercase() }
            .orEmpty()

    private fun browseLetterMatches(name: String, letter: String?): Boolean {
        val trimmed = name.trim()
        if (letter == null) return true
        val first = trimmed.firstOrNull()?.uppercaseChar()
        return if (letter == "#") first == null || first !in 'A'..'Z' else first?.toString() == letter
    }

    private fun <T> page(items: List<T>, limit: Int, offset: Int): List<T> =
        items.drop(offset).take(limit)

    private suspend fun cachedTrackModels(): List<Track> =
        db?.trackDao()?.getAllForBrowse()?.map { it.toModel() }.orEmpty()

    private fun derivedArtistsFromTracks(tracks: List<Track>): List<Artist> =
        tracks
            .flatMap { track ->
                val names = listOf(track.displayArtistName, track.artist, track.albumArtist)
                    .flatMap(::splitMetadataValues)
                    .ifEmpty { listOf(track.displayArtist) }
                names.map { name -> name to track }
            }
            .groupBy({ it.first }, { it.second })
            .map { (name, artistTracks) ->
                Artist(
                    name = name,
                    trackCount = artistTracks.map { it.id }.distinct().size,
                    albumCount = artistTracks.mapNotNull { it.album?.trim()?.takeIf(String::isNotEmpty) }
                        .distinctBy { it.lowercase() }
                        .size,
                    artPath = artistTracks.firstNotNullOfOrNull { it.artPath }
                )
            }

    private fun mergeArtists(indexArtists: List<Artist>, tracks: List<Track>, letter: String?): List<Artist> {
        val merged = linkedMapOf<String, Artist>()
        (indexArtists + derivedArtistsFromTracks(tracks))
            .filter { it.name.isNotBlank() && browseLetterMatches(it.name, letter) }
            .forEach { artist ->
                val key = artist.name.trim().lowercase()
                val existing = merged[key]
                merged[key] = if (existing == null) {
                    artist
                } else {
                    existing.copy(
                        id = existing.id ?: artist.id,
                        trackCount = maxOf(existing.trackCount, artist.trackCount),
                        albumCount = maxOf(existing.albumCount, artist.albumCount),
                        artPath = existing.artPath ?: artist.artPath
                    )
                }
            }
        return merged.values.sortedBy { it.name.lowercase() }
    }

    private fun derivedAlbumsFromTracks(tracks: List<Track>): List<Album> =
        tracks
            .filter { !it.album.isNullOrBlank() }
            .groupBy { it.album!!.trim().lowercase() }
            .map { (_, albumTracks) ->
                val first = albumTracks.first()
                Album(
                    album = first.album?.trim(),
                    artist = first.artist,
                    displayArtist = first.displayArtistName ?: first.albumArtist ?: first.artist,
                    albumArtist = first.albumArtist,
                    trackCount = albumTracks.map { it.id }.distinct().size,
                    year = albumTracks.mapNotNull { it.year }.minOrNull(),
                    artPath = albumTracks.firstNotNullOfOrNull { it.artPath },
                    artHash = albumTracks.firstNotNullOfOrNull { it.artHash },
                    totalDiscs = albumTracks.mapNotNull { it.discNumber }.maxOrNull()
                )
            }

    private fun mergeAlbums(indexAlbums: List<Album>, tracks: List<Track>, letter: String?): List<Album> {
        val merged = linkedMapOf<String, Album>()
        (indexAlbums + derivedAlbumsFromTracks(tracks))
            .filter { it.displayName.isNotBlank() && browseLetterMatches(it.displayName, letter) }
            .forEach { album ->
                val key = album.displayName.trim().lowercase()
                val existing = merged[key]
                merged[key] = if (existing == null) {
                    album
                } else {
                    existing.copy(
                        trackCount = maxOf(existing.trackCount, album.trackCount),
                        year = existing.year ?: album.year,
                        artPath = existing.artPath ?: album.artPath,
                        artHash = existing.artHash ?: album.artHash,
                        totalDiscs = existing.totalDiscs ?: album.totalDiscs
                    )
                }
            }
        return merged.values.sortedBy { it.displayName.lowercase() }
    }

    private fun derivedTagCounts(tracks: List<Track>, selector: (Track) -> String?): Map<String, Int> =
        tracks
            .flatMap { track -> splitMetadataValues(selector(track)).map { tag -> tag to track.id } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ids) -> ids.distinct().size }

    private fun mergeGenres(indexGenres: List<Genre>, tracks: List<Track>): List<Genre> {
        val counts = derivedTagCounts(tracks) { it.genre }
        val merged = linkedMapOf<String, Genre>()
        indexGenres.forEach { genre ->
            if (genre.name.isNotBlank()) merged[genre.name.trim().lowercase()] = genre
        }
        counts.forEach { (name, count) ->
            val key = name.lowercase()
            val existing = merged[key]
            merged[key] = existing?.copy(trackCount = maxOf(existing.trackCount, count))
                ?: Genre(name = name, trackCount = count)
        }
        return merged.values.sortedBy { it.name.lowercase() }
    }

    private fun mergeCountries(indexCountries: List<Country>, tracks: List<Track>): List<Country> {
        val counts = derivedTagCounts(tracks) { it.country }
        val merged = linkedMapOf<String, Country>()
        indexCountries.forEach { country ->
            if (country.name.isNotBlank()) merged[country.name.trim().lowercase()] = country
        }
        counts.forEach { (name, count) ->
            val key = name.lowercase()
            val existing = merged[key]
            merged[key] = existing?.copy(trackCount = maxOf(existing.trackCount, count))
                ?: Country(name = name, trackCount = count)
        }
        return merged.values.sortedBy { it.name.lowercase() }
    }

    private fun mergeLanguages(indexLanguages: List<Language>, tracks: List<Track>): List<Language> {
        val counts = derivedTagCounts(tracks) { it.language }
        val merged = linkedMapOf<String, Language>()
        indexLanguages.forEach { language ->
            if (language.name.isNotBlank()) merged[language.name.trim().lowercase()] = language
        }
        counts.forEach { (name, count) ->
            val key = name.lowercase()
            val existing = merged[key]
            merged[key] = existing?.copy(trackCount = maxOf(existing.trackCount, count))
                ?: Language(name = name, trackCount = count)
        }
        return merged.values.sortedBy { it.name.lowercase() }
    }

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

    suspend fun getCachedArtists(limit: Int, offset: Int, letter: String? = null): List<Artist>? {
        val dao = db?.browseDao() ?: return null
        val indexArtists = if (letter == null) {
            dao.getArtists(Int.MAX_VALUE, 0)
        } else {
            dao.getArtistsByLetter(letter, Int.MAX_VALUE, 0)
        }.map { it.toModel() }
        return page(mergeArtists(indexArtists, cachedTrackModels(), letter), limit, offset)
    }

    suspend fun getCachedAlbums(limit: Int, offset: Int, letter: String? = null): List<Album>? {
        val dao = db?.browseDao() ?: return null
        val indexAlbums = if (letter == null) {
            dao.getAlbums(Int.MAX_VALUE, 0)
        } else {
            dao.getAlbumsByLetter(letter, Int.MAX_VALUE, 0)
        }.map { it.toModel() }
        return page(mergeAlbums(indexAlbums, cachedTrackModels(), letter), limit, offset)
    }

    suspend fun getCachedGenres(limit: Int, offset: Int): List<Genre>? =
        db?.browseDao()?.getGenres(Int.MAX_VALUE, 0)?.map { it.toModel() }
            ?.let { page(mergeGenres(it, cachedTrackModels()), limit, offset) }

    suspend fun getCachedCountries(limit: Int, offset: Int): List<Country>? =
        db?.browseDao()?.getCountries(Int.MAX_VALUE, 0)?.map { it.toModel() }
            ?.let { page(mergeCountries(it, cachedTrackModels()), limit, offset) }

    suspend fun getCachedLanguages(limit: Int, offset: Int): List<Language>? =
        db?.browseDao()?.getLanguages(Int.MAX_VALUE, 0)?.map { it.toModel() }
            ?.let { page(mergeLanguages(it, cachedTrackModels()), limit, offset) }

    suspend fun getCachedArtistCount(letter: String? = null): Int {
        val dao = db?.browseDao() ?: return 0
        val indexArtists = if (letter == null) {
            dao.getArtists(Int.MAX_VALUE, 0)
        } else {
            dao.getArtistsByLetter(letter, Int.MAX_VALUE, 0)
        }.map { it.toModel() }
        return mergeArtists(indexArtists, cachedTrackModels(), letter).size
    }
    suspend fun getCachedAlbumCount(letter: String? = null): Int {
        val dao = db?.browseDao() ?: return 0
        val indexAlbums = if (letter == null) {
            dao.getAlbums(Int.MAX_VALUE, 0)
        } else {
            dao.getAlbumsByLetter(letter, Int.MAX_VALUE, 0)
        }.map { it.toModel() }
        return mergeAlbums(indexAlbums, cachedTrackModels(), letter).size
    }
    suspend fun getCachedGenreCount(): Int {
        val dao = db?.browseDao() ?: return 0
        return mergeGenres(dao.getGenres(Int.MAX_VALUE, 0).map { it.toModel() }, cachedTrackModels()).size
    }
    suspend fun getCachedCountryCount(): Int {
        val dao = db?.browseDao() ?: return 0
        return mergeCountries(dao.getCountries(Int.MAX_VALUE, 0).map { it.toModel() }, cachedTrackModels()).size
    }
    suspend fun getCachedLanguageCount(): Int {
        val dao = db?.browseDao() ?: return 0
        return mergeLanguages(dao.getLanguages(Int.MAX_VALUE, 0).map { it.toModel() }, cachedTrackModels()).size
    }

    suspend fun getCachedTracksPage(limit: Int, offset: Int): List<Track>? =
        db?.trackDao()?.getPage(limit, offset)?.map { it.toModel() }

    suspend fun getCachedTrackCount(): Int = db?.trackDao()?.count() ?: 0

    suspend fun getCachedAlbumTracks(album: String): List<Track>? =
        db?.trackDao()?.getByAlbum(album)?.map { it.toModel() }

    suspend fun getCachedArtistTracks(artist: String): List<Track>? =
        db?.trackDao()?.getByArtist(artist)?.map { it.toModel() }

    suspend fun getCachedGenreTracks(genre: String, limit: Int, offset: Int): List<Track>? =
        db?.trackDao()?.getByGenre(genre, limit, offset)?.map { it.toModel() }

    suspend fun getCachedGenreTrackCount(genre: String): Int =
        db?.trackDao()?.countByGenre(genre) ?: 0

    suspend fun getCachedCountryTracks(country: String, limit: Int, offset: Int): List<Track>? =
        db?.trackDao()?.getByCountry(country, limit, offset)?.map { it.toModel() }

    suspend fun getCachedCountryTrackCount(country: String): Int =
        db?.trackDao()?.countByCountry(country) ?: 0

    suspend fun getCachedLanguageTracks(language: String, limit: Int, offset: Int): List<Track>? =
        db?.trackDao()?.getByLanguage(language, limit, offset)?.map { it.toModel() }

    suspend fun getCachedLanguageTrackCount(language: String): Int =
        db?.trackDao()?.countByLanguage(language) ?: 0

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

    suspend fun cacheRecommendations(buckets: List<RecBucket>) {
        db?.recommendationDao()?.replaceAll(buckets.map { it.toEntity() })
    }

    suspend fun getCachedPlaylists(): List<Playlist>? =
        db?.playlistDao()?.getAll()?.map { it.toModel() }

    suspend fun getCachedPlaylistItems(playlistId: Int): List<PlaylistItem>? {
        val items = db?.playlistDao()?.getItems(playlistId) ?: return null
        return items.map { item ->
            val track = db.trackDao().getById(item.trackId)?.toModel()
            item.toModel(track)
        }
    }

    suspend fun searchCached(query: String, limit: Int = 50, offset: Int = 0): SearchResults {
        val database = db ?: return SearchResults(ok = true)
        val tracks = database.trackDao().search(query, limit, offset).map { it.toModel() }
        if (offset > 0) return SearchResults(ok = true, hits = tracks)

        val artists = database.browseDao().searchArtists(query, 8).map { entity ->
            SearchArtist(
                id = entity.artistId ?: 0,
                name = entity.name,
                artPath = entity.artPath,
                trackCount = entity.trackCount,
                albumCount = entity.albumCount
            )
        }
        val albums = database.browseDao().searchAlbums(query, 8).map { entity ->
            SearchAlbum(
                album = entity.displayName,
                displayArtist = entity.displayArtist ?: entity.albumArtist ?: entity.artist,
                artPath = entity.artPath,
                artHash = entity.artHash,
                trackCount = entity.trackCount
            )
        }
        val playlists = database.playlistDao().search(query, 8).map { entity ->
            SearchPlaylist(id = entity.id, name = entity.name, kind = "playlist")
        }
        return SearchResults(
            ok = true,
            hits = tracks,
            artists = artists,
            albums = albums,
            playlists = playlists
        )
    }

    suspend fun createCachedPlaylist(name: String): Playlist? {
        val dao = db?.playlistDao() ?: return null
        val id = dao.nextLocalPlaylistId()
        val entity = PlaylistEntity(id = id, name = name, userId = 0, createdAt = null, itemCount = 0)
        dao.insertPlaylist(entity)
        return entity.toModel()
    }

    suspend fun renameCachedPlaylist(id: Int, name: String) {
        db?.playlistDao()?.renamePlaylist(id, name)
    }

    suspend fun deleteCachedPlaylist(id: Int) {
        db?.playlistDao()?.deletePlaylistWithItems(id)
    }

    suspend fun addCachedTrackToPlaylist(playlistId: Int, trackId: Int) {
        db?.playlistDao()?.addTrack(playlistId, trackId)
    }

    suspend fun removeCachedTrackFromPlaylist(playlistId: Int, trackId: Int) {
        db?.playlistDao()?.removeTrack(playlistId, trackId)
    }

    // Audiobooks
    suspend fun getCachedAudiobooks(): List<Audiobook>? =
        db?.audiobookDao()?.getAllAudiobooks()?.map { it.toModel() }

    suspend fun getCachedAudiobookChapters(audiobookId: Int): List<AudiobookChapter>? =
        db?.audiobookDao()?.getChapters(audiobookId)?.map { it.toModel() }

    // ── API calls (unchanged) ──

    suspend fun getTracks(limit: Int = 100, offset: Int = 0, sort: String? = null) = api.getTracks(limit, offset, sort)
    suspend fun getArtists(limit: Int = 50, offset: Int = 0, letter: String? = null) =
        api.getArtists(limit, offset, letter)
    suspend fun getArtistDetail(id: Int) = api.getArtistDetail(id)
    suspend fun getArtistTracks(id: Int, limit: Int = 50, offset: Int = 0) = api.getArtistTracks(id, limit, offset)
    suspend fun getAlbums(limit: Int = 50, offset: Int = 0, letter: String? = null) =
        api.getAlbums(limit, offset, letter)
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
    suspend fun getPreferences() = api.getPreferences()
    suspend fun getRecommendations() = api.getRecommendations()
    suspend fun getSimilarTracks(trackId: Int, exclude: String? = null) = api.getSimilarTracks(trackId, exclude)
    suspend fun getRecentlyAdded(limit: Int = 50, offset: Int = 0) = api.getRecentlyAdded(limit = limit, offset = offset)

    // Playlists
    suspend fun getPlaylists() = api.getPlaylists()
    suspend fun getPlaylistItems(id: Int) = api.getPlaylistItems(id)
    suspend fun createPlaylist(name: String) = api.createPlaylist(mapOf("name" to name))
    suspend fun renamePlaylist(id: Int, name: String) = api.renamePlaylist(id, mapOf("name" to name))
    suspend fun deletePlaylist(id: Int) = api.deletePlaylist(id)
    suspend fun addToPlaylist(playlistId: Int, trackId: Int) =
        api.addToPlaylist(playlistId, mapOf("trackId" to trackId))
    suspend fun addTracksToPlaylist(playlistId: Int, trackIds: List<Int>) {
        trackIds.forEach { api.addToPlaylist(playlistId, mapOf("trackId" to it)) }
    }
    suspend fun removeFromPlaylist(playlistId: Int, trackId: Int) =
        api.removeFromPlaylist(playlistId, trackId)

    // Lyrics
    suspend fun getLyrics(trackId: Int): LyricsResponse? {
        val response = api.getLyrics(trackId)
        return if (response.isSuccessful && response.code() != 204) {
            response.body()
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
    suspend fun convertSmartPlaylist(id: Int, deleteSmart: Boolean = false) =
        api.convertSmartPlaylist(id, mapOf("delete" to deleteSmart))
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
