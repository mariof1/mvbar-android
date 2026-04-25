package com.mvbar.android.wear.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.mvbar.android.wear.AuthTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Local-on-watch playback state holder. Stays alive for the process and
 * fronts a MediaController bound to WearPlaybackService.
 *
 * Responsibilities:
 *  - Build streaming URLs from items + auth token.
 *  - Expose a small StateFlow used by both the Now Playing screen and Tile.
 */
object WearPlayerHolder {

    data class State(
        val item: PlayableItem? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val bufferingPercent: Int = 0
    ) {
        val isActive: Boolean get() = item != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var controller: MediaController? = null
    @Volatile private var pollJob: kotlinx.coroutines.Job? = null

    @Synchronized
    fun ensureController(context: Context) {
        if (controller != null) return
        val app = context.applicationContext
        // Make sure the service is started so the MediaSession exists.
        app.startService(Intent(app, WearPlaybackService::class.java))
        val token = SessionToken(app, ComponentName(app, WearPlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { syncState() }
                override fun onIsPlayingChanged(isPlaying: Boolean) { syncState() }
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { syncState() }
            })
            startPolling()
            syncState()
        }, MoreExecutors.directExecutor())
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                syncState()
            }
        }
    }

    private fun syncState() {
        val c = controller ?: return
        val cur = _state.value
        _state.value = cur.copy(
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: cur.durationMs,
            bufferingPercent = c.bufferedPercentage
        )
    }

    fun play(context: Context, item: PlayableItem) {
        ensureController(context)
        val store = AuthTokenStore.get(context.applicationContext)
        val base = store.serverUrl ?: return
        val url = streamUrl(base, item) ?: return

        val media = MediaItem.Builder()
            .setUri(url)
            .setMediaId("${if (item.isPodcast) "ep" else "tr"}-${item.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.subtitle)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

        _state.value = State(item = item, durationMs = item.durationMs ?: 0)
        // Wait for controller; if it's not ready yet, retry briefly.
        scope.launch {
            var tries = 0
            while (controller == null && tries < 20) {
                kotlinx.coroutines.delay(50)
                tries++
            }
            controller?.apply {
                setMediaItem(media)
                prepare()
                play()
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0))
    }

    fun stop() {
        controller?.stop()
        _state.value = State()
    }

    private fun streamUrl(base: String, item: PlayableItem): String? {
        val b = if (base.endsWith("/")) base else "$base/"
        return when (item) {
            is PlayableItem.Music -> "${b}api/library/tracks/${item.id}/stream"
            is PlayableItem.PodcastEp -> "${b}api/podcasts/episodes/${item.id}/stream"
        }
    }
}
