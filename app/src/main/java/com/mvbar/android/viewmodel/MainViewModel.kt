package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.data.ActivityQueue
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.player.PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val buckets: List<RecBucket> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = MvbarDatabase.getInstance(app)
    private val repo = MusicRepository.getInstance(db)
    val playerManager = PlayerManager.getInstance(app)

    /** Persisted across auto-resume: whether queue panel was open */
    var queuePanelOpen = false

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Int>>(emptySet())
    val favoriteIds: StateFlow<Set<Int>> = _favoriteIds.asStateFlow()

    private val _favoritesLoading = MutableStateFlow(false)
    val favoritesLoading: StateFlow<Boolean> = _favoritesLoading.asStateFlow()

    private val _favoritesError = MutableStateFlow<String?>(null)
    val favoritesError: StateFlow<String?> = _favoritesError.asStateFlow()

    private val _history = MutableStateFlow<List<Track>>(emptyList())
    val history: StateFlow<List<Track>> = _history.asStateFlow()

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _smartPlaylists = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = _smartPlaylists.asStateFlow()

    private val _searchResults = MutableStateFlow<SearchResults?>(null)
    val searchResults: StateFlow<SearchResults?> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    private companion object {
        const val PAGE_SIZE = 50
        const val ALL_TRACKS_PAGE_SIZE = 100
    }

    // History pagination
    private val _hasMoreHistory = MutableStateFlow(true)
    val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory.asStateFlow()

    private val _isLoadingMoreHistory = MutableStateFlow(false)
    val isLoadingMoreHistory: StateFlow<Boolean> = _isLoadingMoreHistory.asStateFlow()

    // Search pagination
    private val _hasMoreSearch = MutableStateFlow(false)
    val hasMoreSearch: StateFlow<Boolean> = _hasMoreSearch.asStateFlow()

    private val _isLoadingMoreSearch = MutableStateFlow(false)
    val isLoadingMoreSearch: StateFlow<Boolean> = _isLoadingMoreSearch.asStateFlow()

    private var currentSearchQuery: String = ""

    // Lyrics
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    // Playlist detail
    private val _playlistTracks = MutableStateFlow<List<Track>>(emptyList())
    val playlistTracks: StateFlow<List<Track>> = _playlistTracks.asStateFlow()

    private val _playlistLoading = MutableStateFlow(false)
    val playlistLoading: StateFlow<Boolean> = _playlistLoading.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    // Smart playlist detail
    private val _smartPlaylistDetail = MutableStateFlow<SmartPlaylistResponse?>(null)
    val smartPlaylistDetail: StateFlow<SmartPlaylistResponse?> = _smartPlaylistDetail.asStateFlow()

    private val _smartPlaylistLoading = MutableStateFlow(false)
    val smartPlaylistLoading: StateFlow<Boolean> = _smartPlaylistLoading.asStateFlow()

    // All tracks (paginated for queue "All" tab)
    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _allTracks.asStateFlow()

    private val _allTracksLoading = MutableStateFlow(false)
    val allTracksLoading: StateFlow<Boolean> = _allTracksLoading.asStateFlow()

    private val _hasMoreAllTracks = MutableStateFlow(true)
    val hasMoreAllTracks: StateFlow<Boolean> = _hasMoreAllTracks.asStateFlow()

    private val _isLoadingMoreAllTracks = MutableStateFlow(false)

    init {
        viewModelScope.launch { playerManager.connect() }
        // Sync favorite state whenever the current track changes
        viewModelScope.launch {
            var lastTrackId: Int? = null
            playerManager.state.collect { state ->
                val trackId = state.currentTrack?.id
                if (trackId != lastTrackId) {
                    lastTrackId = trackId
                    if (trackId != null) {
                        playerManager.setFavorite(trackId in _favoriteIds.value)
                    }
                }
            }
        }
        // Auto-reload home data when network is restored after being offline
        viewModelScope.launch {
            NetworkMonitor.reconnectEvents(app).collect {
                DebugLog.i("Home", "Network restored — refreshing home data")
                loadHome(isRefresh = true)
            }
        }
    }

    fun loadHome(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(
                isLoading = !isRefresh, isRefreshing = isRefresh, error = null
            )
            // Load from cache first
            if (!isRefresh) {
                val cachedBuckets = try { repo.getCachedRecommendations() } catch (_: Exception) { null }
                val cachedRecent = try { repo.getCachedRecentlyAdded(PAGE_SIZE) } catch (_: Exception) { null }
                if (!cachedBuckets.isNullOrEmpty() || !cachedRecent.isNullOrEmpty()) {
                    _homeState.value = HomeState(
                        buckets = cachedBuckets ?: emptyList(),
                        recentlyAdded = cachedRecent ?: emptyList()
                    )
                }
            }
            // Then fetch from API
            try {
                DebugLog.i("Home", "Loading recommendations...")
                val buckets = try {
                    val resp = repo.getRecommendations()
                    DebugLog.i("Home", "Got ${resp.buckets.size} buckets")
                    resp.buckets
                } catch (e: Exception) {
                    DebugLog.e("Home", "Failed to load recommendations", e)
                    _homeState.value.buckets.ifEmpty { emptyList() }
                }
                val recent = try {
                    val resp = repo.getRecentlyAdded(PAGE_SIZE)
                    DebugLog.i("Home", "Got ${resp.tracks.size} recent tracks")
                    resp.tracks
                } catch (e: Exception) {
                    DebugLog.e("Home", "Failed to load recent tracks", e)
                    _homeState.value.recentlyAdded.ifEmpty { emptyList() }
                }
                _homeState.value = HomeState(buckets = buckets, recentlyAdded = recent)
            } catch (e: Exception) {
                DebugLog.e("Home", "loadHome failed", e)
                _homeState.value = _homeState.value.copy(
                    isLoading = false, isRefreshing = false,
                    error = "Failed to load: ${e.message}"
                )
            }
        }
    }

    fun loadFavorites(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) _favoritesLoading.value = true
            _favoritesError.value = null
            // Load from cache first
            if (!isRefresh) {
                val cached = try { repo.getCachedFavorites() } catch (_: Exception) { null }
                if (!cached.isNullOrEmpty()) {
                    _favorites.value = cached
                    _favoriteIds.value = cached.map { it.id }.toSet()
                    _favoritesLoading.value = false
                }
            }
            // Then fetch from API
            try {
                val favTracks = repo.getFavorites().tracks
                _favorites.value = favTracks
                _favoriteIds.value = favTracks.map { it.id }.toSet()
                // Update player favorite state if current track is in favorites
                syncPlayerFavoriteState()
                if (AudioCacheManager.autoCacheFavorites && favTracks.isNotEmpty()) {
                    AudioCacheManager.cacheTracks(favTracks)
                }
            } catch (e: Exception) {
                DebugLog.e("Favorites", "Load failed", e)
                if (_favorites.value.isEmpty()) _favoritesError.value = "Failed to load favorites"
            } finally {
                _favoritesLoading.value = false
            }
        }
    }

    fun loadHistory(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) _historyLoading.value = true
            _historyError.value = null
            _hasMoreHistory.value = true
            // Load from cache first
            if (!isRefresh) {
                val cached = try { repo.getCachedHistory() } catch (_: Exception) { null }
                if (!cached.isNullOrEmpty()) {
                    _history.value = cached
                    _historyLoading.value = false
                }
            }
            // Then fetch from API
            try {
                val result = repo.getHistory(PAGE_SIZE, 0)
                _history.value = result.tracks
                _hasMoreHistory.value = result.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("History", "Load failed", e)
                if (_history.value.isEmpty()) _historyError.value = "Failed to load history"
            } finally {
                _historyLoading.value = false
            }
        }
    }

    fun loadMoreHistory() {
        if (_isLoadingMoreHistory.value || !_hasMoreHistory.value) return
        viewModelScope.launch {
            _isLoadingMoreHistory.value = true
            try {
                val offset = _history.value.size
                val result = repo.getHistory(PAGE_SIZE, offset)
                DebugLog.i("History", "Loaded ${result.tracks.size} more history tracks (offset $offset)")
                _history.value = _history.value + result.tracks
                _hasMoreHistory.value = result.tracks.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("History", "Load more failed", e)
            } finally {
                _isLoadingMoreHistory.value = false
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            // Load from cache first
            val cached = try { repo.getCachedPlaylists() } catch (_: Exception) { null }
            if (!cached.isNullOrEmpty()) _playlists.value = cached
            // Then fetch from API
            try {
                _playlists.value = repo.getPlaylists().playlists
            } catch (e: Exception) {
                DebugLog.e("Playlists", "Load failed", e)
            }
        }
    }

    fun loadSmartPlaylists() {
        viewModelScope.launch {
            try {
                _smartPlaylists.value = repo.getSmartPlaylists().items
            } catch (e: Exception) {
                DebugLog.e("SmartPlaylists", "Load failed", e)
            }
        }
    }

    fun loadPlaylistDetail(playlist: Playlist) {
        _selectedPlaylist.value = playlist
        _playlistTracks.value = emptyList()
        viewModelScope.launch {
            _playlistLoading.value = true
            try {
                val resp = repo.getPlaylistItems(playlist.id)
                _playlistTracks.value = resp.items.mapNotNull { it.track }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Load items failed", e)
            } finally {
                _playlistLoading.value = false
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                repo.createPlaylist(name)
                loadPlaylists()
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Create failed", e)
            }
        }
    }

    fun addToPlaylist(playlistId: Int, track: Track) {
        viewModelScope.launch {
            try {
                repo.addToPlaylist(playlistId, track.id)
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Add track failed", e)
            }
        }
    }

    fun removeFromPlaylist(playlistId: Int, trackId: Int) {
        viewModelScope.launch {
            try {
                repo.removeFromPlaylist(playlistId, trackId)
                _selectedPlaylist.value?.let { loadPlaylistDetail(it) }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Remove track failed", e)
            }
        }
    }

    // Smart playlist detail
    fun loadSmartPlaylistDetail(id: Int) {
        viewModelScope.launch {
            _smartPlaylistLoading.value = true
            _smartPlaylistDetail.value = null
            try {
                _smartPlaylistDetail.value = repo.getSmartPlaylist(id)
            } catch (e: Exception) {
                DebugLog.e("SmartPlaylist", "Load detail failed", e)
            } finally {
                _smartPlaylistLoading.value = false
            }
        }
    }

    // All tracks for queue "All" tab — paginated for large libraries

    fun loadAllTracks() {
        if (_allTracksLoading.value) return
        viewModelScope.launch {
            _allTracksLoading.value = true
            _allTracks.value = emptyList()
            _hasMoreAllTracks.value = true
            try {
                val response = repo.getTracks(ALL_TRACKS_PAGE_SIZE, 0)
                _allTracks.value = response.tracks
                _hasMoreAllTracks.value = response.tracks.size >= ALL_TRACKS_PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("AllTracks", "Load failed", e)
            } finally {
                _allTracksLoading.value = false
            }
        }
    }

    fun loadMoreAllTracks() {
        if (_isLoadingMoreAllTracks.value || !_hasMoreAllTracks.value) return
        viewModelScope.launch {
            _isLoadingMoreAllTracks.value = true
            try {
                val offset = _allTracks.value.size
                val response = repo.getTracks(ALL_TRACKS_PAGE_SIZE, offset)
                DebugLog.i("AllTracks", "Loaded ${response.tracks.size} more (offset $offset)")
                _allTracks.value = _allTracks.value + response.tracks
                _hasMoreAllTracks.value = response.tracks.size >= ALL_TRACKS_PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("AllTracks", "Load more failed", e)
            } finally {
                _isLoadingMoreAllTracks.value = false
            }
        }
    }

    fun createSmartPlaylist(name: String, sort: String, filters: SmartPlaylistFilters) {
        viewModelScope.launch {
            try {
                repo.createSmartPlaylist(name, sort, filters)
                loadSmartPlaylists()
            } catch (e: Exception) {
                DebugLog.e("SmartPlaylist", "Create failed", e)
            }
        }
    }

    fun deleteSmartPlaylist(id: Int) {
        viewModelScope.launch {
            try {
                repo.deleteSmartPlaylist(id)
                loadSmartPlaylists()
            } catch (e: Exception) {
                DebugLog.e("SmartPlaylist", "Delete failed", e)
            }
        }
    }

    fun updateSmartPlaylist(id: Int, name: String, sort: String, filters: SmartPlaylistFilters) {
        viewModelScope.launch {
            try {
                repo.updateSmartPlaylist(id, name, sort, filters)
                loadSmartPlaylists()
                loadSmartPlaylistDetail(id)
            } catch (e: Exception) {
                DebugLog.e("SmartPlaylist", "Update failed", e)
            }
        }
    }

    suspend fun suggest(kind: String, query: String) = repo.suggestSmartPlaylist(kind, query)
    suspend fun resolveArtistIds(ids: List<Int>) = repo.resolveArtistIds(ids)

    // Lyrics
    fun loadLyrics(trackId: Int) {
        viewModelScope.launch {
            _lyricsLoading.value = true
            _lyrics.value = emptyList()
            try {
                val raw = repo.getLyrics(trackId)
                if (raw != null) {
                    _lyrics.value = parseLrc(raw)
                }
            } catch (e: Exception) {
                DebugLog.e("Lyrics", "Load failed", e)
            } finally {
                _lyricsLoading.value = false
            }
        }
    }

    fun prefetchLyrics(trackId: Int) {
        viewModelScope.launch { repo.prefetchLyrics(trackId) }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
        return lrc.lines().mapNotNull { line ->
            regex.matchEntire(line.trim())?.let { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0
                val sec = match.groupValues[2].toLongOrNull() ?: 0
                val ms = match.groupValues[3].let {
                    val v = it.toLongOrNull() ?: 0
                    if (it.length == 2) v * 10 else v
                }
                val timeMs = min * 60_000 + sec * 1000 + ms
                LyricLine(timeMs, match.groupValues[4].trim())
            }
        }.filter { it.text.isNotEmpty() }.sortedBy { it.timeMs }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = null
            _searchLoading.value = false
            _hasMoreSearch.value = false
            currentSearchQuery = ""
            return
        }
        currentSearchQuery = query
        _searchLoading.value = true
        _hasMoreSearch.value = false
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            try {
                val results = repo.search(query, PAGE_SIZE, 0)
                _searchResults.value = results
                _hasMoreSearch.value = results.hits.size >= PAGE_SIZE
            } catch (_: Exception) {}
            _searchLoading.value = false
        }
    }

    fun loadMoreSearchResults() {
        if (_isLoadingMoreSearch.value || !_hasMoreSearch.value) return
        val current = _searchResults.value ?: return
        viewModelScope.launch {
            _isLoadingMoreSearch.value = true
            try {
                val offset = current.hits.size
                val results = repo.search(currentSearchQuery, PAGE_SIZE, offset)
                DebugLog.i("Search", "Loaded ${results.hits.size} more hits (offset $offset)")
                _searchResults.value = current.copy(hits = current.hits + results.hits)
                _hasMoreSearch.value = results.hits.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Search", "Load more failed", e)
            } finally {
                _isLoadingMoreSearch.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = null
        _searchLoading.value = false
        _hasMoreSearch.value = false
        currentSearchQuery = ""
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val tracks = queue ?: listOf(track)
        val idx = tracks.indexOf(track).coerceAtLeast(0)
        playerManager.playTracks(tracks, idx)
        // Sync favorite state for the new track
        playerManager.setFavorite(track.id in _favoriteIds.value)
        // Play recording is handled centrally by PlaybackService.onMediaItemTransition
        // Prefetch lyrics for the track
        prefetchLyrics(track.id)
    }

    fun toggleFavorite(trackId: Int) {
        // Optimistically toggle the favorite ID set
        val currentIds = _favoriteIds.value
        val isCurrentlyFav = trackId in currentIds
        _favoriteIds.value = if (isCurrentlyFav) currentIds - trackId else currentIds + trackId
        // Update player state if this is the current track
        playerManager.state.value.currentTrack?.let {
            if (it.id == trackId) playerManager.setFavorite(!isCurrentlyFav)
        }
        // Queue the action (persisted to Room, flushed when online)
        val action = if (isCurrentlyFav) ActivityQueue.ACTION_REMOVE_FAVORITE
                     else ActivityQueue.ACTION_ADD_FAVORITE
        ActivityQueue.enqueue(action, trackId)
        // Auto-cache new favorite if enabled
        if (!isCurrentlyFav && AudioCacheManager.autoCacheFavorites) {
            AudioCacheManager.cacheTrackById(trackId)
        }
    }

    private fun syncPlayerFavoriteState() {
        val currentTrack = playerManager.state.value.currentTrack ?: return
        playerManager.setFavorite(currentTrack.id in _favoriteIds.value)
    }

    fun addToQueue(track: Track) { playerManager.addToQueue(track) }

    /** Save current playback state for auto-resume */
    fun savePlaybackState() {
        viewModelScope.launch {
            val state = playerManager.state.value
            if (state.queue.isEmpty()) return@launch
            val entries = state.queue.map { track ->
                AaPreferences.QueueEntry(
                    mediaId = if (track.id < 0) "ep:${-track.id}" else track.id.toString(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artUri = track.artPath?.let { com.mvbar.android.data.api.ApiClient.artPathUrl(it) }
                        ?: if (track.id < 0) com.mvbar.android.data.api.ApiClient.episodeArtUrl(-track.id)
                        else com.mvbar.android.data.api.ApiClient.trackArtUrl(track.id)
                )
            }
            AaPreferences.savePlaybackState(getApplication(), entries, state.queueIndex, state.position)
            AaPreferences.saveQueuePanelOpen(getApplication(), queuePanelOpen)
        }
    }
}
