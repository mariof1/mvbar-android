package com.mvbar.android.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayMode { NORMAL, REPEAT_ALL, REPEAT_ONE, SHUFFLE }

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val playMode: PlayMode = PlayMode.NORMAL,
    val isFavorite: Boolean = false,
    val isAudiobookMode: Boolean = false,
    val isPodcastModeOverride: Boolean = false,
    val artworkUrl: String? = null
) {
    val isPodcastMode: Boolean get() = isPodcastModeOverride ||
            (currentTrack != null && currentTrack.id < 0 && !isAudiobookMode)
}

class PlayerManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: PlayerManager? = null
        fun getInstance(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }

    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _queue = mutableListOf<Track>()
    private var _customArtUrls: Map<Int, String> = emptyMap()
    private var _isAudiobookMode: Boolean = false

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().await()
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val ctrl = controller ?: return
                val idx = ctrl.currentMediaItemIndex
                // If _queue is stale (e.g. AA started playback), rebuild from controller
                if (_queue.isEmpty() || idx !in _queue.indices) {
                    syncQueueFromController()
                }
                val track = if (idx in _queue.indices) _queue[idx] else null
                val isPodcast = mediaItem?.mediaId?.startsWith("ep:") == true
                val isAudiobook = mediaItem?.mediaId?.startsWith("ab:") == true
                _state.value = _state.value.copy(
                    currentTrack = track,
                    queueIndex = idx,
                    duration = ctrl.duration.coerceAtLeast(0L),
                    isAudiobookMode = isAudiobook || _isAudiobookMode,
                    isPodcastModeOverride = isPodcast,
                    artworkUrl = track?.id?.let { _customArtUrls[it] }
                        ?: mediaItem?.mediaMetadata?.artworkUri?.toString()
                )
                if (_queue.isNotEmpty() && idx >= 0) {
                    AudioCacheManager.prefetchNext(_queue, idx)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(
                        duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
                    )
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                // External queue changes (e.g. AA set new items)
                if (_queue.isEmpty() && timeline.windowCount > 0) {
                    syncQueueFromController()
                }
            }
        })
        // Sync initial state if something is already playing (e.g. started from AA)
        syncFromController()
    }

    /** Rebuild _queue from the controller's current media items */
    private fun syncQueueFromController() {
        val ctrl = controller ?: return
        val count = ctrl.mediaItemCount
        if (count == 0) return
        val items = (0 until count).map { ctrl.getMediaItemAt(it) }
        _queue.clear()
        _queue.addAll(items.map { trackFromMediaItem(it) })
        _state.value = _state.value.copy(queue = _queue.toList())
    }

    /** Sync full player state from controller (used on initial connect) */
    private fun syncFromController() {
        val ctrl = controller ?: return
        if (ctrl.mediaItemCount == 0) return
        syncQueueFromController()
        val idx = ctrl.currentMediaItemIndex
        val track = if (idx in _queue.indices) _queue[idx] else null
        val mediaItem = ctrl.currentMediaItem
        val isPodcast = mediaItem?.mediaId?.startsWith("ep:") == true
        val isAudiobook = mediaItem?.mediaId?.startsWith("ab:") == true
        _state.value = _state.value.copy(
            currentTrack = track,
            queueIndex = idx,
            isPlaying = ctrl.isPlaying,
            position = ctrl.currentPosition.coerceAtLeast(0L),
            duration = ctrl.duration.coerceAtLeast(0L),
            isAudiobookMode = isAudiobook,
            isPodcastModeOverride = isPodcast,
            artworkUrl = mediaItem?.mediaMetadata?.artworkUri?.toString()
        )
        if (ctrl.isPlaying) startProgressUpdates()
    }

    /** Create a Track from a MediaItem's metadata (for externally-started playback) */
    private fun trackFromMediaItem(item: MediaItem): Track {
        val meta = item.mediaMetadata
        val mediaId = item.mediaId
        // Parse ID: "ep:123" → 0 (podcast), "ab:1:2" → 0 (audiobook), "12345" → 12345
        val trackId = when {
            mediaId.startsWith("ep:") -> 0
            mediaId.startsWith("ab:") -> 0
            else -> mediaId.toIntOrNull() ?: 0
        }
        return Track(
            id = trackId,
            title = meta.title?.toString(),
            artist = meta.artist?.toString(),
            album = meta.albumTitle?.toString(),
            artPath = null
        )
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, customStreamUrls: Map<Int, String> = emptyMap(), customArtUrls: Map<Int, String> = emptyMap()) {
        val ctrl = controller ?: run {
            DebugLog.e("Player", "Controller is null, cannot play")
            return
        }
        _queue.clear()
        _queue.addAll(tracks)
        _customArtUrls = customArtUrls
        _isAudiobookMode = customArtUrls.isNotEmpty() && tracks.any { it.id < 0 && customArtUrls.containsKey(it.id) }

        DebugLog.i("Player", "Playing ${tracks.size} tracks from index $startIndex")

        val items = tracks.map { track ->
            val isPodcast = track.id < 0
            val streamUrl = customStreamUrls[track.id]
                ?: if (isPodcast) ApiClient.episodeStreamUrl(-track.id) else ApiClient.streamUrl(track.id)
            val artUrl = customArtUrls[track.id]
                ?: if (isPodcast) {
                    ApiClient.episodeArtUrl(-track.id)
                } else {
                    track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
                }
            DebugLog.d("Player", "Track ${track.id}: stream=$streamUrl")
            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayTitle)
                        .setArtist(track.displayArtist)
                        .setAlbumTitle(track.displayAlbum)
                        .setArtworkUri(ArtworkProvider.buildUri(artUrl))
                        .build()
                )
                .build()
        }

        ctrl.setMediaItems(items, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()

        _state.value = _state.value.copy(
            queue = tracks.toList(),
            queueIndex = startIndex,
            currentTrack = tracks.getOrNull(startIndex),
            isAudiobookMode = _isAudiobookMode,
            artworkUrl = tracks.getOrNull(startIndex)?.id?.let { customArtUrls[it] }
        )
        // Prefetch is handled by onMediaItemTransition listener — no need to call here
    }

    fun addToQueue(track: Track) {
        val ctrl = controller ?: return
        _queue.add(track)
        val streamUrl = ApiClient.streamUrl(track.id)
        val artUrl = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
        val item = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.displayTitle)
                    .setArtist(track.displayArtist)
                    .setArtworkUri(ArtworkProvider.buildUri(artUrl))
                    .build()
            )
            .build()
        ctrl.addMediaItem(item)
        _state.value = _state.value.copy(queue = _queue.toList())
    }

    fun playNext(track: Track) {
        val ctrl = controller ?: return
        val insertAt = (ctrl.currentMediaItemIndex + 1).coerceAtMost(_queue.size)
        _queue.add(insertAt, track)
        val streamUrl = ApiClient.streamUrl(track.id)
        val artUrl = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
        val item = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.displayTitle)
                    .setArtist(track.displayArtist)
                    .setArtworkUri(ArtworkProvider.buildUri(artUrl))
                    .build()
            )
            .build()
        ctrl.addMediaItem(insertAt, item)
        _state.value = _state.value.copy(queue = _queue.toList())
    }

    fun removeFromQueue(index: Int) {
        val ctrl = controller ?: return
        if (index < 0 || index >= _queue.size) return
        val currentIndex = ctrl.currentMediaItemIndex
        _queue.removeAt(index)
        ctrl.removeMediaItem(index)
        if (_queue.isEmpty()) {
            _state.value = PlayerState()
        } else {
            val newIndex = ctrl.currentMediaItemIndex
            val track = if (newIndex in _queue.indices) _queue[newIndex] else null
            _state.value = _state.value.copy(
                queue = _queue.toList(),
                queueIndex = newIndex,
                currentTrack = track
            )
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val ctrl = controller ?: return
        if (from < 0 || from >= _queue.size || to < 0 || to >= _queue.size || from == to) return
        val track = _queue.removeAt(from)
        _queue.add(to, track)
        ctrl.moveMediaItem(from, to)
        val newIndex = ctrl.currentMediaItemIndex
        _state.value = _state.value.copy(
            queue = _queue.toList(),
            queueIndex = newIndex
        )
    }

    fun playQueueIndex(index: Int) {
        val ctrl = controller ?: return
        if (index < 0 || index >= _queue.size) return
        ctrl.seekTo(index, 0L)
        ctrl.play()
    }

    fun clearQueue() {
        val ctrl = controller ?: return
        _queue.clear()
        ctrl.clearMediaItems()
        _state.value = PlayerState()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() {
        if (_state.value.isPodcastMode || _state.value.isAudiobookMode) skipForward() else controller?.seekToNextMediaItem()
    }
    fun previous() {
        if (_state.value.isPodcastMode || _state.value.isAudiobookMode) skipBackward() else controller?.seekToPreviousMediaItem()
    }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    /** Skip forward 15 seconds (podcast mode) */
    fun skipForward(seconds: Int = 15) {
        controller?.let { ctrl ->
            val target = (ctrl.currentPosition + seconds * 1000L).coerceAtMost(ctrl.duration.coerceAtLeast(0L))
            ctrl.seekTo(target)
        }
    }

    /** Skip backward 15 seconds (podcast mode) */
    fun skipBackward(seconds: Int = 15) {
        controller?.let { ctrl ->
            val target = (ctrl.currentPosition - seconds * 1000L).coerceAtLeast(0L)
            ctrl.seekTo(target)
        }
    }

    fun cyclePlayMode() {
        val next = when (_state.value.playMode) {
            PlayMode.NORMAL -> PlayMode.REPEAT_ALL
            PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.NORMAL
        }
        _state.value = _state.value.copy(playMode = next)
        controller?.let { ctrl ->
            when (next) {
                PlayMode.NORMAL -> { ctrl.repeatMode = Player.REPEAT_MODE_OFF; ctrl.shuffleModeEnabled = false }
                PlayMode.REPEAT_ALL -> { ctrl.repeatMode = Player.REPEAT_MODE_ALL; ctrl.shuffleModeEnabled = false }
                PlayMode.REPEAT_ONE -> { ctrl.repeatMode = Player.REPEAT_MODE_ONE; ctrl.shuffleModeEnabled = false }
                PlayMode.SHUFFLE -> { ctrl.repeatMode = Player.REPEAT_MODE_ALL; ctrl.shuffleModeEnabled = true }
            }
        }
    }

    fun setFavorite(isFav: Boolean) {
        _state.value = _state.value.copy(isFavorite = isFav)
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (true) {
                controller?.let { ctrl ->
                    _state.value = _state.value.copy(
                        position = ctrl.currentPosition.coerceAtLeast(0L),
                        duration = ctrl.duration.coerceAtLeast(0L)
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
}
