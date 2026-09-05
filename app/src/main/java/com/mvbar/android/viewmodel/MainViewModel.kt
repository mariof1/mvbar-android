package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.data.ActivityQueue
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.player.PlayMode
import com.mvbar.android.player.PlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val buckets: List<RecBucket> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val serverRefreshing: Boolean = false,
    val hiddenMixCount: Int = 0,
    val recommendationProfile: String = RecommendationProfile.NEW,
    val slateId: String? = null,
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

    private val _recommendationFeedbackBusy = MutableStateFlow(false)
    val recommendationFeedbackBusy: StateFlow<Boolean> = _recommendationFeedbackBusy.asStateFlow()

    private val _recommendationTuningCount = MutableStateFlow(0)
    val recommendationTuningCount: StateFlow<Int> = _recommendationTuningCount.asStateFlow()

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

    private val _recentSearches = MutableStateFlow<List<RecentSearchItem>>(emptyList())
    val recentSearches: StateFlow<List<RecentSearchItem>> = _recentSearches.asStateFlow()

    private val _recentSearchesLoading = MutableStateFlow(false)
    val recentSearchesLoading: StateFlow<Boolean> = _recentSearchesLoading.asStateFlow()

    private var recentSearchJob: kotlinx.coroutines.Job? = null
    private var recentSearchSessionToken: String? = null

    private companion object {
        const val PAGE_SIZE = 50
        const val ALL_TRACKS_PAGE_SIZE = 100
        const val UNSYNCED_LYRIC_TIME = -1L
        val lrcTimestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{2,3}))?]""")
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
        // Pre-load playlists so the Add-to-Playlist dialog is never empty
        loadPlaylists()
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
                DebugLog.i("Offline", "Network restored — refreshing cached app data")
                loadHome(isRefresh = true)
                loadFavorites(isRefresh = true)
                loadHistory(isRefresh = true)
                loadPlaylists()
                loadSmartPlaylists()
                _selectedPlaylist.value?.let { loadPlaylistDetail(it) }
            }
        }
        // Poll favorites every 5 minutes so changes from other devices appear quickly
        viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000L)
                loadFavorites(isRefresh = true)
            }
        }
    }

    private var homeJob: Job? = null
    private var favoritesJob: Job? = null
    private var homeLoadedOnce = false
    private var lastFavoritesLoadTime = 0L

    /** Called when the app returns to foreground — refreshes favorites and recommendations. */
    fun onAppResumed() {
        loadFavorites(isRefresh = true)
        // On first launch, let HomeScreen's LaunchedEffect handle it (cache-first).
        // On subsequent resumes, refresh silently in the background.
        if (homeLoadedOnce) {
            loadHome(isRefresh = true)
        }
    }

    private fun List<RecBucket>.withCompleteRecommendationPayloads(): List<RecBucket> =
        filter { bucket -> bucket.count <= bucket.tracks.size }

    private fun List<Track>.toRecentlyAddedBucket(): RecBucket? {
        val tracks = take(PAGE_SIZE)
        if (tracks.isEmpty()) return null
        val artPairs = tracks
            .mapNotNull { track -> track.artPath?.let { it to (track.artHash ?: "") } }
            .distinctBy { it.first }
            .take(4)
        return RecBucket(
            key = "recently_added",
            name = "Recently Added",
            subtitle = "Newest tracks in your library",
            count = tracks.size,
            tracks = tracks,
            artPaths = artPairs.map { it.first },
            artHashes = artPairs.map { it.second }
        )
    }

    fun loadHome(isRefresh: Boolean = false) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _homeState.value = _homeState.value.copy(
                isLoading = !isRefresh && _homeState.value.buckets.isEmpty(),
                isRefreshing = isRefresh, error = null
            )
            // Load from cache first for instant display
            if (!isRefresh || !NetworkMonitor.isOnline.value) {
                val cachedBuckets = try {
                    repo.getCachedRecommendations()?.withCompleteRecommendationPayloads()
                } catch (_: Exception) { null }
                val cachedRecentBucket = if (cachedBuckets?.any { it.key == "recently_added" } == true) {
                    null
                } else {
                    try { repo.getCachedRecentlyAdded(PAGE_SIZE)?.toRecentlyAddedBucket() } catch (_: Exception) { null }
                }
                val displayBuckets = buildList {
                    cachedBuckets?.let { addAll(it) }
                    cachedRecentBucket?.let { add(it) }
                }
                if (displayBuckets.isNotEmpty()) {
                    _homeState.value = _homeState.value.copy(
                        buckets = displayBuckets,
                        isLoading = false
                    )
                }
            }
            if (!NetworkMonitor.isOnline.value) {
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    serverRefreshing = false
                )
                homeLoadedOnce = true
                return@launch
            }
            // Then fetch recommendation buckets from API. A stale response means the
            // server is rebuilding in the background, so retry briefly without making
            // the user pull to refresh repeatedly.
            try {
                DebugLog.i("Home", "Loading recommendations...")
                var staleRetries = 0
                do {
                    val resp = repo.getRecommendations()
                    val buckets = resp.buckets
                        .map { it.withPlaybackContext(resp.slateId) }
                        .withCompleteRecommendationPayloads()
                    try {
                        repo.cacheRecommendations(buckets)
                    } catch (e: Exception) {
                        DebugLog.e("Home", "Failed to cache recommendations", e)
                    }
                    val rebuilding = resp.stale && resp.refreshing
                    DebugLog.i("Home", "Got ${buckets.size} buckets; stale=$rebuilding")
                    _homeState.value = HomeState(
                        buckets = buckets,
                        serverRefreshing = rebuilding,
                        hiddenMixCount = resp.hiddenMixCount,
                        recommendationProfile = resp.recommendationProfile,
                        slateId = resp.slateId
                    )
                    if (rebuilding && staleRetries < 3) {
                        staleRetries += 1
                        delay(5_000)
                    } else {
                        break
                    }
                } while (true)
                homeLoadedOnce = true
            } catch (e: Exception) {
                DebugLog.e("Home", "loadHome failed", e)
                _homeState.value = _homeState.value.copy(
                    isLoading = false, isRefreshing = false, serverRefreshing = false,
                    error = if (_homeState.value.buckets.isEmpty()) "Failed to load: ${e.message}" else null
                )
            }
        }
    }

    fun hideRecommendationBucket(bucket: RecBucket) {
        if (_recommendationFeedbackBusy.value || !NetworkMonitor.isOnline.value) return
        _recommendationFeedbackBusy.value = true
        val previous = _homeState.value
        _homeState.value = previous.copy(
            buckets = previous.buckets.filterNot { it.key == bucket.key },
            hiddenMixCount = previous.hiddenMixCount + 1
        )
        viewModelScope.launch {
            try {
                val result = repo.sendRecommendationFeedback(
                    RecommendationFeedbackRequest(
                        action = RecommendationFeedbackAction.HIDE_BUCKET,
                        bucketKey = bucket.key
                    )
                )
                _homeState.value = _homeState.value.copy(
                    hiddenMixCount = result.hiddenMixCount ?: _homeState.value.hiddenMixCount
                )
                try {
                    repo.cacheRecommendations(_homeState.value.buckets)
                } catch (cacheError: Exception) {
                    DebugLog.e("Recommendations", "Failed to update recommendation cache", cacheError)
                }
                com.mvbar.android.ui.components.ToastManager.show(
                    "Hidden “${bucket.name}”",
                    com.mvbar.android.ui.components.ToastIcon.SUCCESS
                )
            } catch (e: Exception) {
                DebugLog.e("Recommendations", "Failed to hide ${bucket.key}", e)
                _homeState.value = previous
                com.mvbar.android.ui.components.ToastManager.show(
                    "Could not hide this mix",
                    com.mvbar.android.ui.components.ToastIcon.ERROR
                )
            } finally {
                _recommendationFeedbackBusy.value = false
            }
        }
    }

    fun restoreHiddenRecommendationBuckets() {
        if (_recommendationFeedbackBusy.value || !NetworkMonitor.isOnline.value) return
        _recommendationFeedbackBusy.value = true
        viewModelScope.launch {
            try {
                repo.clearHiddenRecommendationBuckets()
                _homeState.value = _homeState.value.copy(hiddenMixCount = 0, serverRefreshing = true)
                com.mvbar.android.ui.components.ToastManager.show(
                    "Restoring your recommendation mixes",
                    com.mvbar.android.ui.components.ToastIcon.SUCCESS
                )
                loadHome(isRefresh = true)
            } catch (e: Exception) {
                DebugLog.e("Recommendations", "Failed to restore hidden mixes", e)
                _homeState.value = _homeState.value.copy(serverRefreshing = false)
                com.mvbar.android.ui.components.ToastManager.show(
                    "Could not restore recommendation mixes",
                    com.mvbar.android.ui.components.ToastIcon.ERROR
                )
            } finally {
                _recommendationFeedbackBusy.value = false
            }
        }
    }

    fun submitRecommendationFeedback(action: String, track: Track? = playerManager.state.value.currentTrack) {
        val current = track ?: return
        val bucketKey = current.recommendationBucketKey ?: return
        if (_recommendationFeedbackBusy.value || !NetworkMonitor.isOnline.value) return
        _recommendationFeedbackBusy.value = true
        viewModelScope.launch {
            try {
                repo.sendRecommendationFeedback(
                    RecommendationFeedbackRequest(
                        action = action,
                        trackId = current.id,
                        artist = current.displayArtist,
                        bucketKey = bucketKey
                    )
                )
                val message = when (action) {
                    RecommendationFeedbackAction.MORE_LIKE_THIS -> "We’ll use more music like this"
                    RecommendationFeedbackAction.LESS_LIKE_ARTIST -> "We’ll play less from ${current.displayArtist}"
                    RecommendationFeedbackAction.NOT_FOR_ME -> "This track will not be recommended again"
                    else -> "Recommendation preferences updated"
                }
                com.mvbar.android.ui.components.ToastManager.show(
                    message,
                    com.mvbar.android.ui.components.ToastIcon.SUCCESS
                )
                if (action == RecommendationFeedbackAction.NOT_FOR_ME) playerManager.next()
                loadRecommendationFeedback()
            } catch (e: Exception) {
                DebugLog.e("Recommendations", "Failed to save $action", e)
                com.mvbar.android.ui.components.ToastManager.show(
                    "Could not save recommendation feedback",
                    com.mvbar.android.ui.components.ToastIcon.ERROR
                )
            } finally {
                _recommendationFeedbackBusy.value = false
            }
        }
    }

    fun loadRecommendationFeedback() {
        if (!NetworkMonitor.isOnline.value) return
        viewModelScope.launch {
            try {
                _recommendationTuningCount.value = repo.getRecommendationFeedback().preferences.size
            } catch (e: Exception) {
                DebugLog.e("Recommendations", "Failed to load recommendation tuning", e)
            }
        }
    }

    fun resetRecommendationFeedback() {
        if (_recommendationFeedbackBusy.value || !NetworkMonitor.isOnline.value) return
        _recommendationFeedbackBusy.value = true
        viewModelScope.launch {
            try {
                repo.clearAllRecommendationFeedback()
                _recommendationTuningCount.value = 0
                com.mvbar.android.ui.components.ToastManager.show(
                    "Recommendation tuning reset",
                    com.mvbar.android.ui.components.ToastIcon.SUCCESS
                )
                loadHome(isRefresh = true)
            } catch (e: Exception) {
                DebugLog.e("Recommendations", "Failed to reset tuning", e)
                com.mvbar.android.ui.components.ToastManager.show(
                    "Could not reset recommendation tuning",
                    com.mvbar.android.ui.components.ToastIcon.ERROR
                )
            } finally {
                _recommendationFeedbackBusy.value = false
            }
        }
    }

    fun loadFavorites(isRefresh: Boolean = false) {
        // Debounce: skip if successfully loaded within last 30 seconds
        val now = System.currentTimeMillis()
        if (NetworkMonitor.isOnline.value && isRefresh && now - lastFavoritesLoadTime < 30_000) {
            return
        }
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            if (!isRefresh) _favoritesLoading.value = true
            _favoritesError.value = null
            // Load from cache first
            if (!isRefresh || !NetworkMonitor.isOnline.value) {
                val cached = try { repo.getCachedFavorites() } catch (_: Exception) { null }
                if (!cached.isNullOrEmpty()) {
                    _favorites.value = cached
                    _favoriteIds.value = cached.map { it.id }.toSet()
                    _favoritesLoading.value = false
                }
            }
            if (!NetworkMonitor.isOnline.value) {
                syncPlayerFavoriteState()
                _favoritesLoading.value = false
                return@launch
            }
            // Then fetch from API
            try {
                val favTracks = repo.getFavorites().tracks
                _favorites.value = favTracks
                _favoriteIds.value = favTracks.map { it.id }.toSet()
                lastFavoritesLoadTime = System.currentTimeMillis()
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
            if (!isRefresh || !NetworkMonitor.isOnline.value) {
                val cached = try { repo.getCachedHistory() } catch (_: Exception) { null }
                if (!cached.isNullOrEmpty()) {
                    _history.value = cached
                    _historyLoading.value = false
                }
            }
            if (!NetworkMonitor.isOnline.value) {
                _hasMoreHistory.value = false
                _historyLoading.value = false
                return@launch
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
                if (!NetworkMonitor.isOnline.value) {
                    _hasMoreHistory.value = false
                    return@launch
                }
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
            if (!NetworkMonitor.isOnline.value) return@launch
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
            if (!NetworkMonitor.isOnline.value) return@launch
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
                val cached = repo.getCachedPlaylistItems(playlist.id)
                    ?.mapNotNull { it.toTrack() }
                    .orEmpty()
                if (cached.isNotEmpty()) _playlistTracks.value = cached
                if (!NetworkMonitor.isOnline.value) return@launch
                val resp = repo.getPlaylistItems(playlist.id)
                _playlistTracks.value = resp.items.mapNotNull { it.toTrack() }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Load items failed", e)
                val cached = repo.getCachedPlaylistItems(playlist.id)
                    ?.mapNotNull { it.toTrack() }
                    .orEmpty()
                if (cached.isNotEmpty()) _playlistTracks.value = cached
            } finally {
                _playlistLoading.value = false
            }
        }
    }

    private suspend fun refreshCachedPlaylistsState() {
        _playlists.value = repo.getCachedPlaylists().orEmpty()
        _selectedPlaylist.value?.let { selected ->
            _selectedPlaylist.value = _playlists.value.firstOrNull { it.id == selected.id } ?: selected
        }
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                if (NetworkMonitor.isOnline.value) {
                    repo.createPlaylist(trimmed)
                    loadPlaylists()
                } else {
                    val playlist = repo.createCachedPlaylist(trimmed) ?: return@launch
                    ActivityQueue.enqueuePlaylistCreate(playlist.id, trimmed)
                    refreshCachedPlaylistsState()
                }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Create failed", e)
                val playlist = repo.createCachedPlaylist(trimmed) ?: return@launch
                ActivityQueue.enqueuePlaylistCreate(playlist.id, trimmed)
                refreshCachedPlaylistsState()
            }
        }
    }

    fun renamePlaylist(id: Int, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.renameCachedPlaylist(id, trimmed)
            val open = _selectedPlaylist.value
            if (open != null && open.id == id) {
                _selectedPlaylist.value = open.copy(name = trimmed)
            }
            refreshCachedPlaylistsState()
            try {
                if (id < 0 || !NetworkMonitor.isOnline.value) {
                    ActivityQueue.enqueuePlaylistRename(id, trimmed)
                } else {
                    repo.renamePlaylist(id, trimmed)
                    loadPlaylists()
                }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Rename failed", e)
                ActivityQueue.enqueuePlaylistRename(id, trimmed)
            }
        }
    }

    fun deletePlaylist(id: Int) {
        viewModelScope.launch {
            repo.deleteCachedPlaylist(id)
            if (_selectedPlaylist.value?.id == id) {
                _selectedPlaylist.value = null
                _playlistTracks.value = emptyList()
            }
            refreshCachedPlaylistsState()
            try {
                if (id < 0 || !NetworkMonitor.isOnline.value) {
                    ActivityQueue.enqueuePlaylistDelete(id)
                } else {
                    repo.deletePlaylist(id)
                    loadPlaylists()
                }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Delete failed", e)
                ActivityQueue.enqueuePlaylistDelete(id)
            }
        }
    }

    fun addToPlaylist(playlistId: Int, track: Track) {
        viewModelScope.launch {
            repo.addCachedTrackToPlaylist(playlistId, track.id)
            if (_selectedPlaylist.value?.id == playlistId) {
                val existing = _playlistTracks.value
                if (existing.none { it.id == track.id }) _playlistTracks.value = existing + track
            }
            refreshCachedPlaylistsState()
            try {
                if (playlistId < 0 || !NetworkMonitor.isOnline.value) {
                    ActivityQueue.enqueuePlaylistAddTrack(playlistId, track.id)
                } else {
                    repo.addToPlaylist(playlistId, track.id)
                    loadPlaylists()
                }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Add track failed", e)
                ActivityQueue.enqueuePlaylistAddTrack(playlistId, track.id)
            }
        }
    }

    fun addTracksToPlaylist(playlistId: Int, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.forEach { repo.addCachedTrackToPlaylist(playlistId, it.id) }
            if (_selectedPlaylist.value?.id == playlistId) {
                val existing = _playlistTracks.value
                _playlistTracks.value = (existing + tracks).distinctBy { it.id }
            }
            refreshCachedPlaylistsState()
            try {
                if (playlistId < 0 || !NetworkMonitor.isOnline.value) {
                    tracks.forEach { ActivityQueue.enqueuePlaylistAddTrack(playlistId, it.id) }
                } else {
                    repo.addTracksToPlaylist(playlistId, tracks.map { it.id })
                    loadPlaylists()
                }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Add tracks failed", e)
                tracks.forEach { ActivityQueue.enqueuePlaylistAddTrack(playlistId, it.id) }
            }
        }
    }

    fun createPlaylistAndAddTracks(name: String, tracks: List<Track>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val trimmed = name.trim()
            try {
                if (NetworkMonitor.isOnline.value) {
                    val resp = repo.createPlaylist(trimmed)
                    loadPlaylists()
                    val newId = resp.playlist?.id ?: run {
                        DebugLog.e("Playlist", "Create returned no id")
                        return@launch
                    }
                    if (tracks.isNotEmpty()) {
                        repo.addTracksToPlaylist(newId, tracks.map { it.id })
                        loadPlaylists()
                    }
                } else {
                    val playlist = repo.createCachedPlaylist(trimmed) ?: return@launch
                    ActivityQueue.enqueuePlaylistCreate(playlist.id, trimmed)
                    tracks.forEach {
                        repo.addCachedTrackToPlaylist(playlist.id, it.id)
                        ActivityQueue.enqueuePlaylistAddTrack(playlist.id, it.id)
                    }
                    refreshCachedPlaylistsState()
                }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Create+add failed", e)
                val playlist = repo.createCachedPlaylist(trimmed) ?: return@launch
                ActivityQueue.enqueuePlaylistCreate(playlist.id, trimmed)
                tracks.forEach {
                    repo.addCachedTrackToPlaylist(playlist.id, it.id)
                    ActivityQueue.enqueuePlaylistAddTrack(playlist.id, it.id)
                }
                refreshCachedPlaylistsState()
            }
        }
    }

    /** Fetch all tracks for an album by display name. */
    suspend fun fetchAlbumTracks(albumName: String): List<Track> = try {
        if (!NetworkMonitor.isOnline.value) repo.getCachedAlbumTracks(albumName).orEmpty()
        else repo.getAlbumTracks(albumName).tracks
    } catch (e: Exception) {
        DebugLog.e("Collection", "fetchAlbumTracks failed", e)
        repo.getCachedAlbumTracks(albumName).orEmpty()
    }

    /** Fetch all tracks for an artist (paginated; uses large page). */
    suspend fun fetchArtistTracks(artistId: Int, artistName: String? = null): List<Track> = try {
        if (!NetworkMonitor.isOnline.value) artistName?.let { repo.getCachedArtistTracks(it).orEmpty() } ?: emptyList()
        else if (artistId <= 0) artistName?.let { repo.getCachedArtistTracks(it).orEmpty() } ?: emptyList()
        else repo.getArtistTracks(artistId, limit = 1000, offset = 0).tracks
    } catch (e: Exception) {
        DebugLog.e("Collection", "fetchArtistTracks failed", e)
        artistName?.let { repo.getCachedArtistTracks(it).orEmpty() } ?: emptyList()
    }

    fun removeFromPlaylist(playlistId: Int, trackId: Int) {
        viewModelScope.launch {
            repo.removeCachedTrackFromPlaylist(playlistId, trackId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _playlistTracks.value = _playlistTracks.value.filter { it.id != trackId }
            }
            refreshCachedPlaylistsState()
            try {
                if (playlistId < 0 || !NetworkMonitor.isOnline.value) {
                    ActivityQueue.enqueuePlaylistRemoveTrack(playlistId, trackId)
                } else {
                    repo.removeFromPlaylist(playlistId, trackId)
                    loadPlaylists()
                }
            } catch (e: Exception) {
                DebugLog.e("Playlist", "Remove track failed", e)
                ActivityQueue.enqueuePlaylistRemoveTrack(playlistId, trackId)
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

    // All tracks for queue "All" tab — paginated, with offline fallback
    // Always tries API first; falls back to local Room DB cache on failure
    // or when NetworkMonitor reports offline.

    private suspend fun getTracksWithFallback(limit: Int, offset: Int): List<Track> {
        if (!NetworkMonitor.isOnline.value) {
            return repo.getCachedTracksPage(limit, offset) ?: emptyList()
        }
        // Try API first
        try {
            val response = repo.getTracks(limit, offset)
            if (response.tracks.isNotEmpty()) return response.tracks
        } catch (e: Exception) {
            DebugLog.w("AllTracks", "API fetch failed (limit=$limit, offset=$offset), falling back to cache", e)
        }
        // Fallback to local cache
        return repo.getCachedTracksPage(limit, offset) ?: emptyList()
    }

    fun loadAllTracks() {
        if (_allTracksLoading.value) return
        viewModelScope.launch {
            _allTracksLoading.value = true
            _allTracks.value = emptyList()
            _hasMoreAllTracks.value = true
            try {
                val tracks = getTracksWithFallback(ALL_TRACKS_PAGE_SIZE, 0)
                _allTracks.value = tracks
                _hasMoreAllTracks.value = tracks.size >= ALL_TRACKS_PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("AllTracks", "Load failed completely", e)
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
                val tracks = getTracksWithFallback(ALL_TRACKS_PAGE_SIZE, offset)
                DebugLog.i("AllTracks", "Loaded ${tracks.size} more (offset $offset)")
                _allTracks.value = _allTracks.value + tracks
                _hasMoreAllTracks.value = tracks.size >= ALL_TRACKS_PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("AllTracks", "Load more failed completely", e)
            } finally {
                _isLoadingMoreAllTracks.value = false
            }
        }
    }

    fun playShuffledAllTracks(selectedTrack: Track? = null) {
        viewModelScope.launch {
            try {
                // Try online: sample from random offsets across entire library
                val batchSize = 50
                val maxEstimate = 50_000
                val offsets = (0 until 10).map { (0..maxEstimate).random() } + listOf(0)
                val allFetched = mutableListOf<Track>()

                coroutineScope {
                    val deferred = offsets.map { offset ->
                        async {
                            try {
                                repo.getTracks(limit = batchSize, offset = offset).tracks
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    deferred.forEach { allFetched.addAll(it.await()) }
                }

                var tracks = allFetched.distinctBy { it.id }.shuffled().toMutableList()

                // If online fetch yielded nothing, fall back to cached tracks
                if (tracks.isEmpty()) {
                    DebugLog.i("AllTracks", "Online shuffle returned 0 tracks, falling back to cache")
                    val cachedIds = AudioCacheManager.getCachedTrackIds()
                    DebugLog.i("AllTracks", "Audio cache contains ${cachedIds.size} track IDs")
                    if (cachedIds.isNotEmpty()) {
                        val cachedTracks = repo.getTracksByIds(cachedIds) ?: emptyList()
                        tracks = cachedTracks.shuffled().toMutableList()
                    }
                    DebugLog.i("AllTracks", "Offline shuffle: ${tracks.size} cached tracks available")
                }

                if (tracks.isEmpty()) return@launch

                // If a specific track was selected, ensure it's first
                if (selectedTrack != null) {
                    val idx = tracks.indexOfFirst { it.id == selectedTrack.id }
                    if (idx >= 0) {
                        tracks.removeAt(idx)
                    }
                    tracks.add(0, selectedTrack)
                }

                val started = playerManager.playTracks(tracks, 0)
                if (!started) return@launch

                // Enable shuffle mode
                if (playerManager.state.value.playMode != PlayMode.SHUFFLE) {
                    var safety = 0
                    while (playerManager.state.value.playMode != PlayMode.SHUFFLE && safety < 10) {
                        playerManager.cyclePlayMode()
                        safety++
                    }
                }
                playerManager.state.value.currentTrack?.takeIf { it.id > 0 }?.let { prefetchLyrics(it.id) }
            } catch (e: Exception) {
                DebugLog.e("AllTracks", "Shuffle all failed", e)
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

    fun convertSmartPlaylist(id: Int, deleteSmart: Boolean = false, onDone: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val resp = repo.convertSmartPlaylist(id, deleteSmart)
                val ok = resp.isSuccessful
                if (ok) {
                    loadPlaylists()
                    if (deleteSmart) loadSmartPlaylists()
                    com.mvbar.android.ui.components.ToastManager.show(
                        "Smart playlist converted",
                        com.mvbar.android.ui.components.ToastIcon.PLAYLIST
                    )
                } else {
                    DebugLog.e("SmartPlaylist", "Convert failed: HTTP ${resp.code()}")
                    com.mvbar.android.ui.components.ToastManager.show(
                        "Convert failed",
                        com.mvbar.android.ui.components.ToastIcon.ERROR
                    )
                }
                onDone?.invoke(ok)
            } catch (e: Exception) {
                DebugLog.e("SmartPlaylist", "Convert failed", e)
                com.mvbar.android.ui.components.ToastManager.show(
                    "Convert failed",
                    com.mvbar.android.ui.components.ToastIcon.ERROR
                )
                onDone?.invoke(false)
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
                val response = repo.getLyrics(trackId)
                if (response != null) {
                    _lyrics.value = parseLyrics(response.lyrics, response.type)
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

    private fun parseLyrics(raw: String, type: String): List<LyricLine> {
        val text = raw.trim()
        if (text.isBlank()) return emptyList()

        val looksSynced = type.equals("synced", ignoreCase = true) || lrcTimestampRegex.containsMatchIn(text)
        if (looksSynced) {
            val synced = parseLrc(text)
            if (synced.isNotEmpty()) return synced
        }

        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricLine(UNSYNCED_LYRIC_TIME, it) }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        return lrc.lines().flatMap { rawLine ->
            val line = rawLine.trim()
            val matches = lrcTimestampRegex.findAll(line).toList()
            if (matches.isEmpty()) return@flatMap emptyList()

            val lyricText = line.substring(matches.last().range.last + 1).trim()
            if (lyricText.isEmpty()) return@flatMap emptyList()

            matches.map { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0
                val sec = match.groupValues[2].toLongOrNull() ?: 0
                val fraction = match.groupValues.getOrNull(3).orEmpty()
                val ms = when (fraction.length) {
                    2 -> (fraction.toLongOrNull() ?: 0) * 10
                    3 -> fraction.toLongOrNull() ?: 0
                    else -> 0
                }
                LyricLine(min * 60_000 + sec * 1000 + ms, lyricText)
            }
        }.sortedBy { it.timeMs }
    }

    private fun putRecentSearchFirst(item: RecentSearchItem) {
        _recentSearches.value = buildList {
            add(item)
            addAll(_recentSearches.value.filterNot { it.stableKey == item.stableKey })
        }.take(10)
    }

    private fun ensureRecentSearchSession() {
        val activeToken = ApiClient.getToken()
        if (activeToken == recentSearchSessionToken) return
        recentSearchJob?.cancel()
        recentSearchSessionToken = activeToken
        _recentSearches.value = emptyList()
        _recentSearchesLoading.value = false
    }

    fun loadRecentSearches() {
        ensureRecentSearchSession()
        recentSearchJob?.cancel()
        if (!NetworkMonitor.isOnline.value) {
            _recentSearchesLoading.value = false
            return
        }
        recentSearchJob = viewModelScope.launch {
            _recentSearchesLoading.value = true
            try {
                val remote = repo.getRecentSearches(10).searches
                // The server is authoritative here. In particular, do not merge entries
                // retained by this Activity after a logout into another user's history.
                _recentSearches.value = remote.distinctBy { it.stableKey }.take(10)
            } catch (e: Exception) {
                // Search remains usable against older servers; optimistic items stay available.
                DebugLog.e("Search", "Failed to load recent selections", e)
            } finally {
                _recentSearchesLoading.value = false
            }
        }
    }

    fun rememberRecentSearch(request: RecentSearchRequest) {
        ensureRecentSearchSession()
        putRecentSearchFirst(request.optimisticItem())
        if (!NetworkMonitor.isOnline.value) return
        viewModelScope.launch {
            try {
                putRecentSearchFirst(repo.saveRecentSearch(request))
            } catch (e: Exception) {
                // Keep the optimistic item so recent selections still work during outages.
                DebugLog.e("Search", "Failed to sync recent selection", e)
            }
        }
    }

    fun removeRecentSearch(item: RecentSearchItem) {
        ensureRecentSearchSession()
        _recentSearches.value = _recentSearches.value.filterNot { it.stableKey == item.stableKey }
        if (!NetworkMonitor.isOnline.value) return
        viewModelScope.launch {
            try {
                repo.removeRecentSearch(item)
            } catch (e: Exception) {
                DebugLog.e("Search", "Failed to remove recent selection from server", e)
            }
        }
    }

    fun clearRecentSearches() {
        ensureRecentSearchSession()
        _recentSearches.value = emptyList()
        if (!NetworkMonitor.isOnline.value) return
        viewModelScope.launch {
            try {
                repo.clearRecentSearches()
            } catch (e: Exception) {
                DebugLog.e("Search", "Failed to clear recent selections on server", e)
            }
        }
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
                val results = if (NetworkMonitor.isOnline.value) {
                    repo.search(query, PAGE_SIZE, 0)
                } else {
                    repo.searchCached(query, PAGE_SIZE, 0)
                }
                _searchResults.value = results
                _hasMoreSearch.value = results.hits.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Search", "Search failed, falling back to cache", e)
                val results = repo.searchCached(query, PAGE_SIZE, 0)
                _searchResults.value = results
                _hasMoreSearch.value = results.hits.size >= PAGE_SIZE
            }
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
                val results = if (NetworkMonitor.isOnline.value) {
                    repo.search(currentSearchQuery, PAGE_SIZE, offset)
                } else {
                    repo.searchCached(currentSearchQuery, PAGE_SIZE, offset)
                }
                DebugLog.i("Search", "Loaded ${results.hits.size} more hits (offset $offset)")
                _searchResults.value = current.copy(hits = current.hits + results.hits)
                _hasMoreSearch.value = results.hits.size >= PAGE_SIZE
            } catch (e: Exception) {
                DebugLog.e("Search", "Load more failed", e)
                val offset = current.hits.size
                val results = repo.searchCached(currentSearchQuery, PAGE_SIZE, offset)
                _searchResults.value = current.copy(hits = current.hits + results.hits)
                _hasMoreSearch.value = results.hits.size >= PAGE_SIZE
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
        val started = playerManager.playTracks(tracks, idx)
        if (!started) return
        val activeTrack = playerManager.state.value.currentTrack ?: track
        // Sync favorite state for the new track
        playerManager.setFavorite(activeTrack.id in _favoriteIds.value)
        // Play recording is handled centrally by PlaybackService.onMediaItemTransition
        // Prefetch lyrics for the track
        if (activeTrack.id > 0) prefetchLyrics(activeTrack.id)
        // If playing a single track (e.g. from search), fetch similar tracks as radio queue
        if (tracks.size == 1 && activeTrack.id > 0 && NetworkMonitor.isOnline.value) {
            viewModelScope.launch {
                try {
                    val preferences = repo.getPreferences()
                    if (!preferences.preferences.autoContinue) {
                        DebugLog.i("Radio", "Auto-continue disabled; not appending similar tracks")
                        return@launch
                    }
                    val resp = repo.getSimilarTracks(activeTrack.id)
                    if (resp.tracks.isNotEmpty()) {
                        playerManager.appendTracks(resp.tracks)
                        DebugLog.i("Radio", "Appended ${resp.tracks.size} similar tracks for ${activeTrack.displayTitle}")
                    }
                } catch (e: Exception) {
                    DebugLog.e("Radio", "Failed to fetch similar tracks", e)
                }
            }
        }
    }

    fun toggleFavorite(trackId: Int) {
        // Optimistically toggle the favorite ID set
        val currentIds = _favoriteIds.value
        val isCurrentlyFav = trackId in currentIds
        _favoriteIds.value = if (isCurrentlyFav) currentIds - trackId else currentIds + trackId
        if (isCurrentlyFav) {
            _favorites.value = _favorites.value.filter { it.id != trackId }
        } else {
            knownTrackById(trackId)?.let { track ->
                if (_favorites.value.none { it.id == trackId }) {
                    _favorites.value = (_favorites.value + track.copy(isFavorite = true))
                        .sortedBy { it.displayTitle.lowercase() }
                }
            }
        }
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

    private fun knownTrackById(trackId: Int): Track? {
        return playerManager.state.value.currentTrack?.takeIf { it.id == trackId }
            ?: _allTracks.value.firstOrNull { it.id == trackId }
            ?: _history.value.firstOrNull { it.id == trackId }
            ?: _favorites.value.firstOrNull { it.id == trackId }
            ?: _playlistTracks.value.firstOrNull { it.id == trackId }
            ?: _homeState.value.buckets.asSequence().flatMap { it.tracks.asSequence() }.firstOrNull { it.id == trackId }
    }

    private fun syncPlayerFavoriteState() {
        val currentTrack = playerManager.state.value.currentTrack ?: return
        playerManager.setFavorite(currentTrack.id in _favoriteIds.value)
    }

    fun addToQueue(track: Track): Boolean = playerManager.addToQueue(track)

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
