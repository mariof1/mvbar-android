package com.mvbar.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null
)

class BrowseViewModel : ViewModel() {
    private val repo = MusicRepository()
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

    private companion object {
        const val PAGE_SIZE = 50
    }

    fun loadAll(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !isRefresh, isRefreshing = isRefresh, error = null
            )
            try {
                DebugLog.i("Browse", "Loading artists, albums, genres...")
                val artists = try {
                    val r = repo.getArtists(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.artists.size} artists (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreArtists = r.artists.size >= PAGE_SIZE)
                    r.artists
                } catch (e: Exception) { DebugLog.e("Browse", "Artists failed", e); emptyList() }
                val albums = try {
                    val r = repo.getAlbums(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.albums.size} albums (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreAlbums = r.albums.size >= PAGE_SIZE)
                    r.albums
                } catch (e: Exception) { DebugLog.e("Browse", "Albums failed", e); emptyList() }
                val genres = try {
                    val r = repo.getGenres(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.genres.size} genres (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreGenres = r.genres.size >= PAGE_SIZE)
                    r.genres
                } catch (e: Exception) { DebugLog.e("Browse", "Genres failed", e); emptyList() }
                val countries = try {
                    val r = repo.getCountries(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.countries.size} countries (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreCountries = r.countries.size >= PAGE_SIZE)
                    r.countries
                } catch (e: Exception) { DebugLog.e("Browse", "Countries failed", e); emptyList() }
                val languages = try {
                    val r = repo.getLanguages(PAGE_SIZE, 0)
                    DebugLog.i("Browse", "Got ${r.languages.size} languages (total: ${r.total})")
                    _state.value = _state.value.copy(hasMoreLanguages = r.languages.size >= PAGE_SIZE)
                    r.languages
                } catch (e: Exception) { DebugLog.e("Browse", "Languages failed", e); emptyList() }
                _state.value = _state.value.copy(
                    artists = artists, albums = albums, genres = genres,
                    countries = countries, languages = languages,
                    isLoading = false, isRefreshing = false
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
                val r = repo.getArtists(PAGE_SIZE, s.artists.size)
                DebugLog.i("Browse", "Loaded ${r.artists.size} more artists (offset ${s.artists.size})")
                _state.value = _state.value.copy(
                    artists = s.artists + r.artists,
                    hasMoreArtists = r.artists.size >= PAGE_SIZE,
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
                val r = repo.getAlbums(PAGE_SIZE, s.albums.size)
                DebugLog.i("Browse", "Loaded ${r.albums.size} more albums (offset ${s.albums.size})")
                _state.value = _state.value.copy(
                    albums = s.albums + r.albums,
                    hasMoreAlbums = r.albums.size >= PAGE_SIZE,
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
                val r = repo.getGenres(PAGE_SIZE, s.genres.size)
                DebugLog.i("Browse", "Loaded ${r.genres.size} more genres (offset ${s.genres.size})")
                _state.value = _state.value.copy(
                    genres = s.genres + r.genres,
                    hasMoreGenres = r.genres.size >= PAGE_SIZE,
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
                val r = repo.getCountries(PAGE_SIZE, s.countries.size)
                DebugLog.i("Browse", "Loaded ${r.countries.size} more countries (offset ${s.countries.size})")
                _state.value = _state.value.copy(
                    countries = s.countries + r.countries,
                    hasMoreCountries = r.countries.size >= PAGE_SIZE,
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
                val r = repo.getLanguages(PAGE_SIZE, s.languages.size)
                DebugLog.i("Browse", "Loaded ${r.languages.size} more languages (offset ${s.languages.size})")
                _state.value = _state.value.copy(
                    languages = s.languages + r.languages,
                    hasMoreLanguages = r.languages.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more languages failed", e)
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun setTab(tab: Int) { _state.value = _state.value.copy(selectedTab = tab) }

    fun loadArtistDetail(artist: Artist) {
        _selectedArtist.value = artist
        _artistAlbums.value = emptyList()
        _artistAppearsOn.value = emptyList()
        viewModelScope.launch {
            try {
                artist.id?.let { id ->
                    DebugLog.i("Browse", "Loading artist detail for id=$id")
                    // Load tracks and albums in parallel
                    launch {
                        try {
                            _artistTracks.value = repo.getArtistTracks(id).tracks
                        } catch (e: Exception) {
                            DebugLog.e("Browse", "Artist tracks failed", e)
                        }
                    }
                    launch {
                        try {
                            val detail = repo.getArtistDetail(id)
                            _artistAlbums.value = detail.albums
                            _artistAppearsOn.value = detail.appearsOn
                            // Update artist info from detail if available
                            detail.artist?.let { a ->
                                _selectedArtist.value = artist.copy(
                                    name = a.name.ifEmpty { artist.name },
                                    artPath = a.artPath ?: artist.artPath
                                )
                            }
                        } catch (e: Exception) {
                            DebugLog.e("Browse", "Artist detail failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("Browse", "Artist detail failed", e)
            }
        }
    }

    fun loadAlbumTracks(albumName: String) {
        viewModelScope.launch {
            try {
                DebugLog.i("Browse", "Loading album tracks for '$albumName'")
                val response: AlbumDetailResponse = repo.getAlbumTracks(albumName)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for album")
                _albumTracks.value = response.tracks
                _selectedAlbum.value = response.album
            } catch (e: Exception) {
                DebugLog.e("Browse", "Album tracks failed for '$albumName'", e)
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
                DebugLog.i("Browse", "Loading genre tracks for '$genreName'")
                val response = repo.getGenreTracks(genreName, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for genre '$genreName'")
                _genreTracks.value = response.tracks
                _hasMoreGenreTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Genre tracks failed for '$genreName'", e)
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
                val response = repo.getGenreTracks(currentGenreName, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${response.tracks.size} more genre tracks (offset $offset)")
                _genreTracks.value = _genreTracks.value + response.tracks
                _hasMoreGenreTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more genre tracks failed", e)
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
                DebugLog.i("Browse", "Loading country tracks for '$name'")
                val response = repo.getCountryTracks(name, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for country '$name'")
                _countryTracks.value = response.tracks
                _hasMoreCountryTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Country tracks failed for '$name'", e)
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
                val response = repo.getCountryTracks(currentCountryName, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${response.tracks.size} more country tracks (offset $offset)")
                _countryTracks.value = _countryTracks.value + response.tracks
                _hasMoreCountryTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more country tracks failed", e)
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
                DebugLog.i("Browse", "Loading language tracks for '$name'")
                val response = repo.getLanguageTracks(name, PAGE_SIZE, 0)
                DebugLog.i("Browse", "Got ${response.tracks.size} tracks for language '$name'")
                _languageTracks.value = response.tracks
                _hasMoreLanguageTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Language tracks failed for '$name'", e)
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
                val response = repo.getLanguageTracks(currentLanguageName, PAGE_SIZE, offset)
                DebugLog.i("Browse", "Loaded ${response.tracks.size} more language tracks (offset $offset)")
                _languageTracks.value = _languageTracks.value + response.tracks
                _hasMoreLanguageTracks.value = response.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Browse", "Load more language tracks failed", e)
            } finally {
                _isLoadingMoreLanguageTracks.value = false
            }
        }
    }
}
