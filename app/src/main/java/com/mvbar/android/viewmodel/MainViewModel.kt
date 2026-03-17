package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val repo = MusicRepository()
    val playerManager = PlayerManager.getInstance(app)

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

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

    init {
        viewModelScope.launch { playerManager.connect() }
    }

    fun loadHome(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(
                isLoading = !isRefresh, isRefreshing = isRefresh, error = null
            )
            try {
                DebugLog.i("Home", "Loading recommendations...")
                val buckets = try {
                    val resp = repo.getRecommendations()
                    DebugLog.i("Home", "Got ${resp.buckets.size} buckets")
                    resp.buckets
                } catch (e: Exception) {
                    DebugLog.e("Home", "Failed to load recommendations", e)
                    emptyList()
                }
                val recent = try {
                    val resp = repo.getRecentlyAdded(20)
                    DebugLog.i("Home", "Got ${resp.tracks.size} recent tracks")
                    resp.tracks
                } catch (e: Exception) {
                    DebugLog.e("Home", "Failed to load recent tracks", e)
                    emptyList()
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
            try {
                val favTracks = repo.getFavorites().tracks
                _favorites.value = favTracks
                // Auto-cache favorites in background if enabled
                if (AudioCacheManager.autoCacheFavorites && favTracks.isNotEmpty()) {
                    AudioCacheManager.cacheTracks(favTracks)
                }
            } catch (e: Exception) {
                DebugLog.e("Favorites", "Load failed", e)
                _favoritesError.value = "Failed to load favorites"
            } finally {
                _favoritesLoading.value = false
            }
        }
    }

    fun loadHistory(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) _historyLoading.value = true
            _historyError.value = null
            try {
                _history.value = repo.getHistory().tracks
            } catch (e: Exception) {
                DebugLog.e("History", "Load failed", e)
                _historyError.value = "Failed to load history"
            } finally {
                _historyLoading.value = false
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            try { _searchResults.value = repo.search(query) } catch (_: Exception) {}
        }
    }

    fun clearSearch() { _searchResults.value = null }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val tracks = queue ?: listOf(track)
        val idx = tracks.indexOf(track).coerceAtLeast(0)
        playerManager.playTracks(tracks, idx)
        viewModelScope.launch {
            try { repo.recordPlay(track.id) } catch (_: Exception) {}
        }
        // Prefetch lyrics for the track
        prefetchLyrics(track.id)
    }

    fun toggleFavorite(trackId: Int) {
        viewModelScope.launch {
            try {
                repo.toggleFavorite(trackId)
                loadFavorites()
            } catch (_: Exception) {}
        }
    }

    fun addToQueue(track: Track) { playerManager.addToQueue(track) }
}
