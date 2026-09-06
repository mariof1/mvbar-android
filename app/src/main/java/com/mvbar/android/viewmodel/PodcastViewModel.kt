package com.mvbar.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.data.ActivityQueue
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.toEntity
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

    private val _preview = MutableStateFlow<PodcastPreview?>(null)
    val preview: StateFlow<PodcastPreview?> = _preview.asStateFlow()

    private val _previewLoading = MutableStateFlow(false)
    val previewLoading: StateFlow<Boolean> = _previewLoading.asStateFlow()

    private val _previewError = MutableStateFlow<String?>(null)
    val previewError: StateFlow<String?> = _previewError.asStateFlow()

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
            val cached = try { db.podcastDao().getAllPodcasts().map { it.toModel() } } catch (_: Exception) { emptyList() }
            if (cached.isNotEmpty()) _podcasts.value = cached
            if (!NetworkMonitor.isOnline.value) {
                _isLoading.value = false
                return@launch
            }
            try {
                val r = api.getPodcasts()
                _podcasts.value = r.podcasts
                db.podcastDao().insertPodcasts(r.podcasts.map { it.toEntity() })
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
            val cached = try { db.podcastDao().getContinueListening(50).map { it.toModel() } } catch (_: Exception) { emptyList() }
            if (cached.isNotEmpty()) _continueListening.value = cached
            if (!NetworkMonitor.isOnline.value) return@launch
            try {
                val r = api.getNewEpisodes()
                _continueListening.value = r.episodes
                db.podcastDao().insertEpisodes(r.episodes.map { it.toEntity() })
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Failed to load continue listening", e)
            }
        }
    }

    fun loadPodcastDetail(podcastId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val cachedPodcast = try {
                db.podcastDao().getAllPodcasts().find { it.id == podcastId }?.toModel()
            } catch (_: Exception) { null }
            val cachedEpisodes = try {
                db.podcastDao().getEpisodes(podcastId).map { it.toModel() }
            } catch (_: Exception) { emptyList() }
            if (cachedPodcast != null) _selectedPodcast.value = cachedPodcast
            if (cachedEpisodes.isNotEmpty()) _episodes.value = cachedEpisodes
            if (!NetworkMonitor.isOnline.value) {
                _isLoading.value = false
                return@launch
            }
            try {
                val r = api.getPodcastDetail(podcastId)
                _selectedPodcast.value = r.podcast
                _episodes.value = r.episodes
                r.podcast?.let { db.podcastDao().insertPodcasts(listOf(it.toEntity())) }
                db.podcastDao().replaceEpisodes(podcastId, r.episodes.map { it.toEntity() })
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

    fun previewPodcast(feedUrl: String?) {
        if (feedUrl.isNullOrBlank()) {
            _preview.value = null
            _previewError.value = "No RSS feed available"
            _previewLoading.value = false
            return
        }
        viewModelScope.launch {
            _preview.value = null
            _previewError.value = null
            _previewLoading.value = true
            try {
                if (!NetworkMonitor.isOnline.value) {
                    _previewError.value = "Podcast details need a network connection"
                    return@launch
                }
                val r = api.previewPodcast(feedUrl)
                _preview.value = r.preview
                if (r.preview == null) _previewError.value = "No podcast details available"
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Preview failed", e)
                _previewError.value = e.message ?: "Preview failed"
            } finally {
                _previewLoading.value = false
            }
        }
    }

    fun clearPreview() {
        _preview.value = null
        _previewError.value = null
        _previewLoading.value = false
    }

    fun subscribe(feedUrl: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _subscribing.value = true
            _error.value = null
            try {
                if (!NetworkMonitor.isOnline.value) {
                    ActivityQueue.enqueuePodcastSubscribe(feedUrl)
                    _error.value = "Subscription will sync when online"
                    _subscribing.value = false
                    return@launch
                }
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
            _podcasts.value = _podcasts.value.filter { it.id != podcastId }
            try { db.podcastDao().deletePodcast(podcastId) } catch (_: Exception) {}
            try {
                if (NetworkMonitor.isOnline.value) api.unsubscribePodcast(podcastId)
                else ActivityQueue.enqueuePodcastUnsubscribe(podcastId)
                DebugLog.i("Podcast", "Unsubscribed from podcast $podcastId")
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Unsubscribe failed", e)
                ActivityQueue.enqueuePodcastUnsubscribe(podcastId)
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
            ActivityQueue.enqueuePodcastPlayed(episodeId, played)
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
            try {
                if (NetworkMonitor.isOnline.value) loadPodcasts() // refresh unplayed counts
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Mark played failed", e)
            }
        }
    }

    fun playEpisode(episode: Episode, playbackContext: List<Episode>? = null) {
        viewModelScope.launch {
            _playingEpisode.value = episode

            DebugLog.i("Podcast", "Playing episode ${episode.id}: ${episode.title}")

            val autoContinue = isServerAutoContinueEnabled()
            val queueEpisodes = if (autoContinue) {
                buildEpisodeQueue(episode, playbackContext)
            } else {
                listOf(episode)
            }
            val startIndex = queueEpisodes.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
            val tracks = queueEpisodes.map { it.toPseudoTrack() }
            val resumePositions = queueEpisodes
                .filter { !it.played && it.positionMs > 0L }
                .associate { -it.id to it.positionMs }

            if (resumePositions.containsKey(-episode.id)) {
                DebugLog.i("Podcast", "Will resume episode ${episode.id} from ${episode.positionMs}ms")
            }
            if (queueEpisodes.size > 1) {
                DebugLog.i("Podcast", "Queued ${queueEpisodes.size - 1} follow-up episodes")
            }

            com.mvbar.android.social.SocialRealtimeManager.selectLocalLongFormPlayback()
            playerManager.playTracks(
                tracks = tracks,
                startIndex = startIndex,
                customResumePositions = resumePositions
            )

            startProgressSync(episode.id)
        }
    }

    private suspend fun isServerAutoContinueEnabled(): Boolean {
        if (!NetworkMonitor.isOnline.value) return false
        return try {
            api.getPreferences().preferences.autoContinue
        } catch (e: Exception) {
            DebugLog.e("Podcast", "Failed to load playback preferences", e)
            false
        }
    }

    private suspend fun buildEpisodeQueue(
        episode: Episode,
        playbackContext: List<Episode>?
    ): List<Episode> {
        val contextEpisodes = playbackContext
            ?.takeIf { list -> list.isNotEmpty() && list.all { it.podcastId == episode.podcastId } }
        val sourceEpisodes = contextEpisodes ?: loadEpisodesForQueue(episode)
        val startIndex = sourceEpisodes.indexOfFirst { it.id == episode.id }
        if (startIndex < 0) return listOf(episode)

        return sourceEpisodes
            .drop(startIndex)
            .filter { it.id == episode.id || !it.played }
            .distinctBy { it.id }
            .ifEmpty { listOf(episode) }
    }

    private suspend fun loadEpisodesForQueue(episode: Episode): List<Episode> {
        val podcastId = episode.podcastId
        if (podcastId <= 0) return listOf(episode)

        if (NetworkMonitor.isOnline.value) {
            try {
                val response = api.getPodcastDetail(podcastId)
                response.podcast?.let { db.podcastDao().insertPodcasts(listOf(it.toEntity())) }
                db.podcastDao().replaceEpisodes(podcastId, response.episodes.map { it.toEntity() })
                if (response.episodes.isNotEmpty()) return response.episodes
            } catch (e: Exception) {
                DebugLog.e("Podcast", "Failed to load follow-up episodes from API", e)
            }
        }

        return try {
            db.podcastDao().getEpisodes(podcastId).map { it.toModel() }.ifEmpty { listOf(episode) }
        } catch (_: Exception) {
            listOf(episode)
        }
    }

    private fun Episode.toPseudoTrack(): Track {
        val podcastName = podcastTitle
            ?: _selectedPodcast.value?.takeIf { it.id == podcastId }?.title
            ?: "Podcast"
        return Track(
            id = -id,
            title = title,
            artist = podcastName,
            album = podcastName,
            durationMs = durationMs?.toDouble()
        )
    }

    private fun startProgressSync(episodeId: Int) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                val state = playerManager.state.value
                if (state.isPlaying && state.currentTrack?.id == -episodeId && state.position > 0) {
                    ActivityQueue.enqueuePodcastProgress(episodeId, state.position)
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


