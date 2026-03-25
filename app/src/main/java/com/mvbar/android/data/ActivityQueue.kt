package com.mvbar.android.data

import android.content.Context
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
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
                database.pendingActionDao().insert(
                    PendingActionEntity(
                        actionType = actionType,
                        trackId = trackId,
                        payload = payload
                    )
                )
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

    /** Drain the queue, sending each action to the server in order. */
    suspend fun flush() {
        val database = db ?: return
        // Prevent concurrent flushes
        if (!flushMutex.tryLock()) return
        try {
            val actions = database.pendingActionDao().getAll()
            if (actions.isEmpty()) return
            DebugLog.i("ActivityQueue", "Flushing ${actions.size} pending actions")

            for (action in actions) {
                try {
                    when (action.actionType) {
                        ACTION_PLAY -> {
                            ApiClient.api.recordPlay(action.trackId)
                        }
                        ACTION_SKIP -> {
                            val body = action.payload?.let { json ->
                                try {
                                    val obj = JSONObject(json)
                                    mapOf("pct" to obj.optInt("pct", 0))
                                } catch (_: Exception) { null }
                            }
                            ApiClient.api.recordSkip(action.trackId, body)
                        }
                        ACTION_ADD_FAVORITE -> {
                            ApiClient.api.addFavorite(action.trackId)
                        }
                        ACTION_REMOVE_FAVORITE -> {
                            ApiClient.api.removeFavorite(action.trackId)
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
