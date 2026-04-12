package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.toModel
import com.mvbar.android.data.model.*
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.PlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PodcastViewModel(app: Application) : AndroidViewModel(app) {
    private val api get() = ApiClient.api
    private val db = MvbarDatabase.getInstance(app)
    val playerManager = PlayerManager.getInstance(app)

    // Subscriptions list
    private val _podcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val podcasts: StateFlow<List<Podcast>> = _podcasts.asStateFlow()

    // Continue listening episodes
    private val _continueListening = MutableStateFlow<List<Episode>>(emptyList())
    val continueListening: StateFlow<List<Episode>> = _continueListening.asStateFlow()

    // Current podcast detail
    private val _selectedPodcast = MutableStateFlow<Podcast?>(null)
    val selectedPodcast: StateFlow<Podcast?> = _selectedPodcast.asStateFlow()

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    // Search
    private val _searchResults = MutableStateFlow<List<PodcastSearchResult>>(emptyList())
    val searchResults: StateFlow<List<PodcastSearchResult>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _subscribing = MutableStateFlow(false)
    val subscribing: StateFlow<Boolean> = _subscribing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Currently playing episode
    private val _playingEpisode = MutableStateFlow<Episode?>(null)
    val playingEpisode: StateFlow<Episode?> = _playingEpisode.asStateFlow()

    private var progressJob: Job? = null

    fun loadPodcasts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val r = api.getPodcasts()
                _podcasts.value = r.podcasts
                DebugLog.i("Podcast", "Loaded ${r.podcasts.size} subscriptions")
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Failed to load podcasts from API", e)
                // Fall back to local DB cache
                if (_podcasts.value.isEmpty()) {
                    try {
                        val cached = db.podcastDao().getAllPodcasts().map { it.toModel() }
                        if (cached.isNotEmpty()) {
                            _podcasts.value = cached
                            DebugLog.i("Podcast", "Loaded ${cached.size} podcasts from cache")
                        } else {
                            _error.value = "Failed to load podcasts"
                        }
                    } catch (_: Exception) {
                        _error.value = "Failed to load podcasts"
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun loadContinueListening() {
        viewModelScope.launch {
            try {
                val r = api.getNewEpisodes()
                _continueListening.value = r.episodes
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Failed to load continue listening", e)
            }
        }
    }

    fun loadPodcastDetail(podcastId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val r = api.getPodcastDetail(podcastId)
                _selectedPodcast.value = r.podcast
                _episodes.value = r.episodes
                DebugLog.i("Podcast", "Loaded ${r.episodes.size} episodes for podcast $podcastId")
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Failed to load podcast detail from API", e)
                // Fall back to local DB cache
                if (_episodes.value.isEmpty()) {
                    try {
                        val cachedPodcast = db.podcastDao().getAllPodcasts()
                            .find { it.id == podcastId }?.toModel()
                        val cachedEpisodes = db.podcastDao().getEpisodes(podcastId)
                            .map { it.toModel() }
                        if (cachedPodcast != null) _selectedPodcast.value = cachedPodcast
                        if (cachedEpisodes.isNotEmpty()) {
                            _episodes.value = cachedEpisodes
                            DebugLog.i("Podcast", "Loaded ${cachedEpisodes.size} episodes from cache")
                        } else {
                            _error.value = "Failed to load podcast"
                        }
                    } catch (_: Exception) {
                        _error.value = "Failed to load podcast"
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun searchPodcasts(query: String) {
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchLoading.value = true
            try {
                val r = api.searchPodcasts(query.trim())
                _searchResults.value = r.results
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Search failed", e)
            }
            _searchLoading.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun subscribe(feedUrl: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _subscribing.value = true
            _error.value = null
            try {
                api.subscribePodcast(PodcastSubscribeRequest(feedUrl))
                DebugLog.i("Podcast", "Subscribed to $feedUrl")
                loadPodcasts()
                loadContinueListening()
                onSuccess()
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Subscribe failed", e)
                _error.value = e.message ?: "Subscribe failed"
            }
            _subscribing.value = false
        }
    }

    fun unsubscribe(podcastId: Int) {
        viewModelScope.launch {
            try {
                api.unsubscribePodcast(podcastId)
                _podcasts.value = _podcasts.value.filter { it.id != podcastId }
                DebugLog.i("Podcast", "Unsubscribed from podcast $podcastId")
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Unsubscribe failed", e)
            }
        }
    }

    fun refreshPodcast(podcastId: Int) {
        viewModelScope.launch {
            try {
                val r = api.refreshPodcast(podcastId)
                DebugLog.i("Podcast", "Refreshed podcast $podcastId, ${r.newEpisodes} new episodes")
                loadPodcastDetail(podcastId)
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Refresh failed", e)
            }
        }
    }

    fun markEpisodePlayed(episodeId: Int, played: Boolean) {
        viewModelScope.launch {
            try {
                api.markEpisodePlayed(episodeId, EpisodePlayedRequest(played))
                _episodes.value = _episodes.value.map {
                    if (it.id == episodeId) it.copy(played = played) else it
                }
                _continueListening.value = if (played) {
                    _continueListening.value.filter { it.id != episodeId }
                } else {
                    _continueListening.value.map {
                        if (it.id == episodeId) it.copy(played = false) else it
                    }
                }
                loadPodcasts() // refresh unplayed counts
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Mark played failed", e)
            }
        }
    }

    fun playEpisode(episode: Episode) {
        _playingEpisode.value = episode

        DebugLog.i("Podcast", "Playing episode ${episode.id}: ${episode.title}")

        // Create a pseudo-track for the episode so the player can handle it
        // Negative ID distinguishes podcast episodes from music tracks
        val pseudoTrack = Track(
            id = -episode.id,
            title = episode.title,
            artist = episode.podcastTitle,
            album = episode.podcastTitle
        )
        playerManager.playTracks(listOf(pseudoTrack), 0)

        // Resume from saved position if available
        if (episode.positionMs > 0) {
            DebugLog.i("Podcast", "Resuming episode ${episode.id} from ${episode.positionMs}ms")
            viewModelScope.launch {
                // Small delay to let ExoPlayer prepare the media before seeking
                kotlinx.coroutines.delay(500)
                playerManager.seekTo(episode.positionMs)
            }
        }

        // Start periodic progress saving
        startProgressSync(episode.id)
    }

    private fun startProgressSync(episodeId: Int) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                val state = playerManager.state.value
                if (state.isPlaying && state.position > 0) {
                    try {
                        api.updateEpisodeProgress(
                            episodeId,
                            EpisodeProgressRequest(positionMs = state.position)
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun stopEpisode() {
        progressJob?.cancel()
        _playingEpisode.value = null
    }

    fun clearError() {
        _error.value = null
    }
}



