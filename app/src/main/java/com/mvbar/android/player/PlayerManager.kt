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
    val isFavorite: Boolean = false
)

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
                val idx = controller?.currentMediaItemIndex ?: -1
                val track = if (idx in _queue.indices) _queue[idx] else null
                _state.value = _state.value.copy(
                    currentTrack = track,
                    queueIndex = idx,
                    duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(
                        duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
                    )
                }
            }
        })
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val ctrl = controller ?: return
        _queue.clear()
        _queue.addAll(tracks)

        val items = tracks.map { track ->
            val streamUrl = ApiClient.streamUrl(track.id)
            val artUrl = ApiClient.trackArtUrl(track.id)
            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayTitle)
                        .setArtist(track.displayArtist)
                        .setAlbumTitle(track.displayAlbum)
                        .setArtworkUri(Uri.parse(artUrl))
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
            currentTrack = tracks.getOrNull(startIndex)
        )
    }

    fun addToQueue(track: Track) {
        val ctrl = controller ?: return
        _queue.add(track)
        val streamUrl = ApiClient.streamUrl(track.id)
        val artUrl = ApiClient.trackArtUrl(track.id)
        val item = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.displayTitle)
                    .setArtist(track.displayArtist)
                    .setArtworkUri(Uri.parse(artUrl))
                    .build()
            )
            .build()
        ctrl.addMediaItem(item)
        _state.value = _state.value.copy(queue = _queue.toList())
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

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
