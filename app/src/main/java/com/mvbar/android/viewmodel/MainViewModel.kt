package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.model.*
import com.mvbar.android.data.repository.MusicRepository
import com.mvbar.android.player.PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val buckets: List<RecBucket> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository()
    val playerManager = PlayerManager.getInstance(app)

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<Track>>(emptyList())
    val history: StateFlow<List<Track>> = _history.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _searchResults = MutableStateFlow<SearchResults?>(null)
    val searchResults: StateFlow<SearchResults?> = _searchResults.asStateFlow()

    init {
        viewModelScope.launch { playerManager.connect() }
    }

    fun loadHome() {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true)
            try {
                val buckets = try {
                    repo.getRecommendations().buckets
                } catch (_: Exception) { emptyList() }
                val recent = try { repo.getRecentlyAdded(20).tracks } catch (_: Exception) { emptyList() }
                _homeState.value = HomeState(buckets = buckets, recentlyAdded = recent)
            } catch (_: Exception) {
                _homeState.value = _homeState.value.copy(isLoading = false)
            }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            try { _favorites.value = repo.getFavorites().tracks } catch (_: Exception) {}
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            try { _history.value = repo.getHistory().tracks } catch (_: Exception) {}
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            try { _playlists.value = repo.getPlaylists().playlists } catch (_: Exception) {}
        }
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
