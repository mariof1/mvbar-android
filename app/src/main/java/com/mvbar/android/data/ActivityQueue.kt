package com.mvbar.android.data

import android.content.Context
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.FavoriteTrackEntity
import com.mvbar.android.data.model.AudiobookProgressRequest
import com.mvbar.android.data.model.EpisodePlayedRequest
import com.mvbar.android.data.model.EpisodeProgressRequest
import com.mvbar.android.data.model.PodcastSubscribeRequest
import com.mvbar.android.data.local.entity.PendingActionEntity
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Offline-resilient activity queue. Every user action (play, skip, favorite)
 * is persisted to Room first, then flushed to the server when network is
 * available. On reconnect the queue is drained automatically.
 */
object ActivityQueue {

    const val ACTION_PLAY = "PLAY"
    const val ACTION_SKIP = "SKIP"
    const val ACTION_ADD_FAVORITE = "ADD_FAVORITE"
    const val ACTION_REMOVE_FAVORITE = "REMOVE_FAVORITE"
    const val ACTION_PLAYLIST_CREATE = "PLAYLIST_CREATE"
    const val ACTION_PLAYLIST_RENAME = "PLAYLIST_RENAME"
    const val ACTION_PLAYLIST_DELETE = "PLAYLIST_DELETE"
    const val ACTION_PLAYLIST_ADD_TRACK = "PLAYLIST_ADD_TRACK"
    const val ACTION_PLAYLIST_REMOVE_TRACK = "PLAYLIST_REMOVE_TRACK"
    const val ACTION_PODCAST_PROGRESS = "PODCAST_PROGRESS"
    const val ACTION_PODCAST_PLAYED = "PODCAST_PLAYED"
    const val ACTION_PODCAST_SUBSCRIBE = "PODCAST_SUBSCRIBE"
    const val ACTION_PODCAST_UNSUBSCRIBE = "PODCAST_UNSUBSCRIBE"
    const val ACTION_AUDIOBOOK_PROGRESS = "AUDIOBOOK_PROGRESS"
    const val ACTION_AUDIOBOOK_FINISHED = "AUDIOBOOK_FINISHED"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private var db: MvbarDatabase? = null
    private var reconnectListener: (() -> Unit)? = null

    /** Call once from Application.onCreate or PlaybackService.onCreate */
    fun init(context: Context) {
        val appCtx = context.applicationContext
        db = MvbarDatabase.getInstance(appCtx)

        // Ensure NetworkMonitor is initialised
        NetworkMonitor.init(appCtx)

        // Flush pending actions whenever network comes back
        if (reconnectListener == null) {
            val listener: () -> Unit = {
                DebugLog.i("ActivityQueue", "Network restored — flushing pending actions")
                scope.launch { flush() }
            }
            reconnectListener = listener
            NetworkMonitor.addReconnectListener(listener)
        }

        // Attempt an initial flush in case there are leftovers from last session
        scope.launch { flush() }
    }

    /** Enqueue an action. It is persisted immediately and flushed if online. */
    fun enqueue(actionType: String, trackId: Int, payload: String? = null) {
        val database = db ?: return
        scope.launch {
            try {
                applyLocalAction(database, actionType, trackId, payload)
                if (actionType == ACTION_PODCAST_PROGRESS || actionType == ACTION_AUDIOBOOK_PROGRESS) {
                    database.pendingActionDao().deleteByTypeAndTrack(actionType, trackId)
                }
                database.pendingActionDao().insert(PendingActionEntity(actionType = actionType, trackId = trackId, payload = payload))
                DebugLog.d("ActivityQueue", "Queued $actionType for track $trackId")
            } catch (e: Exception) {
                DebugLog.e("ActivityQueue", "Failed to queue $actionType", e)
            }
            // Try to flush right away if online
            if (NetworkMonitor.isOnline.value) {
                flush()
            }
        }
    }

    fun enqueuePlaylistCreate(tempPlaylistId: Int, name: String) {
        enqueue(ACTION_PLAYLIST_CREATE, tempPlaylistId, JSONObject().put("name", name).toString())
    }

    fun enqueuePlaylistRename(playlistId: Int, name: String) {
        enqueue(ACTION_PLAYLIST_RENAME, playlistId, JSONObject().put("name", name).toString())
    }

    fun enqueuePlaylistDelete(playlistId: Int) {
        enqueue(ACTION_PLAYLIST_DELETE, playlistId)
    }

    fun enqueuePlaylistAddTrack(playlistId: Int, trackId: Int) {
        enqueue(ACTION_PLAYLIST_ADD_TRACK, playlistId, JSONObject().put("trackId", trackId).toString())
    }

    fun enqueuePlaylistRemoveTrack(playlistId: Int, trackId: Int) {
        enqueue(ACTION_PLAYLIST_REMOVE_TRACK, playlistId, JSONObject().put("trackId", trackId).toString())
    }

    fun enqueuePodcastProgress(episodeId: Int, positionMs: Long) {
        enqueue(ACTION_PODCAST_PROGRESS, episodeId, JSONObject().put("positionMs", positionMs).toString())
    }

    fun enqueuePodcastPlayed(episodeId: Int, played: Boolean) {
        enqueue(ACTION_PODCAST_PLAYED, episodeId, JSONObject().put("played", played).toString())
    }

    fun enqueuePodcastSubscribe(feedUrl: String) {
        enqueue(ACTION_PODCAST_SUBSCRIBE, 0, JSONObject().put("feedUrl", feedUrl).toString())
    }

    fun enqueuePodcastUnsubscribe(podcastId: Int) {
        enqueue(ACTION_PODCAST_UNSUBSCRIBE, podcastId)
    }

    fun enqueueAudiobookProgress(audiobookId: Int, chapterId: Int, positionMs: Long) {
        enqueue(
            ACTION_AUDIOBOOK_PROGRESS,
            audiobookId,
            JSONObject().put("chapterId", chapterId).put("positionMs", positionMs).toString()
        )
    }

    fun enqueueAudiobookFinished(audiobookId: Int) {
        enqueue(ACTION_AUDIOBOOK_FINISHED, audiobookId)
    }

    private suspend fun applyLocalAction(database: MvbarDatabase, actionType: String, trackId: Int, payload: String?) {
        when (actionType) {
            ACTION_PLAY -> database.historyDao().recordPlay(trackId)
            ACTION_ADD_FAVORITE -> database.favoriteDao().insert(FavoriteTrackEntity(trackId))
            ACTION_REMOVE_FAVORITE -> database.favoriteDao().deleteTrack(trackId)
            ACTION_PODCAST_PROGRESS -> {
                val positionMs = payloadJson(payload)?.optLong("positionMs", 0L) ?: 0L
                if (positionMs > 0L) database.podcastDao().updateEpisodePosition(trackId, positionMs)
            }
            ACTION_PODCAST_PLAYED -> {
                val played = payloadJson(payload)?.optBoolean("played", true) ?: true
                database.podcastDao().markEpisodePlayedLocal(trackId, played)
            }
        }
    }

    private fun payloadJson(payload: String?): JSONObject? =
        payload?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** Drain the queue, sending each action to the server in order. */
    suspend fun flush() {
        val database = db ?: return
        // Prevent concurrent flushes
        if (!flushMutex.tryLock()) return
        try {
            val actions = database.pendingActionDao().getAll()
            if (actions.isEmpty()) return
            DebugLog.i("ActivityQueue", "Flushing ${actions.size} pending actions")

            val remappedPlaylistIds = mutableMapOf<Int, Int>()
            for (action in actions) {
                try {
                    val resolvedSubjectId = remappedPlaylistIds[action.trackId] ?: action.trackId
                    val payloadJson = payloadJson(action.payload)
                    when (action.actionType) {
                        ACTION_PLAY -> {
                            ApiClient.api.recordPlay(action.trackId)
                        }
                        ACTION_SKIP -> {
                            val body = payloadJson?.let { mapOf("pct" to it.optInt("pct", 0)) }
                            ApiClient.api.recordSkip(action.trackId, body)
                        }
                        ACTION_ADD_FAVORITE -> {
                            ApiClient.api.addFavorite(action.trackId)
                        }
                        ACTION_REMOVE_FAVORITE -> {
                            ApiClient.api.removeFavorite(action.trackId)
                        }
                        ACTION_PLAYLIST_CREATE -> {
                            val name = payloadJson?.optString("name").orEmpty()
                            if (name.isNotBlank()) {
                                val response = ApiClient.api.createPlaylist(mapOf("name" to name))
                                val newId = response.playlist?.id
                                if (newId == null || newId <= 0) {
                                    error("Playlist create returned no server id")
                                }
                                remappedPlaylistIds[action.trackId] = newId
                                database.pendingActionDao().replaceTrackId(action.trackId, newId)
                            }
                        }
                        ACTION_PLAYLIST_RENAME -> {
                            val name = payloadJson?.optString("name").orEmpty()
                            if (name.isNotBlank()) {
                                ApiClient.api.renamePlaylist(resolvedSubjectId, mapOf("name" to name))
                            }
                        }
                        ACTION_PLAYLIST_DELETE -> {
                            ApiClient.api.deletePlaylist(resolvedSubjectId)
                        }
                        ACTION_PLAYLIST_ADD_TRACK -> {
                            val addTrackId = payloadJson?.optInt("trackId", 0) ?: 0
                            if (addTrackId > 0) {
                                ApiClient.api.addToPlaylist(resolvedSubjectId, mapOf("trackId" to addTrackId))
                            }
                        }
                        ACTION_PLAYLIST_REMOVE_TRACK -> {
                            val removeTrackId = payloadJson?.optInt("trackId", 0) ?: 0
                            if (removeTrackId > 0) {
                                ApiClient.api.removeFromPlaylist(resolvedSubjectId, removeTrackId)
                            }
                        }
                        ACTION_PODCAST_PROGRESS -> {
                            val positionMs = payloadJson?.optLong("positionMs", 0L) ?: 0L
                            ApiClient.api.updateEpisodeProgress(action.trackId, EpisodeProgressRequest(positionMs = positionMs))
                        }
                        ACTION_PODCAST_PLAYED -> {
                            val played = payloadJson?.optBoolean("played", true) ?: true
                            ApiClient.api.markEpisodePlayed(action.trackId, EpisodePlayedRequest(played))
                        }
                        ACTION_PODCAST_SUBSCRIBE -> {
                            val feedUrl = payloadJson?.optString("feedUrl").orEmpty()
                            if (feedUrl.isNotBlank()) {
                                ApiClient.api.subscribePodcast(PodcastSubscribeRequest(feedUrl))
                            }
                        }
                        ACTION_PODCAST_UNSUBSCRIBE -> {
                            ApiClient.api.unsubscribePodcast(action.trackId)
                        }
                        ACTION_AUDIOBOOK_PROGRESS -> {
                            val chapterId = payloadJson?.optInt("chapterId", 0) ?: 0
                            val positionMs = payloadJson?.optLong("positionMs", 0L) ?: 0L
                            if (chapterId > 0) {
                                ApiClient.api.updateAudiobookProgress(
                                    action.trackId,
                                    AudiobookProgressRequest(chapterId = chapterId, positionMs = positionMs)
                                )
                            }
                        }
                        ACTION_AUDIOBOOK_FINISHED -> {
                            ApiClient.api.markAudiobookFinished(action.trackId)
                        }
                    }
                    // Success — remove from queue
                    database.pendingActionDao().deleteById(action.id)
                    DebugLog.d("ActivityQueue", "Flushed ${action.actionType} for track ${action.trackId}")
                } catch (e: Exception) {
                    // Network or server error — stop flushing, retry later
                    DebugLog.e("ActivityQueue", "Flush failed at ${action.actionType} #${action.trackId}", e)
                    break
                }
            }
        } finally {
            flushMutex.unlock()
        }
    }
}
