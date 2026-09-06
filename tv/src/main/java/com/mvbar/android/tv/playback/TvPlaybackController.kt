package com.mvbar.android.tv.playback

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mvbar.android.tv.data.Audiobook
import com.mvbar.android.tv.data.AudiobookChapter
import com.mvbar.android.tv.data.Episode
import com.mvbar.android.tv.data.Podcast
import com.mvbar.android.tv.data.Track
import com.mvbar.android.tv.data.TvRepository
import kotlin.math.max
import kotlin.math.min

enum class PlaybackKind { MUSIC, PODCAST, AUDIOBOOK }

data class PlaybackItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val artworkUrl: String?,
    val kind: PlaybackKind,
    val durationMs: Long = 0L,
    val resumePositionMs: Long = 0L,
    val trackId: Int? = null,
    val episodeId: Int? = null,
    val audiobookId: Int? = null,
    val chapterId: Int? = null
)

data class PlaybackSnapshot(
    val item: PlaybackItem? = null,
    val queue: List<PlaybackItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class TvPlaybackController(
    context: Context,
    private val onChanged: (PlaybackSnapshot) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var repository: TvRepository? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var queue: List<PlaybackItem> = emptyList()
    private var pendingPlay: Pair<List<PlaybackItem>, Int>? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = notifyState()
    }
    private val positionTicker = object : Runnable {
        override fun run() {
            notifyState()
            if (controller != null) mainHandler.postDelayed(this, 500L)
        }
    }

    fun configure(repository: TvRepository) {
        this.repository = repository
        if (controller == null && controllerFuture == null) connect()
    }

    fun playTracks(tracks: List<Track>, selectedIndex: Int) {
        val repo = repository ?: return
        playItems(tracks.map { track -> track.toPlaybackItem(repo) }, selectedIndex)
    }

    fun playEpisodes(episodes: List<Episode>, selectedIndex: Int, podcast: Podcast?) {
        val repo = repository ?: return
        playItems(
            episodes.map { episode ->
                PlaybackItem(
                    mediaId = "episode:${episode.id}",
                    title = episode.title.ifBlank { "Untitled episode" },
                    artist = episode.podcastTitle ?: podcast?.title ?: "Podcast",
                    album = podcast?.title ?: episode.podcastTitle ?: "Podcast",
                    streamUrl = repo.episodeStreamUrl(episode.id),
                    artworkUrl = repo.episodeArtUrl(episode),
                    kind = PlaybackKind.PODCAST,
                    durationMs = episode.durationMs ?: 0L,
                    resumePositionMs = episode.positionMs.takeIf { !episode.played } ?: 0L,
                    episodeId = episode.id
                )
            },
            selectedIndex
        )
    }

    fun playChapters(audiobook: Audiobook, chapters: List<AudiobookChapter>, selectedIndex: Int) {
        val repo = repository ?: return
        val resumeChapterId = audiobook.progress?.chapterId
        playItems(
            chapters.map { chapter ->
                PlaybackItem(
                    mediaId = "audiobook:${audiobook.id}:${chapter.id}",
                    title = chapter.title.ifBlank { "Chapter ${chapter.position + 1}" },
                    artist = audiobook.author ?: audiobook.narrator ?: "Audiobook",
                    album = audiobook.title,
                    streamUrl = repo.audiobookChapterStreamUrl(audiobook.id, chapter.id),
                    artworkUrl = repo.audiobookArtUrl(audiobook.id),
                    kind = PlaybackKind.AUDIOBOOK,
                    durationMs = chapter.durationMs ?: 0L,
                    resumePositionMs = audiobook.progress?.positionMs
                        ?.takeIf { resumeChapterId == chapter.id }
                        ?: 0L,
                    audiobookId = audiobook.id,
                    chapterId = chapter.id
                )
            },
            selectedIndex
        )
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
    }

    fun seekBy(deltaMs: Long) {
        controller?.let { activeController ->
            val duration = activeController.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            val target = activeController.currentPosition + deltaMs
            activeController.seekTo(
                if (duration == null) max(0L, target) else min(duration, max(0L, target))
            )
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.let { activeController ->
            val duration = activeController.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            activeController.seekTo(
                if (duration == null) max(0L, positionMs) else min(duration, max(0L, positionMs))
            )
        }
    }

    fun playQueueIndex(index: Int) {
        controller?.takeIf { index in queue.indices }?.seekTo(index, 0L)
    }

    fun playNext(track: Track) {
        val repo = repository ?: return
        val activeController = controller ?: return
        val item = track.toPlaybackItem(repo)
        val insertAt = (activeController.currentMediaItemIndex + 1).coerceIn(0, queue.size)
        queue = queue.toMutableList().apply { add(insertAt, item) }
        activeController.addMediaItem(insertAt, item.toMediaItem())
        notifyState()
    }

    fun appendTracks(tracks: List<Track>) {
        val repo = repository ?: return
        val activeController = controller ?: return
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toPlaybackItem(repo) }
        queue = queue + items
        activeController.addMediaItems(items.map { it.toMediaItem() })
        notifyState()
    }

    fun removeQueueIndex(index: Int) {
        val activeController = controller ?: return
        if (index !in queue.indices) return
        queue = queue.toMutableList().apply { removeAt(index) }
        activeController.removeMediaItem(index)
        notifyState()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val activeController = controller ?: return
        if (from !in queue.indices || to !in queue.indices || from == to) return
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        activeController.moveMediaItem(from, to)
        notifyState()
    }

    fun clearQueue() {
        queue = emptyList()
        controller?.stop()
        controller?.clearMediaItems()
        notifyState()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeatMode() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun release() {
        pendingPlay = null
        queue = emptyList()
        repository = null
        mainHandler.removeCallbacks(positionTicker)
        controller?.let { activeController ->
            activeController.removeListener(playerListener)
            activeController.stop()
            activeController.clearMediaItems()
        }
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        onChanged(PlaybackSnapshot())
    }

    private fun playItems(items: List<PlaybackItem>, selectedIndex: Int) {
        if (items.isEmpty() || selectedIndex !in items.indices) return
        val activeController = controller
        if (activeController == null) {
            pendingPlay = items to selectedIndex
            if (controllerFuture == null) connect()
            return
        }
        playNow(activeController, items, selectedIndex)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, TvPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { connectedController ->
                        if (controllerFuture !== future) {
                            connectedController.release()
                            return@onSuccess
                        }
                        controller = connectedController
                        connectedController.addListener(playerListener)
                        controllerFuture = null
                        pendingPlay?.also { (items, selectedIndex) ->
                            pendingPlay = null
                            playNow(connectedController, items, selectedIndex)
                        }
                        mainHandler.removeCallbacks(positionTicker)
                        mainHandler.post(positionTicker)
                        notifyState()
                    }
                    .onFailure { error ->
                        if (controllerFuture === future) controllerFuture = null
                        Log.e("MvbarTvPlayback", "Could not connect to the playback service", error)
                    }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun playNow(activeController: MediaController, items: List<PlaybackItem>, selectedIndex: Int) {
        queue = items
        val mediaItems = items.map { it.toMediaItem() }
        activeController.setMediaItems(mediaItems, selectedIndex, items[selectedIndex].resumePositionMs)
        activeController.prepare()
        activeController.play()
        notifyState()
    }

    private fun notifyState() {
        val activeController = controller
        val currentItem = activeController?.currentMediaItem?.mediaId?.let { mediaId ->
            queue.firstOrNull { it.mediaId == mediaId }
        }
        val duration = activeController?.duration?.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: currentItem?.durationMs?.takeIf { it > 0 }
            ?: 0L
        onChanged(
            PlaybackSnapshot(
                item = currentItem,
                queue = queue,
                currentIndex = activeController?.currentMediaItemIndex ?: -1,
                isPlaying = activeController?.isPlaying == true,
                hasPrevious = activeController?.hasPreviousMediaItem() == true,
                hasNext = activeController?.hasNextMediaItem() == true,
                positionMs = activeController?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                durationMs = duration,
                shuffleEnabled = activeController?.shuffleModeEnabled == true,
                repeatMode = activeController?.repeatMode ?: Player.REPEAT_MODE_OFF
            )
        )
    }

    private fun Track.toPlaybackItem(repo: TvRepository): PlaybackItem = PlaybackItem(
        mediaId = "track:$id",
        title = displayTitle,
        artist = displayArtist,
        album = displayAlbum,
        streamUrl = repo.streamUrl(id),
        artworkUrl = artPath?.let(repo::artPathUrl) ?: repo.trackArtUrl(id),
        kind = PlaybackKind.MUSIC,
        durationMs = durationMs?.toLong() ?: duration?.times(1_000)?.toLong() ?: 0L,
        trackId = id
    )

    private fun PlaybackItem.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .build()
        )
        .build()
}
