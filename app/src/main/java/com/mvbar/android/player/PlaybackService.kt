package com.mvbar.android.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mvbar.android.MainActivity
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.ActivityQueue
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.toModel
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.repository.AuthRepository
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db by lazy { MvbarDatabase.getInstance(this) }
    private var reconnectListener: (() -> Unit)? = null
    /** Cache of track lists by parentId so tapping a track queues all siblings */
    private val browsedTrackCache = mutableMapOf<String, List<MediaItem>>()
    /** Track the previous media item for skip detection */
    private var previousTrackId: Int? = null
    private var previousTrackDurationMs: Long = 0L
    /** Pending resume position for podcast/audiobook episodes */
    private var pendingResumePositionMs: Long = 0L
    /** Job for periodic episode progress saving */
    private var progressSaveJob: kotlinx.coroutines.Job? = null
    /** Flag: re-enable shuffle after the tapped track starts playing */
    private var pendingShuffleRestore = false

    companion object {
        private const val ROOT_ID = "[root]"
        private const val FOR_YOU_ID = "[foryou]"
        private const val RECENT_ID = "[recent]"
        private const val ALBUMS_ID = "[albums]"
        private const val ARTISTS_ID = "[artists]"
        private const val PLAYLISTS_ID = "[playlists]"
        private const val GENRES_ID = "[genres]"
        private const val FAVORITES_ID = "[favorites]"
        private const val LANGUAGES_ID = "[languages]"
        private const val PODCASTS_ID = "[podcasts]"
        private const val AUDIOBOOKS_ID = "[audiobooks]"
        private const val COUNTRIES_ID = "[countries]"
        private const val SUGGESTED_ROOT_ID = "[suggested]"
        private const val RECENT_ROOT_ID = "[recent_root]"

        private fun categoryIdToConstant(key: String): String = when (key) {
            "foryou" -> FOR_YOU_ID
            "recent" -> RECENT_ID
            "favorites" -> FAVORITES_ID
            "albums" -> ALBUMS_ID
            "artists" -> ARTISTS_ID
            "playlists" -> PLAYLISTS_ID
            "genres" -> GENRES_ID
            "languages" -> LANGUAGES_ID
            "podcasts" -> PODCASTS_ID
            "audiobooks" -> AUDIOBOOKS_ID
            "countries" -> COUNTRIES_ID
            else -> key
        }
    }

    override fun onCreate() {
        super.onCreate()

        AudioCacheManager.init(this)

        // Ensure API is configured (critical for Android Auto which may start service without Activity)
        if (ApiClient.getBaseUrl() == "http://localhost/") {
            DebugLog.i("Player", "No API configured — restoring session synchronously")
            kotlinx.coroutines.runBlocking {
                try {
                    AuthRepository(this@PlaybackService).restoreSession()
                    DebugLog.i("Player", "Restored API session in PlaybackService (baseUrl=${ApiClient.getBaseUrl()}, hasToken=${ApiClient.getToken() != null})")
                } catch (e: Exception) {
                    DebugLog.e("Player", "Failed to restore session in PlaybackService", e)
                }
            }
        } else {
            DebugLog.i("Player", "API already configured (baseUrl=${ApiClient.getBaseUrl()}, hasToken=${ApiClient.getToken() != null})")
        }

        val okClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                ApiClient.getToken()?.let {
                    builder.addHeader("Authorization", "Bearer $it")
                }
                chain.proceed(builder.build())
            }
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okClient)
        val dataSourceFactory = AudioCacheManager.createCacheDataSourceFactory(upstreamFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(15_000)
            .setSeekBackIncrementMs(15_000)
            .build()

        player.pauseAtEndOfMediaItems = false

        // Restore saved shuffle/repeat state
        kotlinx.coroutines.runBlocking {
            try {
                player.shuffleModeEnabled = AaPreferences.getShuffleEnabled(this@PlaybackService)
                player.repeatMode = AaPreferences.getRepeatMode(this@PlaybackService)
            } catch (_: Exception) { }
        }

        player.addListener(object : Player.Listener {
            private var consecutiveErrors = 0
            private var retryJob: Job? = null

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DebugLog.e("Player", "Playback error: ${error.errorCodeName}", error)

                val errorCode = error.errorCode
                val isNetworkError = errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED

                if (!isNetworkError) return

                consecutiveErrors++

                // If all tracks have been tried, pause instead of looping
                if (consecutiveErrors > player.mediaItemCount) {
                    DebugLog.w("Player", "All tracks failed, pausing playback")
                    consecutiveErrors = 0
                    player.pause()
                    player.prepare()
                    return
                }

                // Retry current track with exponential backoff (up to 2 retries)
                val retryCount = consecutiveErrors
                if (retryCount <= 2) {
                    val delayMs = retryCount * 2000L  // 2s, 4s
                    DebugLog.i("Player", "Retry $retryCount in ${delayMs}ms")
                    retryJob?.cancel()
                    retryJob = serviceScope.launch {
                        delay(delayMs)
                        withContext(Dispatchers.Main) {
                            player.prepare()
                        }
                    }
                } else {
                    // After 2 retries, skip to next track
                    DebugLog.i("Player", "Skipping after $retryCount errors, moving to next")
                    if (player.mediaItemCount > 1) {
                        player.seekToNextMediaItem()
                        player.prepare()
                    } else {
                        player.pause()
                        player.prepare()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                consecutiveErrors = 0
                retryJob?.cancel()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    consecutiveErrors = 0
                    retryJob?.cancel()
                }
            }
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val libraryCallback = LibraryCallback()

        mediaSession = MediaLibrarySession.Builder(this, player, libraryCallback)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(AuthBitmapLoader())
            .build()

        // Switch custom layout (shuffle/repeat/love vs ±15s) on track change
        // and record play/skip activity via offline-resilient queue
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val session = mediaSession ?: return
                val p = session.player

                // --- Save progress for the episode we're leaving ---
                progressSaveJob?.cancel()
                saveEpisodeProgress(p)

                // --- Activity tracking via ActivityQueue ---
                // Record skip for the previous track if user pressed next/prev
                val prevId = previousTrackId
                if (prevId != null && prevId > 0 &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    val posMs = p.currentPosition
                    val durMs = previousTrackDurationMs
                    val pct = if (durMs > 0) (posMs.toDouble() / durMs * 100).toInt() else 0
                    ActivityQueue.enqueue(
                        ActivityQueue.ACTION_SKIP, prevId,
                        """{"pct":$pct}"""
                    )
                }

                // Record play for the new track (covers both phone and AA playback)
                val newTrackId = item?.mediaId?.toIntOrNull()
                if (newTrackId != null && newTrackId > 0) {
                    ActivityQueue.enqueue(ActivityQueue.ACTION_PLAY, newTrackId)
                }

                // Update previous track reference
                previousTrackId = newTrackId
                previousTrackDurationMs = item?.mediaMetadata?.extras?.getLong("duration_ms", 0L)
                    ?: p.duration.takeIf { it > 0 } ?: 0L

                // --- Episode resume: set pending seek position ---
                val resumeMs = item?.mediaMetadata?.extras?.getLong("resume_position_ms", 0L) ?: 0L
                pendingResumePositionMs = if (resumeMs > 0L) resumeMs else 0L
                if (resumeMs > 0L) {
                    DebugLog.i("Player", "Will resume ${item?.mediaId} at ${resumeMs}ms")
                }

                // --- Start periodic progress saving for episodes ---
                val mediaId = item?.mediaId
                if (mediaId != null && (mediaId.startsWith("ep:") || mediaId.startsWith("ab:"))) {
                    startProgressSaving(p)
                }

                // Check favorite status for the new track
                if (newTrackId != null && newTrackId > 0) {
                    serviceScope.launch {
                        libraryCallback.currentTrackFavorite = try {
                            db.favoriteDao().getFavorites().any { it.id == newTrackId }
                        } catch (_: Exception) { false }
                        libraryCallback.updateCustomLayout(session)
                    }
                } else {
                    libraryCallback.currentTrackFavorite = false
                    libraryCallback.updateCustomLayout(session)
                }

                // Persist queue state for AA reconnect resume
                savePlaybackSnapshot(session.player)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && pendingResumePositionMs > 0L) {
                    val pos = pendingResumePositionMs
                    pendingResumePositionMs = 0L
                    val p = mediaSession?.player ?: return
                    DebugLog.i("Player", "Resuming at ${pos}ms (${p.currentMediaItem?.mediaId})")
                    p.seekTo(pos)
                }
                // Re-enable shuffle after the tapped track has started playing
                if (state == Player.STATE_READY && pendingShuffleRestore) {
                    pendingShuffleRestore = false
                    val p = mediaSession?.player ?: return
                    p.shuffleModeEnabled = true
                    DebugLog.i("Player", "Shuffle restored after track started")
                    // Update the AA shuffle button icon
                    mediaSession?.let { session ->
                        libraryCallback.updateCustomLayout(session)
                    }
                }
            }
        })

        // Watch for category order changes and refresh AA browse tree
        serviceScope.launch {
            AaPreferences.categoryOrderFlow(this@PlaybackService)
                .collect {
                    mediaSession?.let { session ->
                        session.connectedControllers.forEach { ctrl ->
                            session.notifyChildrenChanged(ctrl, ROOT_ID, 0, null)
                        }
                    }
                }
        }

        // Monitor network: refresh browse tree root when connectivity is restored
        NetworkMonitor.init(this)
        ActivityQueue.init(this)
        var reconnectJob: kotlinx.coroutines.Job? = null
        val listener: () -> Unit = {
            // Debounce: only refresh once if network flickers
            reconnectJob?.cancel()
            reconnectJob = serviceScope.launch {
                kotlinx.coroutines.delay(2000)
                DebugLog.i("Player", "Network restored — refreshing Android Auto root")
                mediaSession?.let { session ->
                    session.connectedControllers.forEach { ctrl ->
                        session.notifyChildrenChanged(ctrl, ROOT_ID, 0, null)
                    }
                }
            }
        }
        reconnectListener = listener
        NetworkMonitor.addReconnectListener(listener)

        DebugLog.i("Player", "PlaybackService created with Android Auto support")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the service alive so AA can reconnect and resume playback.
        // Only stop if nothing is playing (no media loaded).
        val player = mediaSession?.player
        if (player == null || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Save playback state and episode progress before shutting down
        progressSaveJob?.cancel()
        mediaSession?.player?.let { player ->
            saveEpisodeProgress(player)
            // Save queue snapshot synchronously so it's available on next launch
            kotlinx.coroutines.runBlocking {
                try {
                    val entries = (0 until player.mediaItemCount).map { i ->
                        val item = player.getMediaItemAt(i)
                        val meta = item.mediaMetadata
                        AaPreferences.QueueEntry(
                            mediaId = item.mediaId,
                            title = meta.title?.toString(),
                            artist = meta.artist?.toString(),
                            album = meta.albumTitle?.toString(),
                            artUri = meta.artworkUri?.toString()
                        )
                    }
                    if (entries.isNotEmpty()) {
                        AaPreferences.savePlaybackState(
                            this@PlaybackService, entries,
                            player.currentMediaItemIndex,
                            player.currentPosition.coerceAtLeast(0L)
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        reconnectListener?.let { NetworkMonitor.removeReconnectListener(it) }
        reconnectListener = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        private val SEEK_BACK_15 = SessionCommand("SEEK_BACK_15", Bundle.EMPTY)
        private val SEEK_FORWARD_15 = SessionCommand("SEEK_FORWARD_15", Bundle.EMPTY)
        private val TOGGLE_SHUFFLE = SessionCommand("TOGGLE_SHUFFLE", Bundle.EMPTY)
        private val TOGGLE_REPEAT = SessionCommand("TOGGLE_REPEAT", Bundle.EMPTY)
        private val TOGGLE_FAVORITE = SessionCommand("TOGGLE_FAVORITE", Bundle.EMPTY)

        var currentTrackFavorite = false

        private fun isPodcastOrAudiobook(item: MediaItem?): Boolean {
            val id = item?.mediaId ?: return false
            return id.startsWith("ep:") || id.startsWith("ab:")
        }

        private fun buildPodcastLayout(): List<CommandButton> = listOf(
            CommandButton.Builder()
                .setSessionCommand(SEEK_BACK_15)
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_back_15)
                .setDisplayName("Back 15s")
                .build(),
            CommandButton.Builder()
                .setSessionCommand(SEEK_FORWARD_15)
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_forward_15)
                .setDisplayName("Forward 15s")
                .build()
        )

        private fun buildMusicLayout(player: Player): List<CommandButton> {
            val shuffleIcon = if (player.shuffleModeEnabled)
                androidx.media3.session.R.drawable.media3_icon_shuffle_on
            else
                androidx.media3.session.R.drawable.media3_icon_shuffle_off
            val repeatIcon = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> androidx.media3.session.R.drawable.media3_icon_repeat_one
                Player.REPEAT_MODE_ALL -> androidx.media3.session.R.drawable.media3_icon_repeat_all
                else -> androidx.media3.session.R.drawable.media3_icon_repeat_off
            }
            val favoriteIcon = if (currentTrackFavorite)
                androidx.media3.session.R.drawable.media3_icon_heart_filled
            else
                androidx.media3.session.R.drawable.media3_icon_heart_unfilled
            return listOf(
                CommandButton.Builder()
                    .setSessionCommand(TOGGLE_FAVORITE)
                    .setIconResId(favoriteIcon)
                    .setDisplayName("Love")
                    .build(),
                CommandButton.Builder()
                    .setSessionCommand(TOGGLE_SHUFFLE)
                    .setIconResId(shuffleIcon)
                    .setDisplayName("Shuffle")
                    .build(),
                CommandButton.Builder()
                    .setSessionCommand(TOGGLE_REPEAT)
                    .setIconResId(repeatIcon)
                    .setDisplayName("Repeat")
                    .build()
            )
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            DebugLog.i("Auto", "onConnect: package=${controller.packageName} uid=${controller.controllerVersion}")

            // If the player has no queue (service restarted or app was killed),
            // restore last playback state so the user can just press play
            if (session.player.mediaItemCount == 0) {
                restorePlaybackState()
            }

            val playerCommands = Player.Commands.Builder()
                .addAllCommands()
                .build()
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SEEK_BACK_15)
                .add(SEEK_FORWARD_15)
                .add(TOGGLE_SHUFFLE)
                .add(TOGGLE_REPEAT)
                .add(TOGGLE_FAVORITE)
                .build()

            val layout = if (isPodcastOrAudiobook(session.player.currentMediaItem))
                buildPodcastLayout() else buildMusicLayout(session.player)

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(layout)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "SEEK_BACK_15" -> session.player.seekBack()
                "SEEK_FORWARD_15" -> session.player.seekForward()
                "TOGGLE_SHUFFLE" -> {
                    session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                    serviceScope.launch {
                        AaPreferences.saveShuffleEnabled(this@PlaybackService, session.player.shuffleModeEnabled)
                    }
                    updateCustomLayout(session)
                }
                "TOGGLE_REPEAT" -> {
                    session.player.repeatMode = when (session.player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    serviceScope.launch {
                        AaPreferences.saveRepeatMode(this@PlaybackService, session.player.repeatMode)
                    }
                    updateCustomLayout(session)
                }
                "TOGGLE_FAVORITE" -> {
                    val trackId = session.player.currentMediaItem?.mediaId?.toIntOrNull()
                    if (trackId != null && trackId > 0) {
                        val action = if (currentTrackFavorite)
                            ActivityQueue.ACTION_REMOVE_FAVORITE
                        else
                            ActivityQueue.ACTION_ADD_FAVORITE
                        ActivityQueue.enqueue(action, trackId)
                        currentTrackFavorite = !currentTrackFavorite
                        updateCustomLayout(session)
                    }
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        fun updateCustomLayout(session: MediaSession) {
            val layout = if (isPodcastOrAudiobook(session.player.currentMediaItem))
                buildPodcastLayout() else buildMusicLayout(session.player)
            session.setCustomLayout(layout)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extrasKeys = params?.extras?.keySet()?.joinToString() ?: "none"
            DebugLog.i("Auto", "onGetLibraryRoot from ${browser.packageName} isRecent=${params?.isRecent} isSuggested=${params?.isSuggested} extras=[$extrasKeys]")

            val rootId = when {
                params?.isSuggested == true -> SUGGESTED_ROOT_ID
                params?.isRecent == true -> RECENT_ROOT_ID
                else -> ROOT_ID
            }
            val root = MediaItem.Builder()
                .setMediaId(rootId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setTitle("mvbar")
                        .build()
                )
                .build()
            // Return content style hints so AA knows how to display children
            val resultExtras = Bundle().apply {
                putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
            }
            val resultParams = LibraryParams.Builder()
                .setRecent(params?.isRecent == true)
                .setSuggested(params?.isSuggested == true)
                .setExtras(resultExtras)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, resultParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val items = when {
                        parentId == ROOT_ID -> getRootChildren()
                        parentId == SUGGESTED_ROOT_ID -> getForYouBuckets()
                        parentId == RECENT_ROOT_ID -> getRecentTracks()
                        parentId == FOR_YOU_ID -> getForYouBuckets()
                        parentId == RECENT_ID -> withShuffle(RECENT_ID, getRecentTracks())
                        parentId == FAVORITES_ID -> withShuffle(FAVORITES_ID, getFavoriteTracks())
                        parentId == ALBUMS_ID -> getAlbumsList()
                        parentId == ARTISTS_ID -> getArtistsList()
                        parentId == PLAYLISTS_ID -> getPlaylistsList()
                        parentId == GENRES_ID -> getGenresList()
                        parentId == LANGUAGES_ID -> getLanguagesList()
                        parentId == PODCASTS_ID -> getPodcastsList()
                        parentId == AUDIOBOOKS_ID -> getAudiobooksList()
                        parentId == COUNTRIES_ID -> getCountriesList()
                        parentId.startsWith("album:") -> withShuffle(parentId, getAlbumTracks(parentId.removePrefix("album:")))
                        parentId.startsWith("artist:") -> withShuffle(parentId, getArtistTracks(parentId.removePrefix("artist:").toInt()))
                        parentId.startsWith("playlist:") -> withShuffle(parentId, getPlaylistTracks(parentId.removePrefix("playlist:").toInt()))
                        parentId.startsWith("smartpl:") -> withShuffle(parentId, getSmartPlaylistTracks(parentId.removePrefix("smartpl:").toInt()))
                        parentId.startsWith("genre:") -> withShuffle(parentId, getGenreTracks(parentId.removePrefix("genre:")))
                        parentId.startsWith("language:") -> withShuffle(parentId, getLanguageTracks(parentId.removePrefix("language:")))
                        parentId.startsWith("country:") -> withShuffle(parentId, getCountryTracks(parentId.removePrefix("country:")))
                        parentId.startsWith("podcast:") -> {
                            val eps = getPodcastEpisodes(parentId.removePrefix("podcast:").toInt())
                            browsedTrackCache[parentId] = eps
                            eps
                        }
                        parentId.startsWith("audiobook:") -> {
                            val chs = getAudiobookChapters(parentId.removePrefix("audiobook:").toInt())
                            browsedTrackCache[parentId] = chs
                            chs
                        }
                        else -> emptyList()
                    }
                    DebugLog.i("Auto", "onGetChildren: parentId=$parentId → ${items.size} items")
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    DebugLog.e("Auto", "Browse error for $parentId", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Determine if this is a browsable folder or a playable track
            val isBrowsable = mediaId.startsWith("album:") || mediaId.startsWith("artist:") ||
                    mediaId.startsWith("playlist:") || mediaId.startsWith("smartpl:") ||
                    mediaId.startsWith("genre:") || mediaId.startsWith("language:") ||
                    mediaId.startsWith("country:") ||
                    mediaId.startsWith("podcast:") || mediaId.startsWith("audiobook:") ||
                    mediaId in listOf(ROOT_ID, SUGGESTED_ROOT_ID, RECENT_ROOT_ID, FOR_YOU_ID, RECENT_ID, FAVORITES_ID, ALBUMS_ID, ARTISTS_ID, PLAYLISTS_ID, GENRES_ID, LANGUAGES_ID, COUNTRIES_ID, PODCASTS_ID, AUDIOBOOKS_ID)
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(isBrowsable)
                        .setIsPlayable(!isBrowsable)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Handle shuffle: load all tracks for the category, shuffle, and play
            val first = mediaItems.firstOrNull()
            if (first != null && first.mediaId.startsWith("shuffle:")) {
                val parentId = first.mediaId.removePrefix("shuffle:")
                return serviceScope.future {
                    val tracks = loadTracksForParent(parentId).shuffled()
                    DebugLog.i("Auto", "Shuffle play: $parentId → ${tracks.size} tracks")
                    tracks.map { resolveStreamUri(it) }.toMutableList()
                }
            }
            // Handle bucket tap: load all tracks, shuffle, and play
            if (first != null && first.mediaId.startsWith("bucket:")) {
                return serviceScope.future {
                    val tracks = getBucketTracks(first.mediaId.removePrefix("bucket:")).shuffled()
                    DebugLog.i("Auto", "Bucket play: ${first.mediaId} → ${tracks.size} tracks")
                    tracks.map { resolveStreamUri(it) }.toMutableList()
                }
            }
            // Single track tap: queue all tracks from the same list, starting from tapped
            if (mediaItems.size == 1 && first != null) {
                val tappedId = first.mediaId
                val isPodcastOrBook = tappedId.startsWith("ep:") || tappedId.startsWith("ab:")

                // Search result tap: play just the tapped track + fetch similar from server
                val isFromSearch = browsedTrackCache.entries
                    .any { it.key.startsWith("search:") && it.value.any { t -> t.mediaId == tappedId } }
                if (isFromSearch && !isPodcastOrBook) {
                    return serviceScope.future {
                        mediaSession.player.shuffleModeEnabled = false
                        val tapped = resolveStreamUri(first)
                        val queue = mutableListOf(tapped)
                        // Fetch similar tracks from server (Last.fm auto-continue)
                        val trackId = tappedId.toIntOrNull()
                        if (trackId != null) {
                            try {
                                val resp = ApiClient.api.getSimilarTracks(trackId)
                                if (resp.tracks.isNotEmpty()) {
                                    val similar = resp.tracks.map { resolveStreamUri(trackToMediaItem(it)) }
                                    queue.addAll(similar)
                                    DebugLog.i("Auto", "Search play: track $trackId + ${similar.size} similar tracks")
                                } else {
                                    DebugLog.i("Auto", "Search play: track $trackId only (no similar: ${resp.message ?: "none available"})")
                                }
                            } catch (e: Exception) {
                                DebugLog.w("Auto", "Similar tracks fetch failed", e)
                            }
                        }
                        queue
                    }
                }

                // Browse list tap: queue all tracks from the same list, starting from tapped
                val otherEntries = browsedTrackCache.entries.filter { !it.key.startsWith("search:") }
                for ((key, tracks) in otherEntries) {
                    val idx = tracks.indexOfFirst { it.mediaId == tappedId }
                    if (idx >= 0) {
                        val reordered = if (isPodcastOrBook) {
                            tracks.subList(idx, tracks.size)
                        } else {
                            tracks.subList(idx, tracks.size) + tracks.subList(0, idx)
                        }
                        DebugLog.i("Auto", "Queue all: tapped=$tappedId from $key, ${tracks.size} tracks idx=$idx")
                        if (isPodcastOrBook) {
                            mediaSession.player.shuffleModeEnabled = false
                        } else if (mediaSession.player.shuffleModeEnabled) {
                            // Temporarily disable shuffle so the tapped track plays first;
                            // shuffle is re-enabled in onPlaybackStateChanged(STATE_READY)
                            mediaSession.player.shuffleModeEnabled = false
                            pendingShuffleRestore = true
                        }
                        val resolved = reordered.map { resolveStreamUri(it) }.toMutableList()
                        return Futures.immediateFuture(resolved)
                    }
                }
            }
            // Fallback: resolve URIs as-is
            val resolved = mediaItems.map { resolveStreamUri(it) }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.future {
                try {
                    val api = ApiClient.api
                    val results = api.search(query, limit = 20)
                    val items = results.hits.map { trackToMediaItem(it) }
                    session.notifySearchResultChanged(browser, query, items.size, params)
                } catch (e: Exception) {
                    DebugLog.e("Auto", "Search error", e)
                }
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val api = ApiClient.api
                    val results = api.search(query, limit = pageSize.coerceAtMost(100))
                    val items = results.hits.map { trackToMediaItem(it) }
                    // Clear previous search caches to avoid stale matches
                    browsedTrackCache.keys.removeAll { it.startsWith("search:") }
                    browsedTrackCache["search:$query"] = items
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    DebugLog.e("Auto", "Search results error", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }
    }

    // Browse tree builders

    private suspend fun getRootChildren(): List<MediaItem> {
        val forYouExtras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2) // grid
        }
        val listExtras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1) // list
        }
        val gridExtras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // grid
        }
        val order = AaPreferences.getCategoryOrder(this@PlaybackService)
        return order.map { key ->
            val id = categoryIdToConstant(key)
            when (id) {
                FOR_YOU_ID -> MediaItem.Builder()
                    .setMediaId(FOR_YOU_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("For You")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .setExtras(forYouExtras)
                            .build()
                    )
                    .build()
                RECENT_ID -> browseFolderItem(RECENT_ID, "Recently Added", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, listExtras)
                FAVORITES_ID -> browseFolderItem(FAVORITES_ID, "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, listExtras)
                ALBUMS_ID -> browseFolderItem(ALBUMS_ID, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                ARTISTS_ID -> browseFolderItem(ARTISTS_ID, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                PLAYLISTS_ID -> browseFolderItem(PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, listExtras)
                GENRES_ID -> browseFolderItem(GENRES_ID, "Genres", MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)
                LANGUAGES_ID -> browseFolderItem(LANGUAGES_ID, "Languages", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                PODCASTS_ID -> browseFolderItem(PODCASTS_ID, "Podcasts", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                AUDIOBOOKS_ID -> browseFolderItem(AUDIOBOOKS_ID, "Audiobooks", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                COUNTRIES_ID -> browseFolderItem(COUNTRIES_ID, "Countries", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, gridExtras)
                else -> browseFolderItem(id, AaPreferences.displayName(key), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            }
        }
    }

    /** Try API call if online, otherwise skip straight to DB fallback (avoids 30s timeout) */
    /**
     * Cache-first data loading: returns local cache instantly if available,
     * otherwise tries the API with a short timeout. On poor/no connection
     * the user sees cached data immediately instead of waiting.
     */
    private suspend fun <T : List<*>> apiOrCache(
        tag: String,
        apiCall: suspend () -> T,
        cacheCall: suspend () -> T
    ): T {
        val cached = try { cacheCall() } catch (_: Exception) { null }
        // If cache has data, return it immediately.
        // Fire a background API refresh so next load picks up fresh data.
        if (cached != null && cached.isNotEmpty()) {
            DebugLog.d("Auto", "$tag: serving ${cached.size} items from cache")
            return cached
        }
        // No cache — must try API (with a short timeout so poor connection doesn't block)
        return try {
            kotlinx.coroutines.withTimeout(5_000) { apiCall() }
        } catch (e: Exception) {
            DebugLog.w("Auto", "$tag: API failed (${e.message}), returning ${cached?.size ?: 0} cached items")
            @Suppress("UNCHECKED_CAST")
            cached ?: emptyList<Nothing>() as T
        }
    }

    /**
     * Same cache-first strategy for methods that don't return lists but need
     * to try cache → API fallback with timeout.
     */
    private suspend fun <T> apiWithTimeout(
        tag: String,
        apiCall: suspend () -> T,
        fallback: suspend () -> T
    ): T {
        return try {
            kotlinx.coroutines.withTimeout(5_000) { apiCall() }
        } catch (e: Exception) {
            DebugLog.w("Auto", "$tag: API timed out or failed (${e.message}), using fallback")
            fallback()
        }
    }

    private suspend fun getRecentTracks(): List<MediaItem> {
        return apiOrCache("Recent tracks",
            apiCall = {
                val response = ApiClient.api.getRecentlyAdded(limit = 50)
                response.tracks.map { trackToMediaItem(it) }
            },
            cacheCall = {
                db.trackDao().getRecentlyAdded(50).map { trackToMediaItem(it.toModel()) }
            }
        )
    }

    private var cachedBuckets: List<com.mvbar.android.data.model.RecBucket> = emptyList()

    private suspend fun getForYouBuckets(): List<MediaItem> {
        val buildBucketItems = { buckets: List<com.mvbar.android.data.model.RecBucket> ->
            cachedBuckets = buckets
            buckets.mapIndexed { index, bucket ->
                val artUri = bucket.artPaths.firstOrNull()?.let { ApiClient.artPathUrl(it) }
                MediaItem.Builder()
                    .setMediaId("bucket:$index")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(bucket.name)
                            .setSubtitle(bucket.subtitle ?: "${bucket.count} tracks")
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .apply { artUri?.let { setArtworkUri(ArtworkProvider.buildUri(it)) } }
                            .build()
                    )
                    .build()
            }
        }
        return apiOrCache("For You",
            apiCall = {
                val response = ApiClient.api.getRecommendations()
                buildBucketItems(response.buckets)
            },
            cacheCall = {
                val cached = db.recommendationDao().getAll().map { it.toModel() }
                buildBucketItems(cached)
            }
        )
    }

    private suspend fun getBucketTracks(indexStr: String): List<MediaItem> {
        val index = indexStr.toIntOrNull() ?: return emptyList()
        val bucket = cachedBuckets.getOrNull(index)
        if (bucket != null && bucket.tracks.isNotEmpty()) {
            return bucket.tracks.map { trackToMediaItem(it) }
        }
        return try {
            kotlinx.coroutines.withTimeout(5_000) {
                val response = ApiClient.api.getRecommendations()
                cachedBuckets = response.buckets
                response.buckets.getOrNull(index)?.tracks?.map { trackToMediaItem(it) } ?: emptyList()
            }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Bucket tracks failed (${e.message})")
            emptyList()
        }
    }

    private suspend fun getFavoriteTracks(): List<MediaItem> {
        return apiOrCache("Favorites",
            apiCall = { ApiClient.api.getFavorites().tracks.map { trackToMediaItem(it) } },
            cacheCall = { db.favoriteDao().getFavorites().map { trackToMediaItem(it.toModel()) } }
        )
    }

    private suspend fun getAlbumsList(): List<MediaItem> {
        val buildItem = { name: String, artist: String?, artPath: String? ->
            val artUri = artPath?.let { ApiClient.artPathUrl(it) }
            MediaItem.Builder()
                .setMediaId("album:$name")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setArtist(artist)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                        .apply { artUri?.let { setArtworkUri(ArtworkProvider.buildUri(it)) } }
                        .build()
                )
                .build()
        }
        return apiOrCache("Albums",
            apiCall = {
                ApiClient.api.getAlbums(limit = 100).albums.map {
                    buildItem(it.displayName, it.artist, it.artPath)
                }
            },
            cacheCall = {
                db.browseDao().getAlbums(100, 0).map {
                    val m = it.toModel()
                    buildItem(m.displayName, m.artist, m.artPath)
                }
            }
        )
    }

    private suspend fun getArtistsList(): List<MediaItem> {
        fun buildItem(id: Int?, name: String): MediaItem? {
            id ?: return null
            return MediaItem.Builder()
                .setMediaId("artist:$id")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                        .build()
                )
                .build()
        }
        return apiOrCache("Artists",
            apiCall = {
                ApiClient.api.getArtists(limit = 100).artists.mapNotNull { buildItem(it.id, it.name) }
            },
            cacheCall = {
                db.browseDao().getArtists(100, 0).mapNotNull { val m = it.toModel(); buildItem(m.id, m.name) }
            }
        )
    }

    private suspend fun getPlaylistsList(): List<MediaItem> {
        val playlists = apiOrCache("Playlists",
            apiCall = {
                ApiClient.api.getPlaylists().playlists.map { playlist ->
                    MediaItem.Builder()
                        .setMediaId("playlist:${playlist.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(playlist.name)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                .build()
                        )
                        .build()
                }
            },
            cacheCall = {
                db.playlistDao().getAll().map { entity ->
                    MediaItem.Builder()
                        .setMediaId("playlist:${entity.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(entity.name)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                .build()
                        )
                        .build()
                }
            }
        )

        val smartPlaylists = try {
            kotlinx.coroutines.withTimeout(5_000) {
                ApiClient.api.getSmartPlaylists().items.map { sp ->
                    MediaItem.Builder()
                        .setMediaId("smartpl:${sp.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("⚡ ${sp.name}")
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                .build()
                        )
                        .build()
                }
            }
        } catch (_: Exception) { emptyList() }

        return playlists + smartPlaylists
    }

    private suspend fun getGenresList(): List<MediaItem> {
        val buildItem = { name: String ->
            MediaItem.Builder()
                .setMediaId("genre:$name")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_GENRE)
                        .build()
                )
                .build()
        }
        return apiOrCache("Genres",
            apiCall = { ApiClient.api.getGenres(limit = 100).genres.map { buildItem(it.name) } },
            cacheCall = { db.browseDao().getGenres(100, 0).map { buildItem(it.name) } }
        )
    }

    private suspend fun getAlbumTracks(albumName: String): List<MediaItem> {
        return apiOrCache("Album tracks",
            apiCall = { ApiClient.api.getAlbumTracks(albumName).tracks.map { trackToMediaItem(it) } },
            cacheCall = { db.trackDao().getByAlbum(albumName).map { trackToMediaItem(it.toModel()) } }
        )
    }

    private suspend fun getArtistTracks(artistId: Int): List<MediaItem> {
        return apiOrCache("Artist tracks",
            apiCall = { ApiClient.api.getArtistTracks(artistId).tracks.map { trackToMediaItem(it) } },
            cacheCall = {
                val artists = db.browseDao().getArtists(200, 0)
                val artistName = artists.find { it.artistId == artistId }?.name
                if (artistName != null) db.trackDao().getByArtist(artistName).map { trackToMediaItem(it.toModel()) }
                else emptyList()
            }
        )
    }

    private suspend fun getPlaylistTracks(playlistId: Int): List<MediaItem> {
        return apiOrCache("Playlist tracks",
            apiCall = {
                ApiClient.api.getPlaylistItems(playlistId).items.mapNotNull { it.track?.let { t -> trackToMediaItem(t) } }
            },
            cacheCall = {
                db.playlistDao().getItems(playlistId).mapNotNull {
                    val track = db.trackDao().getById(it.trackId)
                    track?.let { t -> trackToMediaItem(t.toModel()) }
                }
            }
        )
    }

    private suspend fun getSmartPlaylistTracks(smartPlaylistId: Int): List<MediaItem> {
        return try {
            kotlinx.coroutines.withTimeout(5_000) {
                ApiClient.api.getSmartPlaylist(smartPlaylistId, limit = 100).tracks.map { trackToMediaItem(it) }
            }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Smart playlist tracks failed (${e.message})")
            emptyList()
        }
    }

    private suspend fun getGenreTracks(genreName: String): List<MediaItem> {
        return apiOrCache("Genre tracks",
            apiCall = { ApiClient.api.getGenreTracks(genreName, limit = 100).tracks.map { trackToMediaItem(it) } },
            cacheCall = { db.trackDao().getByGenre(genreName, 100, 0).map { trackToMediaItem(it.toModel()) } }
        )
    }

    // Language browse

    private suspend fun getLanguagesList(): List<MediaItem> {
        val buildItem = { name: String, subtitle: String? ->
            MediaItem.Builder()
                .setMediaId("language:$name")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .apply { subtitle?.let { setSubtitle(it) } }
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
        }
        return apiOrCache("Languages",
            apiCall = {
                ApiClient.api.getLanguages(limit = 100).languages.map {
                    buildItem(it.name, "${it.trackCount} tracks")
                }
            },
            cacheCall = {
                db.browseDao().getLanguages(100, 0).map { buildItem(it.name, null) }
            }
        )
    }

    private suspend fun getLanguageTracks(langName: String): List<MediaItem> {
        return try {
            kotlinx.coroutines.withTimeout(5_000) {
                ApiClient.api.getLanguageTracks(langName, limit = 100).tracks.map { trackToMediaItem(it) }
            }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Language tracks failed (${e.message})")
            emptyList()
        }
    }

    // Country browse

    private suspend fun getCountriesList(): List<MediaItem> {
        val buildItem = { name: String, subtitle: String? ->
            val flagUrl = com.mvbar.android.data.CountryFlags.flagUrl(name)
            MediaItem.Builder()
                .setMediaId("country:$name")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .apply { subtitle?.let { setSubtitle(it) } }
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .apply { if (flagUrl != null) setArtworkUri(Uri.parse(flagUrl)) }
                        .build()
                )
                .build()
        }
        return apiOrCache("Countries",
            apiCall = {
                ApiClient.api.getCountries(limit = 100).countries.map {
                    buildItem(it.name, "${it.trackCount} tracks")
                }
            },
            cacheCall = {
                db.browseDao().getCountries(100, 0).map { buildItem(it.name, null) }
            }
        )
    }

    private suspend fun getCountryTracks(countryName: String): List<MediaItem> {
        return try {
            kotlinx.coroutines.withTimeout(5_000) {
                ApiClient.api.getCountryTracks(countryName, limit = 100).tracks.map { trackToMediaItem(it) }
            }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Country tracks failed (${e.message})")
            emptyList()
        }
    }

    // Podcast browse

    private suspend fun getPodcastsList(): List<MediaItem> {
        val buildItem = { id: Int, title: String, author: String?, imagePath: String? ->
            val artUri = imagePath?.let { ApiClient.podcastArtPathUrl(it) }
                ?: ApiClient.podcastArtUrl(id)
            MediaItem.Builder()
                .setMediaId("podcast:$id")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(author)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                        .setArtworkUri(ArtworkProvider.buildUri(artUri))
                        .build()
                )
                .build()
        }
        return apiOrCache("Podcasts",
            apiCall = {
                ApiClient.api.getPodcasts().podcasts.map { buildItem(it.id, it.title, it.author, it.imagePath) }
            },
            cacheCall = {
                db.podcastDao().getAllPodcasts().map { e -> val m = e.toModel(); buildItem(m.id, m.title, m.author, m.imagePath) }
            }
        )
    }

    private suspend fun getPodcastEpisodes(podcastId: Int): List<MediaItem> {
        val buildEpisodeItem = { episode: com.mvbar.android.data.model.Episode, fallbackTitle: String? ->
            val artUri = episode.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
                ?: episode.podcastImagePath?.let { ApiClient.podcastArtPathUrl(it) }
                ?: ApiClient.episodeArtUrl(episode.id)
            val streamUrl = ApiClient.episodeStreamUrl(episode.id)
            val extras = Bundle().apply {
                putLong("resume_position_ms", episode.positionMs)
                putLong("duration_ms", episode.durationMs ?: 0L)
            }
            MediaItem.Builder()
                .setMediaId("ep:${episode.id}")
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(episode.podcastTitle ?: fallbackTitle)
                        .setArtworkUri(ArtworkProvider.buildUri(artUri))
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }
        return apiOrCache("Podcast episodes",
            apiCall = {
                val response = ApiClient.api.getPodcastDetail(podcastId)
                response.episodes.map { buildEpisodeItem(it, response.podcast?.title) }
            },
            cacheCall = {
                db.podcastDao().getEpisodes(podcastId).map { e -> buildEpisodeItem(e.toModel(), null) }
            }
        )
    }

    // Audiobook browse

    private suspend fun getAudiobooksList(): List<MediaItem> {
        val buildItem = { id: Int, title: String, author: String? ->
            val artUri = ApiClient.audiobookArtUrl(id)
            MediaItem.Builder()
                .setMediaId("audiobook:$id")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(author)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setArtworkUri(ArtworkProvider.buildUri(artUri))
                        .build()
                )
                .build()
        }
        return apiOrCache("Audiobooks",
            apiCall = { ApiClient.api.getAudiobooks().map { buildItem(it.id, it.title, it.author) } },
            cacheCall = { db.audiobookDao().getAllAudiobooks().map { e -> val m = e.toModel(); buildItem(m.id, m.title, m.author) } }
        )
    }

    private suspend fun getAudiobookChapters(audiobookId: Int): List<MediaItem> {
        val artUri = ApiClient.audiobookArtUrl(audiobookId)
        val buildChapterItem = { chapterId: Int, chapterTitle: String, bookTitle: String?, bookAuthor: String? ->
            val streamUrl = ApiClient.audiobookChapterStreamUrl(audiobookId, chapterId)
            MediaItem.Builder()
                .setMediaId("ab:${audiobookId}:${chapterId}")
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapterTitle)
                        .setArtist(bookAuthor)
                        .setAlbumTitle(bookTitle)
                        .setArtworkUri(ArtworkProvider.buildUri(artUri))
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()
                )
                .build()
        }
        return apiOrCache("Audiobook chapters",
            apiCall = {
                val response = ApiClient.api.getAudiobookDetail(audiobookId)
                val book = response.audiobook ?: return@apiOrCache emptyList()
                response.chapters.map { buildChapterItem(it.id, it.title, book.title, book.author) }
            },
            cacheCall = {
                val book = db.audiobookDao().getAllAudiobooks().find { it.id == audiobookId }
                db.audiobookDao().getChapters(audiobookId).map { e ->
                    val ch = e.toModel()
                    buildChapterItem(ch.id, ch.title, book?.title, book?.author)
                }
            }
        )
    }

    // Helpers

    private fun resolveStreamUri(item: MediaItem): MediaItem {
        if (item.localConfiguration?.uri != null) return item
        val mediaId = item.mediaId
        val uri = when {
            mediaId.startsWith("ep:") -> {
                val episodeId = mediaId.removePrefix("ep:").toIntOrNull() ?: return item
                ApiClient.episodeStreamUrl(episodeId)
            }
            mediaId.startsWith("ab:") -> {
                val parts = mediaId.removePrefix("ab:").split(":")
                if (parts.size != 2) return item
                val bookId = parts[0].toIntOrNull() ?: return item
                val chapterId = parts[1].toIntOrNull() ?: return item
                ApiClient.audiobookChapterStreamUrl(bookId, chapterId)
            }
            else -> {
                val trackId = mediaId.toIntOrNull() ?: return item
                ApiClient.streamUrl(trackId)
            }
        }
        DebugLog.i("Player", "resolveStreamUri: mediaId=$mediaId → $uri")
        return item.buildUpon().setUri(uri).build()
    }

    /** Prepend a "Shuffle All" item and cache tracks for queue-all on tap */
    private fun withShuffle(parentId: String, tracks: List<MediaItem>): List<MediaItem> {
        if (tracks.isEmpty()) return tracks
        browsedTrackCache[parentId] = tracks
        val shuffleItem = MediaItem.Builder()
            .setMediaId("shuffle:$parentId")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("\uD83D\uDD00 Shuffle All")
                    .setSubtitle("${tracks.size} tracks")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
        return listOf(shuffleItem) + tracks
    }

    /** Load tracks for a given parent ID (used by shuffle) */
    private suspend fun loadTracksForParent(parentId: String): List<MediaItem> {
        return when {
            parentId == RECENT_ID -> getRecentTracks()
            parentId == FAVORITES_ID -> getFavoriteTracks()
            parentId.startsWith("bucket:") -> getBucketTracks(parentId.removePrefix("bucket:"))
            parentId.startsWith("album:") -> getAlbumTracks(parentId.removePrefix("album:"))
            parentId.startsWith("artist:") -> getArtistTracks(parentId.removePrefix("artist:").toInt())
            parentId.startsWith("playlist:") -> getPlaylistTracks(parentId.removePrefix("playlist:").toInt())
            parentId.startsWith("smartpl:") -> getSmartPlaylistTracks(parentId.removePrefix("smartpl:").toInt())
            parentId.startsWith("genre:") -> getGenreTracks(parentId.removePrefix("genre:"))
            parentId.startsWith("language:") -> getLanguageTracks(parentId.removePrefix("language:"))
            parentId.startsWith("country:") -> getCountryTracks(parentId.removePrefix("country:"))
            else -> emptyList()
        }
    }

    private fun trackToMediaItem(track: com.mvbar.android.data.model.Track): MediaItem {
        val artUrl = track.artPath?.let { ApiClient.artPathUrl(it) }
            ?: ApiClient.trackArtUrl(track.id)
        val streamUrl = ApiClient.streamUrl(track.id)
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.displayTitle)
                    .setArtist(track.displayArtist)
                    .setAlbumTitle(track.displayAlbum)
                    .setArtworkUri(ArtworkProvider.buildUri(artUrl))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    /** Save current episode progress to server and local DB */
    private fun saveEpisodeProgress(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val posMs = player.currentPosition
        if (posMs <= 0L) return
        when {
            mediaId.startsWith("ep:") -> {
                val epId = mediaId.removePrefix("ep:").toIntOrNull() ?: return
                serviceScope.launch {
                    try {
                        db.podcastDao().updateEpisodePosition(epId, posMs)
                    } catch (_: Exception) {}
                    try {
                        if (NetworkMonitor.isOnline.value) {
                            ApiClient.api.updateEpisodeProgress(
                                epId,
                                com.mvbar.android.data.model.EpisodeProgressRequest(positionMs = posMs)
                            )
                        }
                    } catch (e: Exception) {
                        DebugLog.w("Player", "Failed to save episode progress", e)
                    }
                }
            }
            mediaId.startsWith("ab:") -> {
                val parts = mediaId.removePrefix("ab:").split(":")
                if (parts.size == 2) {
                    val bookId = parts[0].toIntOrNull()
                    val chapterId = parts[1].toIntOrNull()
                    if (bookId != null && chapterId != null) {
                        serviceScope.launch {
                            try {
                                if (NetworkMonitor.isOnline.value) {
                                    ApiClient.api.updateAudiobookProgress(
                                        bookId,
                                        com.mvbar.android.data.model.AudiobookProgressRequest(
                                            chapterId = chapterId, positionMs = posMs
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                DebugLog.w("Player", "Failed to save audiobook progress", e)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Periodically save episode/audiobook progress every 30 seconds */
    private fun startProgressSaving(player: Player) {
        progressSaveJob?.cancel()
        progressSaveJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                if (player.isPlaying) {
                    saveEpisodeProgress(player)
                }
            }
        }
    }

    /** Save current queue, index, and position for AA reconnect resume */
    private fun savePlaybackSnapshot(player: Player) {
        serviceScope.launch {
            try {
                val entries = (0 until player.mediaItemCount).map { i ->
                    val item = player.getMediaItemAt(i)
                    val meta = item.mediaMetadata
                    AaPreferences.QueueEntry(
                        mediaId = item.mediaId,
                        title = meta.title?.toString(),
                        artist = meta.artist?.toString(),
                        album = meta.albumTitle?.toString(),
                        artUri = meta.artworkUri?.toString()
                    )
                }
                if (entries.isEmpty()) return@launch
                AaPreferences.savePlaybackState(
                    this@PlaybackService,
                    entries,
                    player.currentMediaItemIndex,
                    player.currentPosition.coerceAtLeast(0L)
                )
            } catch (_: Exception) {}
        }
    }

    /** Restore last queue and resume playback (paused). Called when AA reconnects to an idle player. */
    private fun restorePlaybackState() {
        serviceScope.launch {
            try {
                val saved = AaPreferences.getSavedPlaybackState(this@PlaybackService) ?: return@launch
                val player = mediaSession?.player ?: return@launch
                if (player.mediaItemCount > 0) return@launch // already has a queue

                DebugLog.i("Player", "Restoring queue: ${saved.entries.size} items, idx=${saved.index}, pos=${saved.positionMs}ms")
                val items = saved.entries.map { entry ->
                    val builder = MediaItem.Builder().setMediaId(entry.mediaId)
                    val metaBuilder = MediaMetadata.Builder()
                    entry.title?.let { metaBuilder.setTitle(it) }
                    entry.artist?.let { metaBuilder.setArtist(it) }
                    entry.album?.let { metaBuilder.setAlbumTitle(it) }
                    entry.artUri?.let { metaBuilder.setArtworkUri(android.net.Uri.parse(it)) }
                    metaBuilder.setIsPlayable(true)
                    builder.setMediaMetadata(metaBuilder.build())
                    resolveStreamUri(builder.build())
                }
                val wasShuffling = player.shuffleModeEnabled
                if (wasShuffling) player.shuffleModeEnabled = false
                player.setMediaItems(items, saved.index, saved.positionMs)
                player.prepare()
                // Don't auto-play — let the user press play on the head unit
                if (wasShuffling) player.shuffleModeEnabled = true
                DebugLog.i("Player", "Queue restored, ready to resume")
            } catch (e: Exception) {
                DebugLog.w("Player", "Failed to restore playback state", e)
            }
        }
    }

    private fun browseFolderItem(
        id: String,
        title: String,
        mediaType: Int,
        extras: Bundle? = null
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .apply { extras?.let { setExtras(it) } }
                .build()
        )
        .build()
}
