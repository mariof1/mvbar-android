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
 * Local-on-watch playback state holder with queue support. Backed by a
 * MediaController bound to WearPlaybackService.
 */
object WearPlayerHolder {

    data class State(
        val queue: List<PlayableItem> = emptyList(),
        val index: Int = 0,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val bufferingPercent: Int = 0,
        val isFavorite: Boolean = false
    ) {
        val item: PlayableItem? get() = queue.getOrNull(index)
        val isActive: Boolean get() = item != null
        val hasPrevious: Boolean get() = index > 0
        val hasNext: Boolean get() = index < queue.size - 1
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
        cachedContext = app
        app.startService(Intent(app, WearPlaybackService::class.java))
        val token = SessionToken(app, ComponentName(app, WearPlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { syncState() }
                override fun onIsPlayingChanged(isPlaying: Boolean) { syncState() }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncState() }
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
        val newIndex = c.currentMediaItemIndex.coerceIn(0, (cur.queue.size - 1).coerceAtLeast(0))
        _state.value = cur.copy(
            index = newIndex,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: cur.durationMs,
            bufferingPercent = c.bufferedPercentage
        )
    }

    /** Play a single item — replaces queue. */
    fun play(context: Context, item: PlayableItem) {
        playQueue(context, listOf(item), 0)
    }

    /** Play a list starting at startIndex. */
    fun playQueue(context: Context, items: List<PlayableItem>, startIndex: Int) {
        if (items.isEmpty()) return
        ensureController(context)
        val store = AuthTokenStore.get(context.applicationContext)
        val base = store.serverUrl ?: return
        val mediaItems = items.mapNotNull { it.toMediaItem(base) }
        if (mediaItems.isEmpty()) return

        _state.value = State(
            queue = items,
            index = startIndex.coerceIn(0, items.size - 1),
            durationMs = items.getOrNull(startIndex)?.durationMs ?: 0,
            isFavorite = (items.getOrNull(startIndex) as? PlayableItem.Music)?.track?.isFavorite ?: false
        )

        scope.launch {
            var tries = 0
            while (controller == null && tries < 20) {
                kotlinx.coroutines.delay(50); tries++
            }
            controller?.apply {
                setMediaItems(mediaItems, startIndex, 0L)
                prepare()
                play()
            }
        }
    }

    fun addToQueue(item: PlayableItem) {
        val store = AuthTokenStore.get(controllerCtx() ?: return)
        val base = store.serverUrl ?: return
        val mi = item.toMediaItem(base) ?: return
        controller?.addMediaItem(mi)
        _state.value = _state.value.copy(queue = _state.value.queue + item)
    }

    fun playNext(item: PlayableItem) {
        val store = AuthTokenStore.get(controllerCtx() ?: return)
        val base = store.serverUrl ?: return
        val mi = item.toMediaItem(base) ?: return
        val c = controller ?: return
        val pos = c.currentMediaItemIndex + 1
        c.addMediaItem(pos, mi)
        val cur = _state.value
        val newQueue = cur.queue.toMutableList().apply { add(pos.coerceAtMost(size), item) }
        _state.value = cur.copy(queue = newQueue)
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
        val cur = _state.value
        if (index in cur.queue.indices) {
            _state.value = cur.copy(queue = cur.queue.toMutableList().also { it.removeAt(index) })
        }
    }

    fun seekToQueueIndex(index: Int) {
        controller?.seekTo(index, 0L)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0))
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    fun setFavoriteLocal(fav: Boolean) {
        _state.value = _state.value.copy(isFavorite = fav)
    }

    fun stop() {
        controller?.stop()
        _state.value = State()
    }

    private fun controllerCtx(): Context? = cachedContext

    @Volatile private var cachedContext: Context? = null

    private fun PlayableItem.toMediaItem(base: String): MediaItem? {
        val url = streamUrl(base, this) ?: return null
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId("${if (isPodcast) "ep" else "tr"}-${id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private fun streamUrl(base: String, item: PlayableItem): String? {
        val b = if (base.endsWith("/")) base else "$base/"
        return when (item) {
            is PlayableItem.Music -> "${b}api/library/tracks/${item.id}/stream"
            is PlayableItem.PodcastEp -> "${b}api/podcasts/episodes/${item.id}/stream"
        }
    }
}
