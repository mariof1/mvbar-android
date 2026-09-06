package com.mvbar.android.tv

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvbar.android.tv.data.Album
import com.mvbar.android.tv.data.Artist
import com.mvbar.android.tv.data.Audiobook
import com.mvbar.android.tv.data.AudiobookChapter
import com.mvbar.android.tv.data.Episode
import com.mvbar.android.tv.data.Podcast
import com.mvbar.android.tv.data.RecommendationBucket
import com.mvbar.android.tv.data.SearchResponse
import com.mvbar.android.tv.data.PlaylistCollaborationResponse
import com.mvbar.android.tv.data.SocialUser
import com.mvbar.android.tv.data.Track
import com.mvbar.android.tv.data.TvApiException
import com.mvbar.android.tv.data.TvConnectDevice
import com.mvbar.android.tv.data.TvConnectTrack
import com.mvbar.android.tv.data.TvPlaylist
import com.mvbar.android.tv.data.TvRealtimeClient
import com.mvbar.android.tv.data.TvRealtimeRefresh
import com.mvbar.android.tv.data.TvRepository
import com.mvbar.android.tv.data.TvSession
import com.mvbar.android.tv.data.TvSessionStore
import com.mvbar.android.tv.data.realtimeRefreshForEvent
import com.mvbar.android.tv.playback.PlaybackSnapshot
import com.mvbar.android.tv.playback.PlaybackKind
import com.mvbar.android.tv.playback.PlaybackItem
import com.mvbar.android.tv.playback.TvPlaybackController
import com.mvbar.android.tv.home.TvHomePublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class TvSection(val label: String) {
    FOR_YOU("For You"),
    RECENT("Recently Added"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    PODCASTS("Podcasts"),
    AUDIOBOOKS("Audiobooks")
}

data class TrackCollection(
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
    val playlist: TvPlaylist? = null
)

data class ArtistScreen(
    val artist: Artist,
    val albums: List<Album>,
    val appearsOn: List<Album>,
    val tracks: List<Track>
)

enum class TvActionPane { NONE, TRACK, PLAYLISTS, SHARE, CREATE_PLAYLIST, CONNECT }

data class TvUiState(
    val checkingSession: Boolean = true,
    val signedIn: Boolean = false,
    val serverUrl: String = "",
    val authToken: String = "",
    val email: String = "",
    val googleAuthEnabled: Boolean = false,
    val googleClientId: String? = null,
    val googleAuthServerUrl: String = "",
    val checkingGoogleAuth: Boolean = false,
    val googleSigningIn: Boolean = false,
    val selectedSection: TvSection = TvSection.FOR_YOU,
    val recommendations: List<RecommendationBucket> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<TvPlaylist> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val podcasts: List<Podcast> = emptyList(),
    val newEpisodes: List<Episode> = emptyList(),
    val audiobooks: List<Audiobook> = emptyList(),
    val trackCollection: TrackCollection? = null,
    val selectedPodcast: Podcast? = null,
    val podcastEpisodes: List<Episode> = emptyList(),
    val selectedAudiobook: Audiobook? = null,
    val audiobookChapters: List<AudiobookChapter> = emptyList(),
    val selectedArtist: ArtistScreen? = null,
    val playlistCollaboration: PlaylistCollaborationResponse? = null,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val searchResults: SearchResponse? = null,
    val searchedQuery: String = "",
    val focusSearchResults: Boolean = false,
    val nowPlayingVisible: Boolean = false,
    val actionPane: TvActionPane = TvActionPane.NONE,
    val actionTrack: Track? = null,
    val shareTargets: List<SocialUser> = emptyList(),
    val actionLoading: Boolean = false,
    val notice: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val playback: PlaybackSnapshot = PlaybackSnapshot(),
    val connectDevices: List<TvConnectDevice> = emptyList(),
    val selectedConnectDeviceId: String? = null,
    val localConnectDeviceId: String = ""
) {
    val selectedConnectDevice: TvConnectDevice?
        get() = connectDevices.firstOrNull { it.id == selectedConnectDeviceId }
    val controllingRemote: Boolean
        get() = selectedConnectDevice?.id?.let { it != localConnectDeviceId } == true
    val displayedPlayback: PlaybackSnapshot
        get() = selectedConnectDevice?.takeIf { controllingRemote }?.asPlaybackSnapshot(serverUrl) ?: playback
}

private fun TvConnectDevice.asPlaybackSnapshot(serverUrl: String): PlaybackSnapshot {
    val current = state.track ?: return PlaybackSnapshot()
    val base = serverUrl.trimEnd('/')
    fun TvConnectTrack.toPlaybackItem() = PlaybackItem(
        mediaId = "connect:${id}:${this.id}",
        title = title ?: "Track #${this.id}",
        artist = artist ?: "Unknown artist",
        album = album.orEmpty(),
        streamUrl = "",
        artworkUrl = "$base/api/library/tracks/${this.id}/art",
        kind = PlaybackKind.MUSIC,
        durationMs = durationMs ?: if (this.id == current.id) state.durationMs else 0L,
        trackId = this.id
    )
    val remoteQueue = state.queue.map { it.toPlaybackItem() }.ifEmpty { listOf(current.toPlaybackItem()) }
    val remoteIndex = state.queueIndex.takeIf { it in remoteQueue.indices }
        ?: remoteQueue.indexOfFirst { it.trackId == current.id }.coerceAtLeast(0)
    val item = remoteQueue[remoteIndex]
    return PlaybackSnapshot(
        item = item,
        queue = remoteQueue,
        currentIndex = remoteIndex,
        isPlaying = state.isPlaying,
        hasPrevious = state.queueIndex > 0,
        hasNext = state.queueLength > 0 && state.queueIndex < state.queueLength - 1,
        positionMs = state.positionMs,
        durationMs = state.durationMs.takeIf { it > 0 } ?: current.durationMs ?: 0L
    )
}

class TvViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = TvSessionStore(application)
    private val _state = MutableStateFlow(
        TvUiState(
            serverUrl = sessionStore.lastServerUrl(),
            localConnectDeviceId = sessionStore.clientId
        )
    )
    val state: StateFlow<TvUiState> = _state.asStateFlow()
    private var repository: TvRepository? = null
    private var lastRecordedTrackId: Int? = null
    private var searchJob: Job? = null
    private var googleAuthCheckJob: Job? = null
    private var refreshJob: Job? = null
    private var realtimeRefreshJob: Job? = null
    private var recommendationRefreshJob: Job? = null
    private var noticeJob: Job? = null
    private var pendingDeepLink: Uri? = null
    private var pendingVoiceSearch: Pair<String, Boolean>? = null
    private var lastRefreshElapsed = 0L
    private var lastRecommendationsRefreshElapsed = 0L
    private var lastProgressSyncElapsed = 0L
    private var previousPlayback = PlaybackSnapshot()
    private val homePublisher = TvHomePublisher(application)
    private val realtime = TvRealtimeClient(
        scope = viewModelScope,
        onEvent = { event -> viewModelScope.launch { handleRealtimeEvent(event) } },
        onConnectCommand = ::handleConnectCommand,
        onConnectDevices = { devices ->
            _state.update { state ->
                val available = devices.mapTo(mutableSetOf()) { it.id }
                val selected = state.selectedConnectDeviceId?.takeIf(available::contains)
                    ?: state.localConnectDeviceId.takeIf(available::contains)
                    ?: devices.firstOrNull { it.state.isPlaying }?.id
                    ?: devices.firstOrNull()?.id
                state.copy(connectDevices = devices, selectedConnectDeviceId = selected)
            }
        },
        onConnectError = ::showNotice,
        onSessionInvalidated = ::handleSessionInvalidated
    )

    private val playback = TvPlaybackController(application) { snapshot ->
        val previous = previousPlayback
        previousPlayback = snapshot
        _state.update { it.copy(playback = snapshot) }
        realtime.publishPlayback(snapshot)

        snapshot.item?.trackId?.takeIf { it != lastRecordedTrackId }?.let { trackId ->
            lastRecordedTrackId = trackId
            viewModelScope.launch {
                repository?.recordPlay(trackId)
                delay(2_000L)
                refreshRecommendations()
            }
        }

        if (previous.item?.mediaId != snapshot.item?.mediaId && previous.item != null) {
            syncLongFormProgress(previous, force = true)
        }
        val justPaused = previous.isPlaying && !snapshot.isPlaying
        syncLongFormProgress(snapshot, force = justPaused)
    }

    init {
        val saved = sessionStore.load()
        if (saved == null) {
            _state.update { it.copy(checkingSession = false) }
        } else {
            connect(saved, verify = true)
        }
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (_state.value.signedIn) refreshContent(silent = true)
            }
        }
    }

    fun signIn(serverUrl: String, email: String, password: String) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, googleSigningIn = false, error = null) }
            runCatching {
                TvRepository.login(serverUrl, email, password, sessionStore.clientId)
            }.onSuccess { session ->
                sessionStore.save(session)
                connectNow(session, verify = false)
            }.onFailure(::showError)
        }
    }

    fun checkGoogleAuth(serverUrl: String) {
        googleAuthCheckJob?.cancel()
        val requestedServer = serverUrl.trim().trimEnd('/')
        if (requestedServer.isBlank()) {
            _state.update {
                it.copy(
                    googleAuthEnabled = false,
                    googleClientId = null,
                    googleAuthServerUrl = "",
                    checkingGoogleAuth = false
                )
            }
            return
        }

        googleAuthCheckJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    googleAuthEnabled = false,
                    googleClientId = null,
                    googleAuthServerUrl = requestedServer,
                    checkingGoogleAuth = true
                )
            }
            try {
                val info = TvRepository.googleAuthInfo(requestedServer, sessionStore.clientId)
                _state.update {
                    it.copy(
                        googleAuthEnabled = info.enabled && !info.clientId.isNullOrBlank(),
                        googleClientId = info.clientId?.takeIf(String::isNotBlank),
                        googleAuthServerUrl = requestedServer,
                        checkingGoogleAuth = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        googleAuthEnabled = false,
                        googleClientId = null,
                        googleAuthServerUrl = requestedServer,
                        checkingGoogleAuth = false
                    )
                }
            }
        }
    }

    fun googleSignIn(serverUrl: String, idToken: String) {
        if (_state.value.loading || idToken.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, googleSigningIn = true, error = null) }
            runCatching {
                TvRepository.googleSignIn(serverUrl, idToken, sessionStore.clientId)
            }.onSuccess { session ->
                sessionStore.save(session)
                connectNow(session, verify = false)
            }.onFailure(::showError)
        }
    }

    fun onAppResumed() {
        val elapsed = SystemClock.elapsedRealtime()
        if (_state.value.signedIn && elapsed - lastRefreshElapsed >= RESUME_REFRESH_DEBOUNCE_MS) {
            refreshContent(silent = true)
        }
    }

    fun handleDeepLink(uri: Uri) {
        if (!_state.value.signedIn || repository == null) {
            pendingDeepLink = uri
        } else {
            consumeDeepLink(uri)
        }
    }

    fun selectSection(section: TvSection) {
        _state.update {
            it.copy(
                selectedSection = section,
                searchVisible = false,
                trackCollection = null,
                selectedPodcast = null,
                podcastEpisodes = emptyList(),
                selectedAudiobook = null,
                audiobookChapters = emptyList(),
                selectedArtist = null,
                playlistCollaboration = null,
                actionPane = TvActionPane.NONE,
                actionTrack = null,
                error = null
            )
        }
    }

    fun openSearch() {
        _state.update {
            it.copy(
                searchVisible = true,
                focusSearchResults = false,
                trackCollection = null,
                selectedPodcast = null,
                podcastEpisodes = emptyList(),
                selectedAudiobook = null,
                audiobookChapters = emptyList(),
                selectedArtist = null,
                error = null
            )
        }
    }

    fun openVoiceSearch(query: String, playImmediately: Boolean = false) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        if (repository == null) {
            pendingVoiceSearch = normalized to playImmediately
        }
        if (!playImmediately) {
            _state.update {
                it.copy(
                    searchVisible = true,
                    searchQuery = normalized,
                    searchedQuery = "",
                    searchResults = null,
                    focusSearchResults = true,
                    trackCollection = null,
                    selectedArtist = null,
                    nowPlayingVisible = false,
                    error = null
                )
            }
            requestSearch(normalized, delayMs = 0L, showValidation = true)
            return
        }
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.search(normalized) }
                .onSuccess { results ->
                    val first = results.hits.firstOrNull()
                    if (first == null) {
                        _state.update { it.copy(loading = false) }
                        showNotice("No music found for “$normalized”")
                    } else {
                        playMusic(results.hits, 0)
                        _state.update { it.copy(loading = false) }
                        showNotice("Playing ${first.displayTitle}")
                    }
                }
                .onFailure(::showError)
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(searchVisible = false, focusSearchResults = false, loading = false, error = null)
        }
    }

    fun openAlbum(album: Album) {
        val repo = repository ?: return
        loadDetail {
            val tracks = repo.albumTracks(album.displayName)
            _state.update {
                it.copy(
                    loading = false,
                    trackCollection = TrackCollection(
                        album.displayName,
                        album.artistName,
                        tracks
                    )
                )
            }
        }
    }

    fun openPlaylist(playlist: TvPlaylist) {
        val repo = repository ?: return
        loadDetail {
            val tracks = repo.playlistTracks(playlist)
            val collaboration = if (playlist.kind == TvPlaylist.Kind.STANDARD) {
                runCatching { repo.playlistCollaboration(playlist.id) }.getOrNull()
            } else {
                null
            }
            val kind = when {
                playlist.kind == TvPlaylist.Kind.SMART -> "Smart playlist"
                playlist.collaborative -> "Collaborative playlist"
                !playlist.ownerEmail.isNullOrBlank() && playlist.ownerEmail != _state.value.email ->
                    "Shared by ${playlist.ownerEmail}"
                else -> "Playlist"
            }
            _state.update {
                it.copy(
                    loading = false,
                    trackCollection = TrackCollection(playlist.name, kind, tracks, playlist),
                    playlistCollaboration = collaboration
                )
            }
        }
    }

    fun openArtist(artistId: Int?, artistName: String) {
        val repo = repository ?: return
        loadDetail {
            val artist = artistId?.takeIf { it > 0 }?.let { Artist(id = it, name = artistName) }
                ?: repo.findArtist(artistName)
                ?: throw TvApiException("Artist not found")
            val (detail, tracks) = repo.artist(artist.id)
            val resolved = detail.artist ?: artist
            _state.update {
                it.copy(
                    loading = false,
                    selectedArtist = ArtistScreen(resolved, detail.albums, detail.appearsOn, tracks),
                    trackCollection = null,
                    selectedPodcast = null,
                    selectedAudiobook = null,
                    nowPlayingVisible = false,
                    actionPane = TvActionPane.NONE,
                    actionTrack = null
                )
            }
        }
    }

    fun openPodcast(podcast: Podcast) {
        val repo = repository ?: return
        loadDetail {
            val response = repo.podcast(podcast.id)
            _state.update {
                it.copy(
                    loading = false,
                    selectedPodcast = response.podcast ?: podcast,
                    podcastEpisodes = response.episodes
                )
            }
        }
    }

    fun openAudiobook(audiobook: Audiobook) {
        val repo = repository ?: return
        loadDetail {
            val response = repo.audiobook(audiobook.id)
            val selected = (response.audiobook ?: audiobook).copy(
                progress = response.progress ?: response.audiobook?.progress ?: audiobook.progress
            )
            _state.update {
                it.copy(
                    loading = false,
                    selectedAudiobook = selected,
                    audiobookChapters = response.chapters
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                focusSearchResults = false,
                searchedQuery = "",
                searchResults = if (query.trim().length < 2) null else it.searchResults,
                loading = false,
                error = null
            )
        }
        requestSearch(query.trim(), delayMs = 450L, showValidation = false)
    }

    fun search() {
        requestSearch(_state.value.searchQuery.trim(), delayMs = 0L, showValidation = true)
    }

    fun play(tracks: List<Track>, track: Track) {
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index >= 0) playMusic(tracks, index)
    }

    fun playBucket(bucket: RecommendationBucket) {
        val firstTrack = bucket.tracks.firstOrNull() ?: return
        play(bucket.tracks, firstTrack)
    }

    fun playEpisode(episodes: List<Episode>, episode: Episode, podcast: Podcast? = null) {
        val index = episodes.indexOfFirst { it.id == episode.id }
        if (index >= 0) {
            selectLocalLongFormPlayback()
            playback.playEpisodes(episodes, index, podcast)
        }
    }

    fun playChapter(audiobook: Audiobook, chapters: List<AudiobookChapter>, chapter: AudiobookChapter) {
        val index = chapters.indexOfFirst { it.id == chapter.id }
        if (index >= 0) {
            selectLocalLongFormPlayback()
            playback.playChapters(audiobook, chapters, index)
        }
    }

    private fun selectLocalLongFormPlayback() {
        sendRemoteCommand("pause")
        _state.update { it.copy(selectedConnectDeviceId = it.localConnectDeviceId) }
    }

    fun togglePlayPause() {
        if (!sendRemoteCommand("toggle")) playback.togglePlayPause()
    }

    fun playPlayback() {
        if (!sendRemoteCommand("play")) playback.play()
    }

    fun pausePlayback() {
        if (!sendRemoteCommand("pause")) playback.pause()
    }

    fun next() {
        if (!sendRemoteCommand("next")) playback.next()
    }

    fun previous() {
        if (!sendRemoteCommand("previous")) playback.previous()
    }

    fun seekBackward() = seekDisplayedBy(-10_000L)
    fun seekForward() = seekDisplayedBy(10_000L)

    fun playQueueIndex(index: Int) {
        if (!sendRemoteCommand("play_index", buildJsonObject { put("index", index) })) {
            playback.playQueueIndex(index)
        }
    }
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeatMode() = playback.cycleRepeatMode()

    fun openConnectPlayers() {
        _state.update { it.copy(actionPane = TvActionPane.CONNECT, error = null) }
    }

    fun selectConnectDevice(deviceId: String) {
        val state = _state.value
        val target = state.connectDevices.firstOrNull { it.id == deviceId } ?: return
        if (target.id != state.localConnectDeviceId && state.playback.item?.kind != PlaybackKind.MUSIC) {
            playback.pause()
        }
        val current = state.selectedConnectDevice
        val local = state.connectDevices.firstOrNull { it.id == state.localConnectDeviceId }
        val source = current?.takeIf { it.state.track != null }
            ?: local?.takeIf { it.state.track != null }
            ?: state.connectDevices.firstOrNull { it.state.isPlaying }
        if (source != null && source.id != target.id) {
            realtime.transferPlayback(source.id, target.id)
        }
        _state.update {
            it.copy(selectedConnectDeviceId = target.id, actionPane = TvActionPane.NONE)
        }
        showNotice("Controlling ${target.name}")
    }

    private fun selectedRemoteDevice(): TvConnectDevice? = _state.value.selectedConnectDevice
        ?.takeIf { it.id != _state.value.localConnectDeviceId }

    private fun sendRemoteCommand(command: String, payload: JsonObject = JsonObject(emptyMap())): Boolean {
        val target = selectedRemoteDevice() ?: return false
        return realtime.sendCommand(target.id, command, payload)
    }

    private fun seekDisplayedBy(deltaMs: Long) {
        val remote = selectedRemoteDevice()
        if (remote == null) {
            playback.seekBy(deltaMs)
            return
        }
        val duration = remote.state.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        val position = (remote.state.positionMs + deltaMs).coerceIn(0L, duration)
        sendRemoteCommand("seek", buildJsonObject { put("positionMs", position) })
    }

    private fun playMusic(tracks: List<Track>, selectedIndex: Int) {
        val playable = tracks.filter { it.id > 0 }.take(500)
        if (playable.isEmpty()) return
        val originalId = tracks.getOrNull(selectedIndex)?.id
        val safeIndex = playable.indexOfFirst { it.id == originalId }.coerceAtLeast(0)
        if (selectedRemoteDevice() != null) {
            sendRemoteCommand("play_tracks", buildJsonObject {
                put("tracks", buildJsonArray {
                    playable.forEach { track -> add(connectTrackJson(track)) }
                })
                put("queueIndex", safeIndex)
                put("positionMs", 0)
                put("isPlaying", true)
            })
        } else {
            playback.playTracks(playable, safeIndex)
        }
    }

    private fun connectTrackJson(track: Track) = buildJsonObject {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.displayArtist)
        put("album", track.album)
        put("artPath", track.artPath)
        put("durationMs", track.durationMs)
    }

    fun openTrackActions(track: Track) {
        _state.update {
            it.copy(
                actionPane = TvActionPane.TRACK,
                actionTrack = track,
                shareTargets = emptyList(),
                actionLoading = false,
                error = null
            )
        }
    }

    fun openCurrentTrackActions() {
        currentMusicTrack()?.let(::openTrackActions)
    }

    fun closeActions() {
        _state.update {
            it.copy(
                actionPane = TvActionPane.NONE,
                actionTrack = null,
                shareTargets = emptyList(),
                actionLoading = false
            )
        }
    }

    fun navigateActionBack() {
        _state.update { state ->
            when (state.actionPane) {
                TvActionPane.PLAYLISTS,
                TvActionPane.SHARE -> state.copy(actionPane = TvActionPane.TRACK, actionLoading = false, error = null)
                TvActionPane.CREATE_PLAYLIST -> state.copy(
                    actionPane = if (state.actionTrack == null) TvActionPane.NONE else TvActionPane.PLAYLISTS,
                    actionLoading = false,
                    error = null
                )
                TvActionPane.TRACK,
                TvActionPane.CONNECT,
                TvActionPane.NONE -> state.copy(
                    actionPane = TvActionPane.NONE,
                    actionTrack = null,
                    shareTargets = emptyList(),
                    actionLoading = false,
                    error = null
                )
            }
        }
    }

    fun showPlaylistTargets() {
        _state.update { it.copy(actionPane = TvActionPane.PLAYLISTS) }
    }

    fun showCreatePlaylist() {
        _state.update { it.copy(actionPane = TvActionPane.CREATE_PLAYLIST, actionLoading = false) }
    }

    fun showShareTargets() {
        val track = _state.value.actionTrack ?: return
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionPane = TvActionPane.SHARE, actionLoading = true, error = null) }
            runCatching { repo.shareTargets(track.id) }
                .onSuccess { targets ->
                    _state.update { it.copy(shareTargets = targets, actionLoading = false) }
                }
                .onFailure(::showError)
        }
    }

    fun toggleFavorite(track: Track) {
        val repo = repository ?: return
        val favorite = _state.value.favorites.any { it.id == track.id }
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, error = null) }
            runCatching { repo.setFavorite(track.id, !favorite) }
                .onSuccess {
                    val favorites = runCatching { repo.favorites() }.getOrElse { _state.value.favorites }
                    _state.update { it.copy(favorites = favorites, actionLoading = false) }
                    showNotice(if (favorite) "Removed from Favorites" else "Added to Favorites")
                }
                .onFailure(::showError)
        }
    }

    fun queueTrackNext(track: Track) {
        playback.playNext(track)
        closeActions()
        showNotice("${track.displayTitle} will play next")
    }

    fun addActionTrackToPlaylist(playlist: TvPlaylist) {
        val track = _state.value.actionTrack ?: return
        val repo = repository ?: return
        if (playlist.kind != TvPlaylist.Kind.STANDARD) return
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, error = null) }
            runCatching { repo.addTrackToPlaylist(playlist.id, track.id) }
                .onSuccess {
                    closeActions()
                    showNotice("Added to ${playlist.name}")
                    refreshContent(silent = true)
                }
                .onFailure(::showError)
        }
    }

    fun removeActionTrackFromPlaylist() {
        val track = _state.value.actionTrack ?: return
        val playlist = _state.value.trackCollection?.playlist ?: return
        val repo = repository ?: return
        if (playlist.kind != TvPlaylist.Kind.STANDARD) return
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, error = null) }
            runCatching { repo.removeTrackFromPlaylist(playlist.id, track.id) }
                .onSuccess {
                    closeActions()
                    showNotice("Removed from ${playlist.name}")
                    openPlaylist(playlist)
                }
                .onFailure(::showError)
        }
    }

    fun createPlaylist(name: String, addActionTrack: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val repo = repository ?: return
        val track = _state.value.actionTrack.takeIf { addActionTrack }
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, error = null) }
            runCatching {
                val playlist = repo.createPlaylist(trimmed)
                track?.let { repo.addTrackToPlaylist(playlist.id, it.id) }
                playlist
            }.onSuccess { playlist ->
                closeActions()
                showNotice(if (track == null) "Created ${playlist.name}" else "Created ${playlist.name} and added the song")
                refreshContent(silent = true)
            }.onFailure(::showError)
        }
    }

    fun shareActionTrack(recipient: SocialUser) {
        val track = _state.value.actionTrack ?: return
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, error = null) }
            runCatching { repo.shareTrack(track.id, recipient.id) }
                .onSuccess {
                    closeActions()
                    showNotice("Shared with ${recipient.email}")
                }
                .onFailure(::showError)
        }
    }

    fun startRadio(track: Track) {
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, error = null) }
            val exclude = _state.value.displayedPlayback.queue.mapNotNull { it.trackId }
            runCatching { repo.similarTracks(track.id, exclude) }
                .onSuccess { similar ->
                    if (similar.isEmpty()) {
                        _state.update { it.copy(actionLoading = false) }
                        showNotice("No similar songs are available")
                    } else {
                        playMusic(listOf(track) + similar.filter { it.id != track.id }, 0)
                        closeActions()
                        showNotice("Started radio with ${similar.size} recommendations")
                    }
                }
                .onFailure(::showError)
        }
    }

    fun deleteOpenPlaylist() {
        val playlist = _state.value.trackCollection?.playlist ?: return
        if (!playlist.isOwner || playlist.kind != TvPlaylist.Kind.STANDARD) return
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.deletePlaylist(playlist.id) }
                .onSuccess {
                    _state.update { it.copy(trackCollection = null, playlistCollaboration = null, loading = false) }
                    showNotice("Deleted ${playlist.name}")
                    refreshContent(silent = true)
                }
                .onFailure(::showError)
        }
    }

    fun openNowPlaying() {
        if (_state.value.displayedPlayback.item != null) {
            _state.update { it.copy(nowPlayingVisible = true) }
        }
    }

    fun closeNowPlaying() {
        _state.update { it.copy(nowPlayingVisible = false) }
    }

    fun navigateBack(): Boolean {
        val state = _state.value
        return when {
            state.actionPane != TvActionPane.NONE -> {
                closeActions()
                true
            }
            state.nowPlayingVisible -> {
                closeNowPlaying()
                true
            }
            state.searchVisible -> {
                closeSearch()
                true
            }
            state.trackCollection != null -> {
                _state.update { it.copy(trackCollection = null, playlistCollaboration = null) }
                true
            }
            state.selectedArtist != null -> {
                _state.update { it.copy(selectedArtist = null) }
                true
            }
            state.selectedPodcast != null -> {
                _state.update { it.copy(selectedPodcast = null, podcastEpisodes = emptyList()) }
                true
            }
            state.selectedAudiobook != null -> {
                _state.update { it.copy(selectedAudiobook = null, audiobookChapters = emptyList()) }
                true
            }
            else -> false
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun notifyUser(message: String) = showNotice(message)

    fun signOut() {
        searchJob?.cancel()
        googleAuthCheckJob?.cancel()
        refreshJob?.cancel()
        realtimeRefreshJob?.cancel()
        recommendationRefreshJob?.cancel()
        noticeJob?.cancel()
        searchJob = null
        googleAuthCheckJob = null
        refreshJob = null
        realtimeRefreshJob = null
        recommendationRefreshJob = null
        noticeJob = null
        realtime.stop()
        syncLongFormProgress(previousPlayback, force = true)
        playback.release()
        previousPlayback = PlaybackSnapshot()
        lastRecordedTrackId = null
        repository = null
        pendingDeepLink = null
        pendingVoiceSearch = null
        sessionStore.clear()
        _state.value = TvUiState(
            checkingSession = false,
            serverUrl = sessionStore.lastServerUrl(),
            localConnectDeviceId = sessionStore.clientId
        )
    }

    private fun handleSessionInvalidated(reason: String) {
        viewModelScope.launch {
            signOut()
            _state.update {
                it.copy(error = reason.ifBlank { "Your session expired. Sign in again." })
            }
        }
    }

    override fun onCleared() {
        syncLongFormProgress(previousPlayback, force = true)
        realtime.stop()
        playback.release()
        super.onCleared()
    }

    private fun connect(session: TvSession, verify: Boolean) {
        viewModelScope.launch { connectNow(session, verify) }
    }

    private suspend fun connectNow(session: TvSession, verify: Boolean) {
        _state.update { it.copy(checkingSession = true, loading = true, error = null) }
        runCatching {
            val repo = TvRepository(session, sessionStore.clientId)
            if (verify) repo.verifySession()
            repo to loadContent(repo)
        }.onSuccess { (repo, content) ->
            repository = repo
            playback.configure(repo)
            realtime.start(session, sessionStore.clientId)
            lastRefreshElapsed = SystemClock.elapsedRealtime()
            _state.update {
                content.applyTo(
                    it.copy(
                        checkingSession = false,
                        signedIn = true,
                        serverUrl = session.serverUrl,
                        authToken = session.token,
                        email = session.email,
                        googleSigningIn = false,
                        loading = false,
                        refreshing = false,
                        error = content.errorMessage
                    )
                )
            }
            publishTvHome()
            pendingDeepLink?.also {
                pendingDeepLink = null
                consumeDeepLink(it)
            }
            pendingVoiceSearch?.also { (query, playImmediately) ->
                pendingVoiceSearch = null
                openVoiceSearch(query, playImmediately)
            }
        }.onFailure { throwable ->
            if ((throwable as? TvApiException)?.unauthorized == true) {
                signOut()
                _state.update { it.copy(error = "Your session expired. Sign in again.") }
            } else {
                _state.update {
                    it.copy(
                        checkingSession = false,
                        loading = false,
                        googleSigningIn = false,
                        error = friendlyMessage(throwable)
                    )
                }
            }
        }
    }

    private fun refreshContent(silent: Boolean) {
        val repo = repository ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = !silent, refreshing = silent, error = null) }
            runCatching { loadContent(repo) }
                .onSuccess { content ->
                    lastRefreshElapsed = SystemClock.elapsedRealtime()
                    _state.update {
                        content.applyTo(
                            it.copy(
                                loading = false,
                                refreshing = false,
                                error = content.errorMessage
                            )
                        )
                    }
                    publishTvHome()
                }
                .onFailure(::showError)
        }
    }

    private suspend fun loadContent(repo: TvRepository): ContentUpdate = coroutineScope {
        val recommendations = async { runCatching { repo.recommendations() } }
        val recent = async { runCatching { repo.recentlyAdded() } }
        val albums = async { runCatching { repo.albums() } }
        val playlists = async { runCatching { repo.playlists() } }
        val favorites = async { runCatching { repo.favorites() } }
        val podcasts = async { runCatching { repo.podcasts() } }
        val newEpisodes = async { runCatching { repo.newEpisodes() } }
        val audiobooks = async { runCatching { repo.audiobooks() } }
        val recommendationResult = recommendations.await()
        val recentResult = recent.await()
        val albumResult = albums.await()
        val playlistResult = playlists.await()
        val favoriteResult = favorites.await()
        val podcastResult = podcasts.await()
        val newEpisodeResult = newEpisodes.await()
        val audiobookResult = audiobooks.await()
        val failures = listOfNotNull(
            recommendationResult.exceptionOrNull(),
            recentResult.exceptionOrNull(),
            albumResult.exceptionOrNull(),
            playlistResult.exceptionOrNull(),
            favoriteResult.exceptionOrNull(),
            podcastResult.exceptionOrNull(),
            newEpisodeResult.exceptionOrNull(),
            audiobookResult.exceptionOrNull()
        )
        if (failures.size == 8) throw failures.first()
        ContentUpdate(
            recommendations = recommendationResult.getOrNull(),
            recent = recentResult.getOrNull(),
            albums = albumResult.getOrNull(),
            playlists = playlistResult.getOrNull(),
            favorites = favoriteResult.getOrNull(),
            podcasts = podcastResult.getOrNull(),
            newEpisodes = newEpisodeResult.getOrNull(),
            audiobooks = audiobookResult.getOrNull(),
            errorMessage = if (failures.isNotEmpty()) {
                "Some sections could not be updated. MVBar will retry automatically."
            } else {
                null
            }
        )
    }

    private fun refreshRecommendations() {
        val repo = repository ?: return
        if (recommendationRefreshJob?.isActive == true) return
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastRecommendationsRefreshElapsed < RECOMMENDATION_REFRESH_DEBOUNCE_MS) return
        lastRecommendationsRefreshElapsed = elapsed
        recommendationRefreshJob = viewModelScope.launch {
            runCatching { repo.recommendations() }.onSuccess { buckets ->
                _state.update { it.copy(recommendations = buckets) }
                publishTvHome()
            }
        }
    }

    private fun publishTvHome() {
        val state = _state.value
        if (!state.signedIn) return
        viewModelScope.launch {
            homePublisher.publish(state.recommendations, state.recentlyAdded, state.newEpisodes)
        }
    }

    private fun consumeDeepLink(uri: Uri) {
        when (uri.host) {
            "home" -> selectSection(TvSection.FOR_YOU)
            "track" -> {
                val id = uri.pathSegments.firstOrNull()?.toIntOrNull() ?: return
                val queue = _state.value.recentlyAdded.ifEmpty {
                    _state.value.recommendations.flatMap { it.tracks }
                }
                queue.firstOrNull { it.id == id }?.let { play(queue, it) }
                    ?: showNotice("This song is no longer available")
            }
            "episode" -> {
                val id = uri.pathSegments.firstOrNull()?.toIntOrNull() ?: return
                val episodes = _state.value.newEpisodes
                episodes.firstOrNull { it.id == id }?.let { episode ->
                    val podcast = _state.value.podcasts.firstOrNull { it.id == episode.podcastId }
                    playEpisode(episodes, episode, podcast)
                } ?: showNotice("This episode is no longer available")
            }
            "bucket" -> {
                val key = uri.pathSegments.firstOrNull() ?: return
                _state.value.recommendations.firstOrNull { it.key == key }?.let(::playBucket)
                    ?: showNotice("This recommendation has changed")
            }
        }
    }

    private fun handleRealtimeEvent(event: String) {
        if (
            event.equals("connected", ignoreCase = true) &&
            SystemClock.elapsedRealtime() - lastRefreshElapsed < INITIAL_CONNECTION_REFRESH_WINDOW_MS
        ) {
            return
        }
        when (realtimeRefreshForEvent(event)) {
            TvRealtimeRefresh.ALL_CONTENT -> scheduleRealtimeRefresh(AUTO_EVENT_REFRESH_DEBOUNCE_MS) {
                refreshContent(silent = true)
            }
            TvRealtimeRefresh.RECOMMENDATIONS -> scheduleRealtimeRefresh(RECOMMENDATION_EVENT_REFRESH_DEBOUNCE_MS) {
                refreshRecommendations()
            }
            TvRealtimeRefresh.LONG_FORM -> scheduleRealtimeRefresh(AUTO_EVENT_REFRESH_DEBOUNCE_MS) {
                refreshLongFormDetails()
            }
            TvRealtimeRefresh.NONE -> Unit
        }
    }

    private fun handleConnectCommand(command: String, payload: JsonObject): Boolean {
        val snapshot = _state.value.playback
        return when (command) {
            "play" -> (snapshot.item != null).also { if (it) playback.play() }
            "pause" -> (snapshot.item != null).also { if (it) playback.pause() }
            "toggle" -> (snapshot.item != null).also { if (it) playback.togglePlayPause() }
            "next" -> snapshot.hasNext.also { if (it) playback.next() }
            "previous" -> snapshot.hasPrevious.also { if (it) playback.previous() }
            "seek" -> (snapshot.item != null).also {
                if (it) playback.seekTo(payload["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L)
            }
            "stop", "clear_queue" -> true.also { playback.clearQueue() }
            "play_index" -> {
                val index = payload["index"]?.jsonPrimitive?.intOrNull ?: 0
                (index in snapshot.queue.indices).also { if (it) playback.playQueueIndex(index) }
            }
            "remove_index" -> {
                val index = payload["index"]?.jsonPrimitive?.intOrNull ?: 0
                (index in snapshot.queue.indices).also { if (it) playback.removeQueueIndex(index) }
            }
            "reorder" -> {
                val from = payload["from"]?.jsonPrimitive?.intOrNull ?: 0
                val to = payload["to"]?.jsonPrimitive?.intOrNull ?: 0
                (from in snapshot.queue.indices && to in snapshot.queue.indices).also {
                    if (it) playback.moveQueueItem(from, to)
                }
            }
            "play_tracks", "add_tracks", "play_next" -> {
                val tracks = payload["tracks"]?.jsonArray?.mapNotNull { element ->
                    val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val id = item["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    if (id <= 0) return@mapNotNull null
                    Track(
                        id = id,
                        title = item["title"]?.jsonPrimitive?.contentOrNull,
                        artist = item["artist"]?.jsonPrimitive?.contentOrNull,
                        album = item["album"]?.jsonPrimitive?.contentOrNull,
                        artPath = item["artPath"]?.jsonPrimitive?.contentOrNull,
                        durationMs = item["durationMs"]?.jsonPrimitive?.doubleOrNull
                    )
                }.orEmpty()
                when {
                    tracks.isEmpty() -> false
                    command == "add_tracks" -> playback.appendTracks(tracks) > 0
                    command == "play_next" -> playback.playNextMany(tracks) > 0
                    else -> {
                        val index = (payload["queueIndex"]?.jsonPrimitive?.intOrNull ?: 0)
                            .coerceIn(0, tracks.lastIndex)
                        playback.playTracksIfReady(tracks, index).also { accepted ->
                            if (accepted) {
                                val position = payload["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L
                                if (position > 0) playback.seekTo(position)
                                if (payload["isPlaying"]?.jsonPrimitive?.booleanOrNull == false) playback.pause()
                            }
                        }
                    }
                }
            }
            else -> false
        }
    }

    private fun scheduleRealtimeRefresh(delayMs: Long, action: () -> Unit) {
        realtimeRefreshJob?.cancel()
        realtimeRefreshJob = viewModelScope.launch {
            delay(delayMs)
            action()
        }
    }

    private fun refreshLongFormDetails() {
        _state.value.selectedPodcast?.let(::openPodcast)
        _state.value.selectedAudiobook?.let(::openAudiobook)
    }

    private fun loadDetail(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { block() }
                .onSuccess { if (_state.value.searchVisible) closeSearch() }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    showError(error)
                }
        }
    }

    private fun requestSearch(query: String, delayMs: Long, showValidation: Boolean) {
        searchJob?.cancel()
        if (query.length < 2) {
            _state.update {
                it.copy(
                    loading = false,
                    searchResults = null,
                    searchedQuery = "",
                    error = if (showValidation) "Enter at least two characters" else null
                )
            }
            return
        }
        val repo = repository ?: return
        searchJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            _state.update { it.copy(loading = true, error = null) }
            do {
                runCatching { repo.search(query) }
                    .onSuccess { results ->
                        if (_state.value.searchQuery.trim() == query) {
                            _state.update {
                                it.copy(loading = false, searchResults = results, searchedQuery = query)
                            }
                        }
                    }
                    .onFailure { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        if (_state.value.searchQuery.trim() == query) showError(error)
                    }
                if (_state.value.searchResults?.indexing != true || _state.value.error != null) break
                delay(5000)
            } while (_state.value.searchQuery.trim() == query)
        }
    }

    private fun syncLongFormProgress(snapshot: PlaybackSnapshot, force: Boolean) {
        val item = snapshot.item ?: return
        if (item.episodeId == null && item.audiobookId == null) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && (!snapshot.isPlaying || elapsed - lastProgressSyncElapsed < PROGRESS_SYNC_INTERVAL_MS)) return
        lastProgressSyncElapsed = elapsed
        val finished = snapshot.durationMs > 0 && snapshot.positionMs >= snapshot.durationMs - 5_000L
        val repo = repository ?: return
        viewModelScope.launch {
            item.episodeId?.let {
                repo.updateEpisodeProgress(it, snapshot.positionMs, finished)
            }
            if (item.audiobookId != null && item.chapterId != null) {
                repo.updateAudiobookProgress(
                    item.audiobookId,
                    item.chapterId,
                    snapshot.positionMs,
                    finished
                )
            }
        }
    }

    private fun currentMusicTrack(): Track? {
        val item = _state.value.displayedPlayback.item?.takeIf { it.kind == PlaybackKind.MUSIC } ?: return null
        val trackId = item.trackId ?: return null
        val state = _state.value
        return sequenceOf(
            state.trackCollection?.tracks.orEmpty(),
            state.selectedArtist?.tracks.orEmpty(),
            state.recentlyAdded,
            state.favorites,
            state.recommendations.flatMap { it.tracks },
            state.searchResults?.hits.orEmpty()
        ).flatten().firstOrNull { it.id == trackId }
            ?: Track(id = trackId, title = item.title, artist = item.artist, album = item.album)
    }

    private fun showNotice(message: String) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = message) }
        noticeJob = viewModelScope.launch {
            delay(NOTICE_DURATION_MS)
            _state.update { it.copy(notice = null) }
        }
    }

    private fun showError(throwable: Throwable) {
        if ((throwable as? TvApiException)?.unauthorized == true) {
            signOut()
            _state.update { it.copy(error = "Your session expired. Sign in again.") }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    actionLoading = false,
                    checkingSession = false,
                    googleSigningIn = false,
                    error = friendlyMessage(throwable)
                )
            }
        }
    }

    private fun friendlyMessage(throwable: Throwable): String = when (throwable) {
        is TvApiException -> throwable.message ?: "MVBar could not complete that request"
        else -> "Could not reach MVBar. Check the server address and network connection."
    }

    private data class ContentUpdate(
        val recommendations: List<RecommendationBucket>?,
        val recent: List<Track>?,
        val albums: List<Album>?,
        val playlists: List<TvPlaylist>?,
        val favorites: List<Track>?,
        val podcasts: List<Podcast>?,
        val newEpisodes: List<Episode>?,
        val audiobooks: List<Audiobook>?,
        val errorMessage: String?
    ) {
        fun applyTo(state: TvUiState): TvUiState = state.copy(
            recommendations = recommendations ?: state.recommendations,
            recentlyAdded = recent ?: state.recentlyAdded,
            albums = albums ?: state.albums,
            playlists = playlists ?: state.playlists,
            favorites = favorites ?: state.favorites,
            podcasts = podcasts ?: state.podcasts,
            newEpisodes = newEpisodes ?: state.newEpisodes,
            audiobooks = audiobooks ?: state.audiobooks
        )
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 60_000L
        const val AUTO_EVENT_REFRESH_DEBOUNCE_MS = 1_500L
        const val RECOMMENDATION_EVENT_REFRESH_DEBOUNCE_MS = 2_000L
        const val RESUME_REFRESH_DEBOUNCE_MS = 15_000L
        const val INITIAL_CONNECTION_REFRESH_WINDOW_MS = 5_000L
        const val RECOMMENDATION_REFRESH_DEBOUNCE_MS = 3_000L
        const val PROGRESS_SYNC_INTERVAL_MS = 15_000L
        const val NOTICE_DURATION_MS = 3_500L
    }
}
