package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.AudioCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class BrowseState(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val countries: List<Country> = emptyList(),
    val languages: List<Language> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0,
    val hasMoreArtists: Boolean = true,
    val hasMoreAlbums: Boolean = true,
    val hasMoreGenres: Boolean = true,
    val hasMoreCountries: Boolean = true,
    val hasMoreLanguages: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val artistLetter: String? = null,
    val albumLetter: String? = null,
    val offlinePlayableArtists: Set<String> = emptySet(),
    val offlinePlayableAlbums: Set<String> = emptySet(),
    val offlinePlayableGenres: Set<String> = emptySet(),
    val offlinePlayableCountries: Set<String> = emptySet(),
    val offlinePlayableLanguages: Set<String> = emptySet()
)

class BrowseViewModel(app: Application) : AndroidViewModel(app) {
    private val db = MvbarDatabase.getInstance(app)
    private val repo = MusicRepository.getInstance(db)
    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private val _artistTracks = MutableStateFlow<List<Track>>(emptyList())
    val artistTracks: StateFlow<List<Track>> = _artistTracks.asStateFlow()

    private val _albumTracks = MutableStateFlow<List<Track>>(emptyList())
    val albumTracks: StateFlow<List<Track>> = _albumTracks.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    val selectedArtist: StateFlow<Artist?> = _selectedArtist.asStateFlow()

    private val _genreTracks = MutableStateFlow<List<Track>>(emptyList())
    val genreTracks: StateFlow<List<Track>> = _genreTracks.asStateFlow()

    private val _genreLoading = MutableStateFlow(false)
    val genreLoading: StateFlow<Boolean> = _genreLoading.asStateFlow()

    private val _hasMoreGenreTracks = MutableStateFlow(true)
    val hasMoreGenreTracks: StateFlow<Boolean> = _hasMoreGenreTracks.asStateFlow()

    private val _isLoadingMoreGenreTracks = MutableStateFlow(false)
    val isLoadingMoreGenreTracks: StateFlow<Boolean> = _isLoadingMoreGenreTracks.asStateFlow()

    private var currentGenreName: String = ""

    private val _countryTracks = MutableStateFlow<List<Track>>(emptyList())
    val countryTracks: StateFlow<List<Track>> = _countryTracks.asStateFlow()

    private val _countryLoading = MutableStateFlow(false)
    val countryLoading: StateFlow<Boolean> = _countryLoading.asStateFlow()

    private val _hasMoreCountryTracks = MutableStateFlow(true)
    val hasMoreCountryTracks: StateFlow<Boolean> = _hasMoreCountryTracks.asStateFlow()

    private val _isLoadingMoreCountryTracks = MutableStateFlow(false)
    val isLoadingMoreCountryTracks: StateFlow<Boolean> = _isLoadingMoreCountryTracks.asStateFlow()

    private var currentCountryName: String = ""
    private var artistLetterJob: Job? = null
    private var albumLetterJob: Job? = null

    private val _languageTracks = MutableStateFlow<List<Track>>(emptyList())
    val languageTracks: StateFlow<List<Track>> = _languageTracks.asStateFlow()

    private val _languageLoading = MutableStateFlow(false)
    val languageLoading: StateFlow<Boolean> = _languageLoading.asStateFlow()

    private val _hasMoreLanguageTracks = MutableStateFlow(true)
    val hasMoreLanguageTracks: StateFlow<Boolean> = _hasMoreLanguageTracks.asStateFlow()

    private val _isLoadingMoreLanguageTracks = MutableStateFlow(false)
    val isLoadingMoreLanguageTracks: StateFlow<Boolean> = _isLoadingMoreLanguageTracks.asStateFlow()

    private var currentLanguageName: String = ""

    // Artist albums from detail endpoint
    private val _artistAlbums = MutableStateFlow<List<Album>>(emptyList())
    val artistAlbums: StateFlow<List<Album>> = _artistAlbums.asStateFlow()

    private val _artistAppearsOn = MutableStateFlow<List<Album>>(emptyList())
    val artistAppearsOn: StateFlow<List<Album>> = _artistAppearsOn.asStateFlow()

    private val _hasMoreArtistTracks = MutableStateFlow(true)
    val hasMoreArtistTracks: StateFlow<Boolean> = _hasMoreArtistTracks.asStateFlow()

    private val _isLoadingMoreArtistTracks = MutableStateFlow(false)
    val isLoadingMoreArtistTracks: StateFlow<Boolean> = _isLoadingMoreArtistTracks.asStateFlow()

    private var currentArtistId: Int? = null
    private var currentArtistName: String = ""

    private companion object {
        const val PAGE_SIZE = 50
    }

    private data class PlayableCollections(
        val artists: Set<String> = emptySet(),
        val albums: Set<String> = emptySet(),
        val genres: Set<String> = emptySet(),
        val countries: Set<String> = emptySet(),
        val languages: Set<String> = emptySet()
    )

    private data class BrowsePage<T>(val items: List<T>, val total: Int)

    private fun hasMore(offset: Int, fetched: Int, total: Int): Boolean =
        if (total > 0) offset + fetched < total else fetched >= PAGE_SIZE

    private fun availabilityKey(value: String?): String =
        value?.trim()?.lowercase().orEmpty()

    private fun splitMetadataValues(value: String?): List<String> =
        value
            ?.split(';', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private suspend fun cachedPlayableCollections(): PlayableCollections {
        val ids = AudioCacheManager.getCachedTrackIds()
        if (ids.isEmpty()) return PlayableCollections()
        val tracks = repo.getTracksByIds(ids).orEmpty()
        val artists = tracks.flatMap { track ->
            listOf(track.artist, track.displayArtistName, track.albumArtist)
                .flatMap(::splitMetadataValues)
        }.map(::availabilityKey).filter { it.isNotEmpty() }.toSet()
        val albums = tracks.map { availabilityKey(it.album) }.filter { it.isNotEmpty() }.toSet()
        val genres = tracks.flatMap { splitMetadataValues(it.genre) }
            .map(::availabilityKey)
            .filter { it.isNotEmpty() }
            .toSet()
        val countries = tracks.flatMap { splitMetadataValues(it.country) }
            .map(::availabilityKey)
            .filter { it.isNotEmpty() }
            .toSet()
        val languages = tracks.flatMap { splitMetadataValues(it.language) }
            .map(::availabilityKey)
            .filter { it.isNotEmpty() }
            .toSet()
        return PlayableCollections(artists, albums, genres, countries, languages)
    }

    private suspend fun cachedArtistsPage(letter: String?, limit: Int, offset: Int): BrowsePage<Artist> =
        BrowsePage(
            repo.getCachedArtists(limit, offset, letter).orEmpty(),
            repo.getCachedArtistCount(letter)
        )

    private suspend fun artistsPage(letter: String?, limit: Int, offset: Int): BrowsePage<Artist> {
        if (NetworkMonitor.isOnline.value) {
            try {
                val response = repo.getArtists(limit, offset, letter)
                return BrowsePage(response.artists, response.total)
            } catch (e: Exception) {
                DebugLog.e("Browse", "Artists API page failed, falling back to cache", e)
            }
        }
        return cachedArtistsPage(letter, limit, offset)
    }

    private suspend fun cachedAlbumsPage(letter: String?, limit: Int, offset: Int): BrowsePage<Album> =
        BrowsePage(
            repo.getCachedAlbums(limit, offset, letter).orEmpty(),
            repo.getCachedAlbumCount(letter)
        )

    private suspend fun albumsPage(letter: String?, limit: Int, offset: Int): BrowsePage<Album> {
        if (NetworkMonitor.isOnline.value) {
            try {
                val response = repo.getAlbums(limit, offset, letter)
                return BrowsePage(response.albums, response.total)
            } catch (e: Exception) {
                DebugLog.e("Browse", "Albums API page failed, falling back to cache", e)
            }
        }
        return cachedAlbumsPage(letter, limit, offset)
    }

    private suspend fun cachedGenresPage(limit: Int, offset: Int): BrowsePage<Genre> =
        BrowsePage(repo.getCachedGenres(limit, offset).orEmpty(), repo.getCachedGenreCount())

    private suspend fun genresPage(limit: Int, offset: Int): BrowsePage<Genre> {
        if (NetworkMonitor.isOnline.value) {
            try {
                val response = repo.getGenres(limit, offset)
                return BrowsePage(response.genres, response.total)
            } catch (e: Exception) {
                DebugLog.e("Browse", "Genres API page failed, falling back to cache", e)
            }
        }
        return cachedGenresPage(limit, offset)
    }

    private suspend fun cachedCountriesPage(limit: Int, offset: Int): BrowsePage<Country> =
        BrowsePage(repo.getCachedCountries(limit, offset).orEmpty(), repo.getCachedCountryCount())

    private suspend fun countriesPage(limit: Int, offset: Int): BrowsePage<Country> {
        if (NetworkMonitor.isOnline.value) {
            try {
                val response = repo.getCountries(limit, offset)
                return BrowsePage(response.countries, response.total)
            } catch (e: Exception) {
                DebugLog.e("Browse", "Countries API page failed, falling back to cache", e)
            }
        }
        return cachedCountriesPage(limit, offset)
    }

    private suspend fun cachedLanguagesPage(limit: Int, offset: Int): BrowsePage<Language> =
        BrowsePage(repo.getCachedLanguages(limit, offset).orEmpty(), repo.getCachedLanguageCount())

    private suspend fun languagesPage(limit: Int, offset: Int): BrowsePage<Language> {
        if (NetworkMonitor.isOnline.value) {
            try {
                val response = repo.getLanguages(limit, offset)
                return BrowsePage(response.languages, response.total)
            } catch (e: Exception) {
                DebugLog.e("Browse", "Languages API page failed, falling back to cache", e)
            }
        }
        return cachedLanguagesPage(limit, offset)
    }

    fun loadAll(isRefresh: Boolean = false) {
        artistLetterJob?.cancel()
        albumLetterJob?.cancel()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                error = null,
                artistLetter = null,
                albumLetter = null
            )
            try {
                val offline = !NetworkMonitor.isOnline.value
                if (!isRefresh || offline) {
                    val playable = cachedPlayableCollections()
                    val cachedArtists = cachedArtistsPage(null, PAGE_SIZE, 0)
                    val cachedAlbums = cachedAlbumsPage(null, PAGE_SIZE, 0)
                    val cachedGenres = cachedGenresPage(PAGE_SIZE, 0)
                    val cachedCountries = cachedCountriesPage(PAGE_SIZE, 0)
                    val cachedLanguages = cachedLanguagesPage(PAGE_SIZE, 0)
                    val hasCachedBrowse = cachedArtists.items.isNotEmpty() ||
                        cachedAlbums.items.isNotEmpty() ||
                        cachedGenres.items.isNotEmpty() ||
                        cachedCountries.items.isNotEmpty() ||
                        cachedLanguages.items.isNotEmpty()
                    if (hasCachedBrowse || offline) {
                        DebugLog.i("Browse", "Loaded cached browse root (offline=$offline)")
                        _state.value = _state.value.copy(
                            artists = cachedArtists.items,
                            albums = cachedAlbums.items,
                            genres = cachedGenres.items,
                            countries = cachedCountries.items,
                            languages = cachedLanguages.items,
                            hasMoreArtists = hasMore(0, cachedArtists.items.size, cachedArtists.total),
                            hasMoreAlbums = hasMore(0, cachedAlbums.items.size, cachedAlbums.total),
                            hasMoreGenres = hasMore(0, cachedGenres.items.size, cachedGenres.total),
                            hasMoreCountries = hasMore(0, cachedCountries.items.size, cachedCountries.total),
                            hasMoreLanguages = hasMore(0, cachedLanguages.items.size, cachedLanguages.total),
                            offlinePlayableArtists = playable.artists,
                            offlinePlayableAlbums = playable.albums,
                            offlinePlayableGenres = playable.genres,
                            offlinePlayableCountries = playable.countries,
                            offlinePlayableLanguages = playable.languages,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                    if (offline) return@launch
                }

                DebugLog.i("Browse", "Loading artists, albums, genres...")
                val playable = cachedPlayableCollections()
                val artists = artistsPage(null, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${artists.items.size} artists (total: ${artists.total})")
                val albums = albumsPage(null, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${albums.items.size} albums (total: ${albums.total})")
                val genres = genresPage(PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${genres.items.size} genres (total: ${genres.total})")
                val countries = countriesPage(PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${countries.items.size} countries (total: ${countries.total})")
                val languages = languagesPage(PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${languages.items.size} languages (total: ${languages.total})")

                _state.value = _state.value.copy(
                    artists = artists.items,
                    albums = albums.items,
                    genres = genres.items,
                    countries = countries.items,
                    languages = languages.items,
                    hasMoreArtists = hasMore(0, artists.items.size, artists.total),
                    hasMoreAlbums = hasMore(0, albums.items.size, albums.total),
                    hasMoreGenres = hasMore(0, genres.items.size, genres.total),
                    hasMoreCountries = hasMore(0, countries.items.size, countries.total),
                    hasMoreLanguages = hasMore(0, languages.items.size, languages.total),
                    offlinePlayableArtists = playable.artists,
                    offlinePlayableAlbums = playable.albums,
                    offlinePlayableGenres = playable.genres,
                    offlinePlayableCountries = playable.countries,
                    offlinePlayableLanguages = playable.languages,
                    isLoading = false,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "loadAll failed", e)
                _state.value = _state.value.copy(
                    isLoading = false, isRefreshing = false,
                    error = "Failed to load: ${e.message}"
                )
            }
        }
    }

    fun loadMoreArtists() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreArtists) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val offset = s.artists.size
                val page = artistsPage(s.artistLetter, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${page.items.size} more artists (letter=${s.artistLetter ?: "All"}, offset=$offset, total=${page.total})")
                _state.value = _state.value.copy(
                    artists = s.artists + page.items,
                    hasMoreArtists = hasMore(offset, page.items.size, page.total),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more artists failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun loadMoreAlbums() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreAlbums) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val offset = s.albums.size
                val page = albumsPage(s.albumLetter, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${page.items.size} more albums (letter=${s.albumLetter ?: "All"}, offset=$offset, total=${page.total})")
                _state.value = _state.value.copy(
                    albums = s.albums + page.items,
                    hasMoreAlbums = hasMore(offset, page.items.size, page.total),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more albums failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun loadMoreGenres() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreGenres) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val offset = s.genres.size
                val page = genresPage(PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${page.items.size} more genres (offset $offset, total=${page.total})")
                _state.value = _state.value.copy(
                    genres = s.genres + page.items,
                    hasMoreGenres = hasMore(offset, page.items.size, page.total),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more genres failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun loadMoreCountries() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreCountries) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val offset = s.countries.size
                val page = countriesPage(PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${page.items.size} more countries (offset $offset, total=${page.total})")
                _state.value = _state.value.copy(
                    countries = s.countries + page.items,
                    hasMoreCountries = hasMore(offset, page.items.size, page.total),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more countries failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun loadMoreLanguages() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMoreLanguages) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val offset = s.languages.size
                val page = languagesPage(PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${page.items.size} more languages (offset $offset, total=${page.total})")
                _state.value = _state.value.copy(
                    languages = s.languages + page.items,
                    hasMoreLanguages = hasMore(offset, page.items.size, page.total),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more languages failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun setTab(tab: Int) { _state.value = _state.value.copy(selectedTab = tab) }

    fun selectArtistLetter(letter: String?) {
        val normalized = normalizeBrowseLetter(letter)
        val s = _state.value
        if (s.artistLetter == normalized && s.artists.isNotEmpty()) return
        artistLetterJob?.cancel()
        artistLetterJob = viewModelScope.launch {
            _state.value = s.copy(
                artistLetter = normalized,
                artists = emptyList(),
                hasMoreArtists = true,
                isLoadingMore = true,
                error = null
            )
            try {
                val page = artistsPage(normalized, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Selected artist letter ${normalized ?: "All"}: ${page.items.size}/${page.total}")
                _state.value = _state.value.copy(
                    artists = page.items,
                    hasMoreArtists = hasMore(0, page.items.size, page.total),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Artist letter filter failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun selectAlbumLetter(letter: String?) {
        val normalized = normalizeBrowseLetter(letter)
        val s = _state.value
        if (s.albumLetter == normalized && s.albums.isNotEmpty()) return
        albumLetterJob?.cancel()
        albumLetterJob = viewModelScope.launch {
            _state.value = s.copy(
                albumLetter = normalized,
                albums = emptyList(),
                hasMoreAlbums = true,
                isLoadingMore = true,
                error = null
            )
            try {
                val page = albumsPage(normalized, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Selected album letter ${normalized ?: "All"}: ${page.items.size}/${page.total}")
                _state.value = _state.value.copy(
                    albums = page.items,
                    hasMoreAlbums = hasMore(0, page.items.size, page.total),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Album letter filter failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun normalizeBrowseLetter(letter: String?): String? {
        val value = letter?.trim()?.uppercase() ?: return null
        return when {
            value == "#" -> "#"
            value.length == 1 && value[0] in 'A'..'Z' -> value
            else -> null
        }
    }

    fun loadArtistDetail(artist: Artist) {
        _selectedArtist.value = artist
        _artistAlbums.value = emptyList()
        _artistAppearsOn.value = emptyList()
        _hasMoreArtistTracks.value = true
        currentArtistId = artist.id
        currentArtistName = artist.name
        viewModelScope.launch {
            try {
                val id = artist.id
                if (id == null) {
                    val cached = repo.getCachedArtistTracks(artist.name).orEmpty()
                    _artistTracks.value = cached.take(PAGE_SIZE)
                    _hasMoreArtistTracks.value = cached.size > PAGE_SIZE
                    return@launch
                }
                DebugLog.i("Browse", "Loading artist detail for id=$id")
                // Load tracks and albums in parallel
                launch {
                    try {
                        if (!NetworkMonitor.isOnline.value) {
                            val cached = repo.getCachedArtistTracks(artist.name).orEmpty()
                            _artistTracks.value = cached.take(PAGE_SIZE)
                            _hasMoreArtistTracks.value = cached.size > PAGE_SIZE
                            return@launch
                        }
                        val response = repo.getArtistTracks(id, PAGE_SIZE, 0)
                        _artistTracks.value = response.tracks
                        _hasMoreArtistTracks.value = response.tracks.size >= PAGE_SIZE
                        if (response.tracks.isNotEmpty()) {
                            val current = _selectedArtist.value ?: artist
                            if (current.trackCount <= 0) {
                                _selectedArtist.value = current.copy(trackCount = response.tracks.size)
                            }
                        }
                    } catch (e: Exception) {
                        DebugLog.e("Browse", "Artist tracks failed", e)
                        val cached = repo.getCachedArtistTracks(artist.name).orEmpty()
                        if (cached.isNotEmpty()) {
                            _artistTracks.value = cached.take(PAGE_SIZE)
                            _hasMoreArtistTracks.value = cached.size > PAGE_SIZE
                        }
                    }
                }
                launch {
                    try {
                        if (!NetworkMonitor.isOnline.value) return@launch
                        val detail = repo.getArtistDetail(id)
                        _artistAlbums.value = detail.albums
                        _artistAppearsOn.value = detail.appearsOn
                        val current = _selectedArtist.value ?: artist
                        val detailArtist = detail.artist
                        _selectedArtist.value = current.copy(
                            name = detailArtist?.name?.takeIf { it.isNotBlank() }
                                ?: current.name.ifBlank { artist.name },
                            trackCount = maxOf(
                                current.trackCount,
                                detailArtist?.trackCount ?: 0
                            ),
                            albumCount = maxOf(
                                current.albumCount,
                                detailArtist?.albumCount ?: 0,
                                detail.albums.size
                            ),
                            artPath = detailArtist?.artPath ?: current.artPath ?: artist.artPath
                        )
                    } catch (e: Exception) {
                        DebugLog.e("Browse", "Artist detail failed", e)
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("Browse", "Artist detail failed", e)
            }
        }
    }

    fun loadMoreArtistTracks() {
        if (_isLoadingMoreArtistTracks.value || !_hasMoreArtistTracks.value) return
        val id = currentArtistId
        viewModelScope.launch {
            _isLoadingMoreArtistTracks.value = true
            try {
                val offset = _artistTracks.value.size
                if (id == null || !NetworkMonitor.isOnline.value) {
                    val artistName = _selectedArtist.value?.name?.takeIf { it.isNotBlank() } ?: currentArtistName
                    val cached = repo.getCachedArtistTracks(artistName).orEmpty()
                    val next = cached.drop(offset).take(PAGE_SIZE)
                    _artistTracks.value = _artistTracks.value + next
                    _hasMoreArtistTracks.value = offset + next.size < cached.size
                    return@launch
                }
                val response = repo.getArtistTracks(id, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${response.tracks.size} more artist tracks (offset $offset)")
                _artistTracks.value = _artistTracks.value + response.tracks
                _hasMoreArtistTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more artist tracks failed", e)
                val artistName = _selectedArtist.value?.name?.takeIf { it.isNotBlank() } ?: currentArtistName
                val offset = _artistTracks.value.size
                val cached = repo.getCachedArtistTracks(artistName).orEmpty()
                val next = cached.drop(offset).take(PAGE_SIZE)
                if (next.isNotEmpty()) {
                    _artistTracks.value = _artistTracks.value + next
                    _hasMoreArtistTracks.value = offset + next.size < cached.size
                }
            } finally {
                _isLoadingMoreArtistTracks.value = false
            }
        }
    }

    fun loadAlbumTracks(albumName: String) {
        viewModelScope.launch {
            try {
                if (!NetworkMonitor.isOnline.value) {
                    val cached = repo.getCachedAlbumTracks(albumName).orEmpty()
                    _albumTracks.value = cached
                    _selectedAlbum.value = _state.value.albums.firstOrNull { it.displayName == albumName }
                    return@launch
                }
                DebugLog.i("Browse", "Loading album tracks for '$albumName'")
                val response: AlbumDetailResponse = repo.getAlbumTracks(albumName)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for album")
                _albumTracks.value = response.tracks
                _selectedAlbum.value = response.album
            } catch (e: Exception) {
                DebugLog.e("Browse", "Album tracks failed for '$albumName'", e)
                val cached = repo.getCachedAlbumTracks(albumName).orEmpty()
                if (cached.isNotEmpty()) {
                    _albumTracks.value = cached
                    _selectedAlbum.value = _state.value.albums.firstOrNull { it.displayName == albumName }
                }
            }
        }
    }

    fun loadGenreTracks(genreName: String) {
        currentGenreName = genreName
        viewModelScope.launch {
            _genreLoading.value = true
            _genreTracks.value = emptyList()
            _hasMoreGenreTracks.value = true
            try {
                if (!NetworkMonitor.isOnline.value) {
                    val cached = repo.getCachedGenreTracks(genreName, PAGE_SIZE, 0).orEmpty()
                    val total = repo.getCachedGenreTrackCount(genreName)
                    _genreTracks.value = cached
                    _hasMoreGenreTracks.value = hasMore(0, cached.size, total)
                    return@launch
                }
                DebugLog.i("Browse", "Loading genre tracks for '$genreName'")
                val response = repo.getGenreTracks(genreName, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for genre '$genreName'")
                _genreTracks.value = response.tracks
                _hasMoreGenreTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Genre tracks failed for '$genreName'", e)
                val cached = repo.getCachedGenreTracks(genreName, PAGE_SIZE, 0).orEmpty()
                if (cached.isNotEmpty()) {
                    val total = repo.getCachedGenreTrackCount(genreName)
                    _genreTracks.value = cached
                    _hasMoreGenreTracks.value = hasMore(0, cached.size, total)
                }
            } finally {
                _genreLoading.value = false
            }
        }
    }

    fun loadMoreGenreTracks() {
        if (_isLoadingMoreGenreTracks.value || !_hasMoreGenreTracks.value) return
        viewModelScope.launch {
            _isLoadingMoreGenreTracks.value = true
            try {
                val offset = _genreTracks.value.size
                if (!NetworkMonitor.isOnline.value) {
                    val cached = repo.getCachedGenreTracks(currentGenreName, PAGE_SIZE, offset).orEmpty()
                    val total = repo.getCachedGenreTrackCount(currentGenreName)
                    _genreTracks.value = _genreTracks.value + cached
                    _hasMoreGenreTracks.value = hasMore(offset, cached.size, total)
                    return@launch
                }
                val response = repo.getGenreTracks(currentGenreName, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${response.tracks.size} more genre tracks (offset $offset)")
                _genreTracks.value = _genreTracks.value + response.tracks
                _hasMoreGenreTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more genre tracks failed", e)
                val offset = _genreTracks.value.size
                val cached = repo.getCachedGenreTracks(currentGenreName, PAGE_SIZE, offset).orEmpty()
                if (cached.isNotEmpty()) {
                    val total = repo.getCachedGenreTrackCount(currentGenreName)
                    _genreTracks.value = _genreTracks.value + cached
                    _hasMoreGenreTracks.value = hasMore(offset, cached.size, total)
                }
            } finally {
                _isLoadingMoreGenreTracks.value = false
            }
        }
    }

    fun loadCountryTracks(name: String) {
        currentCountryName = name
        viewModelScope.launch {
            _countryLoading.value = true
            _countryTracks.value = emptyList()
            _hasMoreCountryTracks.value = true
            try {
                if (!NetworkMonitor.isOnline.value) {
                    val cached = repo.getCachedCountryTracks(name, PAGE_SIZE, 0).orEmpty()
                    val total = repo.getCachedCountryTrackCount(name)
                    _countryTracks.value = cached
                    _hasMoreCountryTracks.value = hasMore(0, cached.size, total)
                    return@launch
                }
                DebugLog.i("Browse", "Loading country tracks for '$name'")
                val response = repo.getCountryTracks(name, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for country '$name'")
                _countryTracks.value = response.tracks
                _hasMoreCountryTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Country tracks failed for '$name'", e)
                val cached = repo.getCachedCountryTracks(name, PAGE_SIZE, 0).orEmpty()
                if (cached.isNotEmpty()) {
                    val total = repo.getCachedCountryTrackCount(name)
                    _countryTracks.value = cached
                    _hasMoreCountryTracks.value = hasMore(0, cached.size, total)
                }
            } finally {
                _countryLoading.value = false
            }
        }
    }

    fun loadMoreCountryTracks() {
        if (_isLoadingMoreCountryTracks.value || !_hasMoreCountryTracks.value) return
        viewModelScope.launch {
            _isLoadingMoreCountryTracks.value = true
            try {
                val offset = _countryTracks.value.size
                if (!NetworkMonitor.isOnline.value) {
                    val cached = repo.getCachedCountryTracks(currentCountryName, PAGE_SIZE, offset).orEmpty()
                    val total = repo.getCachedCountryTrackCount(currentCountryName)
                    _countryTracks.value = _countryTracks.value + cached
                    _hasMoreCountryTracks.value = hasMore(offset, cached.size, total)
                    return@launch
                }
                val response = repo.getCountryTracks(currentCountryName, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${response.tracks.size} more country tracks (offset $offset)")
                _countryTracks.value = _countryTracks.value + response.tracks
                _hasMoreCountryTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more country tracks failed", e)
                val offset = _countryTracks.value.size
                val cached = repo.getCachedCountryTracks(currentCountryName, PAGE_SIZE, offset).orEmpty()
                if (cached.isNotEmpty()) {
                    val total = repo.getCachedCountryTrackCount(currentCountryName)
                    _countryTracks.value = _countryTracks.value + cached
                    _hasMoreCountryTracks.value = hasMore(offset, cached.size, total)
                }
            } finally {
                _isLoadingMoreCountryTracks.value = false
            }
        }
    }

    fun loadLanguageTracks(name: String) {
        currentLanguageName = name
        viewModelScope.launch {
            _languageLoading.value = true
            _languageTracks.value = emptyList()
            _hasMoreLanguageTracks.value = true
            try {
                if (!NetworkMonitor.isOnline.value) {
                    val cached = repo.getCachedLanguageTracks(name, PAGE_SIZE, 0).orEmpty()
                    val total = repo.getCachedLanguageTrackCount(name)
                    _languageTracks.value = cached
                    _hasMoreLanguageTracks.value = hasMore(0, cached.size, total)
                    return@launch
                }
                DebugLog.i("Browse", "Loading language tracks for '$name'")
                val response = repo.getLanguageTracks(name, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for language '$name'")
                _languageTracks.value = response.tracks
                _hasMoreLanguageTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Language tracks failed for '$name'", e)
                val cached = repo.getCachedLanguageTracks(name, PAGE_SIZE, 0).orEmpty()
                if (cached.isNotEmpty()) {
                    val total = repo.getCachedLanguageTrackCount(name)
                    _languageTracks.value = cached
                    _hasMoreLanguageTracks.value = hasMore(0, cached.size, total)
                }
            } finally {
                _languageLoading.value = false
            }
        }
    }

    fun loadMoreLanguageTracks() {
        if (_isLoadingMoreLanguageTracks.value || !_hasMoreLanguageTracks.value) return
        viewModelScope.launch {
            _isLoadingMoreLanguageTracks.value = true
            try {
                val offset = _languageTracks.value.size
                if (!NetworkMonitor.isOnline.value) {
                    val cached = repo.getCachedLanguageTracks(currentLanguageName, PAGE_SIZE, offset).orEmpty()
                    val total = repo.getCachedLanguageTrackCount(currentLanguageName)
                    _languageTracks.value = _languageTracks.value + cached
                    _hasMoreLanguageTracks.value = hasMore(offset, cached.size, total)
                    return@launch
                }
                val response = repo.getLanguageTracks(currentLanguageName, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${response.tracks.size} more language tracks (offset $offset)")
                _languageTracks.value = _languageTracks.value + response.tracks
                _hasMoreLanguageTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more language tracks failed", e)
                val offset = _languageTracks.value.size
                val cached = repo.getCachedLanguageTracks(currentLanguageName, PAGE_SIZE, offset).orEmpty()
                if (cached.isNotEmpty()) {
                    val total = repo.getCachedLanguageTrackCount(currentLanguageName)
                    _languageTracks.value = _languageTracks.value + cached
                    _hasMoreLanguageTracks.value = hasMore(offset, cached.size, total)
                }
            } finally {
                _isLoadingMoreLanguageTracks.value = false
            }
        }
    }
}
