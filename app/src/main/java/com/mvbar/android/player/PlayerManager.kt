package com.mvbar.android.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
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
import kotlin.random.Random

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
    val isCasting: Boolean = false,
    val artworkUrl: String? = null
) {
    val isPodcastMode: Boolean get() = isPodcastModeOverride ||
            (currentTrack != null && currentTrack.id < 0 && !isAudiobookMode)
}

class PlayerManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: PlayerManager? = null
        private val EPISODE_STREAM_REGEX = """/api/podcasts/episodes/(\d+)/stream(?:\?.*)?$""".toRegex()
        private val AUDIOBOOK_STREAM_REGEX = """/api/audiobooks/(\d+)/chapters/(\d+)/stream(?:\?.*)?$""".toRegex()
        fun getInstance(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }

    private var controller: MediaController? = null
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var castRemoteClient: RemoteMediaClient? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var currentCastContentId: String? = null
    private var handledCastFinishedContentId: String? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _queue = mutableListOf<Track>()
    private var _customArtUrls: Map<Int, String> = emptyMap()
    private var _isAudiobookMode: Boolean = false

    private val castProgressListener = RemoteMediaClient.ProgressListener { positionMs, durationMs ->
        _state.value = _state.value.copy(
            position = positionMs.coerceAtLeast(0L),
            duration = durationMs.coerceAtLeast(0L),
            isCasting = true
        )
    }

    private val castCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            syncFromCast()
        }

        override fun onMediaError(mediaError: com.google.android.gms.cast.MediaError) {
            DebugLog.e("Cast", "Cast media error: ${mediaError.reason}")
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private val castSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            DebugLog.i("Cast", "Cast session started: $sessionId")
            attachCastSession(session, transferCurrentPlayback = true)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            DebugLog.i("Cast", "Cast session resumed; wasSuspended=$wasSuspended")
            attachCastSession(session, transferCurrentPlayback = false)
            syncFromCast()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            DebugLog.i("Cast", "Cast session ended; error=$error")
            detachCastSession()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            DebugLog.w("Cast", "Cast session suspended; reason=$reason")
            detachCastSession()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            DebugLog.e("Cast", "Cast session start failed; error=$error")
            detachCastSession()
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            DebugLog.e("Cast", "Cast session resume failed; error=$error")
            detachCastSession()
        }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    }

    private fun isPodcastMediaItem(item: MediaItem?): Boolean {
        val mediaId = item?.mediaId ?: return false
        if (mediaId.startsWith("ep:")) return true
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        if (EPISODE_STREAM_REGEX.containsMatchIn(uri)) return true
        return mediaId.toIntOrNull()?.let { it < 0 } == true && !isAudiobookMediaItem(item)
    }

    private fun isAudiobookMediaItem(item: MediaItem?): Boolean {
        val mediaId = item?.mediaId ?: return false
        if (mediaId.startsWith("ab:")) return true
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        return AUDIOBOOK_STREAM_REGEX.containsMatchIn(uri)
    }

    private fun mediaIdForTrack(trackId: Int, streamUrl: String): String {
        if (trackId >= 0) return trackId.toString()

        EPISODE_STREAM_REGEX.find(streamUrl)?.groupValues?.getOrNull(1)?.let { episodeId ->
            return "ep:$episodeId"
        }

        AUDIOBOOK_STREAM_REGEX.find(streamUrl)?.let { match ->
            val bookId = match.groupValues.getOrNull(1)
            val chapterId = match.groupValues.getOrNull(2)
            if (!bookId.isNullOrBlank() && !chapterId.isNullOrBlank()) {
                return "ab:$bookId:$chapterId"
            }
        }

        return trackId.toString()
    }

    private fun contentTypeForTrack(track: Track): String {
        val ext = track.path
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        return when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun castPlayerStateName(playerState: Int): String = when (playerState) {
        MediaStatus.PLAYER_STATE_BUFFERING -> "BUFFERING"
        MediaStatus.PLAYER_STATE_IDLE -> "IDLE"
        MediaStatus.PLAYER_STATE_LOADING -> "LOADING"
        MediaStatus.PLAYER_STATE_PAUSED -> "PAUSED"
        MediaStatus.PLAYER_STATE_PLAYING -> "PLAYING"
        MediaStatus.PLAYER_STATE_UNKNOWN -> "UNKNOWN"
        else -> "UNKNOWN($playerState)"
    }

    private fun castIdleReasonName(idleReason: Int): String = when (idleReason) {
        MediaStatus.IDLE_REASON_CANCELED -> "CANCELED"
        MediaStatus.IDLE_REASON_ERROR -> "ERROR"
        MediaStatus.IDLE_REASON_FINISHED -> "FINISHED"
        MediaStatus.IDLE_REASON_INTERRUPTED -> "INTERRUPTED"
        MediaStatus.IDLE_REASON_NONE -> "NONE"
        else -> "UNKNOWN($idleReason)"
    }

    private fun artworkUrlForTrack(track: Track?, mediaItem: MediaItem? = null): String? {
        if (track == null) return mediaItem?.mediaMetadata?.artworkUri?.toString()
        _customArtUrls[track.id]?.let { return it }
        if (track.id < 0) return mediaItem?.mediaMetadata?.artworkUri?.toString()
        return track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
    }

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().await()
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_state.value.isCasting) return
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
                val isPodcast = isPodcastMediaItem(mediaItem)
                val isAudiobook = isAudiobookMediaItem(mediaItem)
                _state.value = _state.value.copy(
                    currentTrack = track,
                    queueIndex = idx,
                    duration = ctrl.duration.coerceAtLeast(0L),
                    isAudiobookMode = isAudiobook || _isAudiobookMode,
                    isPodcastModeOverride = isPodcast,
                    artworkUrl = artworkUrlForTrack(track, mediaItem)
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
        initCast()
    }

    private fun initCast() {
        if (castContext != null) return

        try {
            val context = CastContext.getSharedInstance(context)
            castContext = context
            context.sessionManager.addSessionManagerListener(castSessionListener, CastSession::class.java)
            context.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let {
                attachCastSession(it, transferCurrentPlayback = false)
            }
        } catch (e: Exception) {
            DebugLog.w("Cast", "Google Cast is not available", e)
        }
    }

    private fun attachCastSession(session: CastSession, transferCurrentPlayback: Boolean) {
        castRemoteClient?.unregisterCallback(castCallback)
        castRemoteClient?.removeProgressListener(castProgressListener)

        castSession = session
        castRemoteClient = session.remoteMediaClient?.also { remote ->
            remote.registerCallback(castCallback)
            remote.addProgressListener(castProgressListener, 500L)
        }
        _state.value = _state.value.copy(isCasting = castRemoteClient != null)
        DebugLog.i(
            "Cast",
            "Cast session attached; hasRemoteClient=${castRemoteClient != null} transfer=$transferCurrentPlayback"
        )

        if (transferCurrentPlayback) {
            transferCurrentTrackToCast()
        }
    }

    private fun detachCastSession() {
        DebugLog.i("Cast", "Cast session detached")
        castRemoteClient?.unregisterCallback(castCallback)
        castRemoteClient?.removeProgressListener(castProgressListener)
        castRemoteClient = null
        castSession = null
        currentCastContentId = null
        handledCastFinishedContentId = null
        _state.value = _state.value.copy(isCasting = false, isPlaying = controller?.isPlaying == true)
    }

    private fun syncFromCast() {
        val remote = castRemoteClient ?: return
        val status = remote.mediaStatus ?: return
        val isPlaying = status.playerState == MediaStatus.PLAYER_STATE_PLAYING ||
            status.playerState == MediaStatus.PLAYER_STATE_BUFFERING
        DebugLog.d(
            "Cast",
            "Cast status player=${castPlayerStateName(status.playerState)} idle=${castIdleReasonName(status.idleReason)} position=${status.streamPosition} duration=${status.mediaInfo?.streamDuration}"
        )
        if (status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            status.idleReason == MediaStatus.IDLE_REASON_FINISHED &&
            handleCastFinished(status)
        ) {
            return
        }
        _state.value = _state.value.copy(
            isCasting = true,
            isPlaying = isPlaying,
            position = status.streamPosition.coerceAtLeast(0L),
            duration = (status.mediaInfo?.streamDuration ?: _state.value.duration).coerceAtLeast(0L)
        )
    }

    private fun handleCastFinished(status: MediaStatus): Boolean {
        val finishedContentId = status.mediaInfo?.contentId
        val expectedContentId = currentCastContentId
        if (finishedContentId == null || expectedContentId == null) {
            return false
        }
        if (finishedContentId != expectedContentId) {
            DebugLog.d("Cast", "Ignoring stale Cast finish for content=$finishedContentId expected=$expectedContentId")
            return true
        }
        if (handledCastFinishedContentId == finishedContentId) return true

        val currentIndex = _state.value.queueIndex
        val nextIndex = nextCastQueueIndexAfterFinished(currentIndex)
        handledCastFinishedContentId = finishedContentId

        if (nextIndex == null) {
            DebugLog.i("Cast", "Cast queue finished at index $currentIndex")
            return false
        }

        DebugLog.i("Cast", "Advancing Cast queue from index $currentIndex to $nextIndex")
        playCastQueueIndex(nextIndex)
        return true
    }

    private fun nextCastQueueIndexAfterFinished(currentIndex: Int): Int? {
        if (_queue.isEmpty()) return null
        if (currentIndex !in _queue.indices) return _queue.indices.firstOrNull()

        return when (_state.value.playMode) {
            PlayMode.REPEAT_ONE -> currentIndex
            PlayMode.SHUFFLE -> {
                if (_queue.size == 1) currentIndex
                else _queue.indices.filter { it != currentIndex }.random(Random)
            }
            PlayMode.REPEAT_ALL -> {
                val next = currentIndex + 1
                if (next in _queue.indices) next else 0
            }
            PlayMode.NORMAL -> (currentIndex + 1).takeIf { it in _queue.indices }
        }
    }

    private fun transferCurrentTrackToCast() {
        val idx = _state.value.queueIndex.takeIf { it in _queue.indices } ?: return
        val positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: _state.value.position
        playCastQueueIndex(idx, positionMs)
    }

    private fun playCastQueueIndex(index: Int, positionMs: Long = 0L) {
        val remote = castRemoteClient ?: return
        val track = _queue.getOrNull(index) ?: return
        if (track.id < 0) {
            DebugLog.w("Cast", "Casting podcasts and audiobooks is not supported yet")
            return
        }

        scope.launch {
            try {
                val castUrl = ApiClient.api.getCastUrl(track.id)
                if (!castUrl.ok || castUrl.url.isBlank()) {
                    DebugLog.e("Cast", "Server did not return a cast URL for track ${track.id}")
                    return@launch
                }

                val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(CastMediaMetadata.KEY_TITLE, track.displayTitle)
                    putString(CastMediaMetadata.KEY_ARTIST, track.displayArtist)
                    putString(CastMediaMetadata.KEY_ALBUM_TITLE, track.displayAlbum)
                    castUrl.artUrl?.takeIf { it.isNotBlank() }?.let { artUrl ->
                        addImage(WebImage(Uri.parse(artUrl)))
                    }
                }
                val contentType = castUrl.contentType.ifBlank { contentTypeForTrack(track) }
                val mediaInfo = MediaInfo.Builder(castUrl.url)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(contentType)
                    .setMetadata(metadata)
                    .build()
                val request = MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(positionMs.coerceAtLeast(0L))
                    .build()

                controller?.pause()
                currentCastContentId = castUrl.url
                DebugLog.i(
                    "Cast",
                    "Loading Cast track id=${track.id} title=${track.displayTitle} contentType=$contentType hasArt=${!castUrl.artUrl.isNullOrBlank()} position=$positionMs"
                )
                remote.load(request).setResultCallback { result ->
                    val status = result.status
                    if (status.isSuccess) {
                        DebugLog.i("Cast", "Cast load accepted for track ${track.id}")
                        handledCastFinishedContentId = null
                    } else {
                        DebugLog.e(
                            "Cast",
                            "Cast load failed for track ${track.id}; status=${status.statusCode} message=${status.statusMessage}"
                        )
                        if (currentCastContentId == castUrl.url) currentCastContentId = null
                        _state.value = _state.value.copy(isPlaying = false)
                    }
                }
                _state.value = _state.value.copy(
                    currentTrack = track,
                    queueIndex = index,
                    isCasting = true,
                    isPlaying = true,
                    position = positionMs.coerceAtLeast(0L),
                    duration = track.durationMs?.toLong() ?: _state.value.duration,
                    isPodcastModeOverride = false,
                    isAudiobookMode = false,
                    artworkUrl = artworkUrlForTrack(track)
                )
            } catch (e: Exception) {
                DebugLog.e("Cast", "Failed to start Cast playback", e)
            }
        }
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
        val isPodcast = isPodcastMediaItem(mediaItem)
        val isAudiobook = isAudiobookMediaItem(mediaItem)
        _state.value = _state.value.copy(
            currentTrack = track,
            queueIndex = idx,
            isPlaying = ctrl.isPlaying,
            position = ctrl.currentPosition.coerceAtLeast(0L),
            duration = ctrl.duration.coerceAtLeast(0L),
            isAudiobookMode = isAudiobook,
            isPodcastModeOverride = isPodcast,
            artworkUrl = artworkUrlForTrack(track, mediaItem)
        )
        if (ctrl.isPlaying) startProgressUpdates()
    }

    /** Create a Track from a MediaItem's metadata (for externally-started playback) */
    private fun trackFromMediaItem(item: MediaItem): Track {
        val meta = item.mediaMetadata
        val mediaId = item.mediaId
        // Parse canonical AA IDs back into the pseudo IDs used by phone-side podcast/audiobook flows.
        val trackId = when {
            mediaId.startsWith("ep:") -> -(mediaId.removePrefix("ep:").toIntOrNull() ?: 0)
            mediaId.startsWith("ab:") -> {
                val parts = mediaId.removePrefix("ab:").split(":")
                val bookId = parts.getOrNull(0)?.toIntOrNull()
                val chapterId = parts.getOrNull(1)?.toIntOrNull()
                if (bookId != null && chapterId != null) -(bookId * 100000 + chapterId) else 0
            }
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

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        customStreamUrls: Map<Int, String> = emptyMap(),
        customArtUrls: Map<Int, String> = emptyMap(),
        customResumePositions: Map<Int, Long> = emptyMap()
    ) {
        val ctrl = controller ?: run {
            DebugLog.e("Player", "Controller is null, cannot play")
            return
        }
        if (tracks.isEmpty()) {
            DebugLog.w("Player", "playTracks called with an empty track list")
            return
        }
        val safeStartIndex = startIndex.coerceIn(tracks.indices)
        _queue.clear()
        _queue.addAll(tracks)
        _customArtUrls = customArtUrls
        _isAudiobookMode = customArtUrls.isNotEmpty() && tracks.any { it.id < 0 && customArtUrls.containsKey(it.id) }
        val isSpecialPlayback = tracks.any { it.id < 0 }

        DebugLog.i("Player", "Playing ${tracks.size} tracks from index $safeStartIndex")

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
            val extras = Bundle().apply {
                customResumePositions[track.id]?.takeIf { it > 0 }?.let { putLong("resume_position_ms", it) }
                track.durationMs?.toLong()?.takeIf { it > 0 }?.let { putLong("duration_ms", it) }
            }
            DebugLog.d("Player", "Track ${track.id}: stream=$streamUrl")
            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(mediaIdForTrack(track.id, streamUrl))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayTitle)
                        .setArtist(track.displayArtist)
                        .setAlbumTitle(track.displayAlbum)
                        .setArtworkUri(ArtworkProvider.buildUri(artUrl))
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        if (isSpecialPlayback) {
            ctrl.repeatMode = Player.REPEAT_MODE_OFF
            ctrl.shuffleModeEnabled = false
        }

        // If shuffle is on, temporarily disable it so the tapped track plays first.
        // ExoPlayer shuffle can randomize the start position; we re-enable after setMediaItems
        // so that "next" tracks are shuffled but the tapped one always plays immediately.
        val wasShuffling = ctrl.shuffleModeEnabled && !isSpecialPlayback
        if (wasShuffling) {
            ctrl.shuffleModeEnabled = false
        }

        val keepCasting = _state.value.isCasting && castRemoteClient != null && !isSpecialPlayback

        ctrl.setMediaItems(items, safeStartIndex, 0L)
        ctrl.prepare()

        if (keepCasting) {
            ctrl.pause()
        } else {
            ctrl.play()
            if (_state.value.isCasting) {
                castContext?.sessionManager?.endCurrentSession(false)
            }
        }

        if (wasShuffling) {
            ctrl.shuffleModeEnabled = true
        }

        _state.value = _state.value.copy(
            queue = tracks.toList(),
            queueIndex = safeStartIndex,
            currentTrack = tracks.getOrNull(safeStartIndex),
            playMode = if (isSpecialPlayback) PlayMode.NORMAL else _state.value.playMode,
            isAudiobookMode = _isAudiobookMode,
            isPodcastModeOverride = isSpecialPlayback && !_isAudiobookMode,
            isCasting = keepCasting || _state.value.isCasting,
            artworkUrl = artworkUrlForTrack(tracks.getOrNull(safeStartIndex))
        )
        if (keepCasting) {
            playCastQueueIndex(safeStartIndex)
        }
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

    /** Append multiple tracks to the end of the queue (e.g. similar tracks radio) */
    fun appendTracks(tracks: List<Track>) {
        val ctrl = controller ?: return
        val items = tracks.map { track ->
            val streamUrl = ApiClient.streamUrl(track.id)
            val artUrl = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
            _queue.add(track)
            MediaItem.Builder()
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
        }
        ctrl.addMediaItems(items)
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

    /** Insert multiple tracks right after the current one, preserving order. */
    fun playNextMany(tracks: List<Track>) {
        val ctrl = controller ?: return
        if (tracks.isEmpty()) return
        val baseIndex = (ctrl.currentMediaItemIndex + 1).coerceAtMost(_queue.size)
        val items = tracks.mapIndexed { offset, track ->
            _queue.add(baseIndex + offset, track)
            val streamUrl = ApiClient.streamUrl(track.id)
            val artUrl = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
            MediaItem.Builder()
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
        }
        ctrl.addMediaItems(baseIndex, items)
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
                currentTrack = track,
                artworkUrl = artworkUrlForTrack(track)
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
        if (_state.value.isCasting) {
            if (index in _queue.indices) playCastQueueIndex(index)
            return
        }
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

    fun togglePlay() {
        if (_state.value.isCasting) {
            val status = castRemoteClient?.mediaStatus
            if (status?.playerState == MediaStatus.PLAYER_STATE_PLAYING ||
                status?.playerState == MediaStatus.PLAYER_STATE_BUFFERING
            ) {
                castRemoteClient?.pause()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                castRemoteClient?.play()
                _state.value = _state.value.copy(isPlaying = true)
            }
            return
        }
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    fun next() {
        if (_state.value.isCasting) {
            val nextIndex = (_state.value.queueIndex + 1).takeIf { it in _queue.indices } ?: return
            playCastQueueIndex(nextIndex)
            return
        }
        if (_state.value.isPodcastMode || _state.value.isAudiobookMode) skipForward() else controller?.seekToNextMediaItem()
    }
    fun previous() {
        if (_state.value.isCasting) {
            val previousIndex = (_state.value.queueIndex - 1).takeIf { it in _queue.indices } ?: return
            playCastQueueIndex(previousIndex)
            return
        }
        if (_state.value.isPodcastMode || _state.value.isAudiobookMode) skipBackward() else controller?.seekToPreviousMediaItem()
    }
    fun seekTo(positionMs: Long) {
        if (_state.value.isCasting) {
            castRemoteClient?.seek(
                MediaSeekOptions.Builder()
                    .setPosition(positionMs.coerceAtLeast(0L))
                    .build()
            )
            _state.value = _state.value.copy(position = positionMs.coerceAtLeast(0L))
            return
        }
        controller?.seekTo(positionMs)
    }

    fun isCasting(): Boolean = _state.value.isCasting && castSession?.isConnected == true

    fun adjustCastVolume(direction: Int): Boolean {
        val session = castSession?.takeIf { it.isConnected } ?: return false
        return try {
            val current = session.volume.coerceIn(0.0, 1.0)
            val next = (current + direction.coerceIn(-1, 1) * 0.05).coerceIn(0.0, 1.0)
            if (direction > 0 && session.isMute) {
                session.setMute(false)
            }
            session.setVolume(next)
            DebugLog.d("Cast", "Cast volume ${"%.2f".format(current)} -> ${"%.2f".format(next)}")
            true
        } catch (e: Exception) {
            DebugLog.e("Cast", "Failed to adjust Cast volume", e)
            false
        }
    }

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
