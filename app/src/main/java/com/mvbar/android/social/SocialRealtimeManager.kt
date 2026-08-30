package com.mvbar.android.social

import android.content.Context
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl
import java.util.concurrent.TimeUnit

object SocialRealtimeManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private var appContext: Context? = null
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var stopped = true
    private var attempts = 0

    @Synchronized
    fun start(context: Context) {
        appContext = context.applicationContext
        stopped = false
        if (socket != null || ApiClient.getToken().isNullOrBlank()) return
        connect()
    }

    /**
     * Invalidates social data held by active screens. This is also called by the
     * background notification worker, whose server snapshot is newer than any
     * view model that Android may have kept alive while the app was stopped.
     */
    fun requestRefresh() {
        _revision.update { it + 1 }
    }

    @Synchronized
    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "Signed out")
        socket = null
        attempts = 0
    }

    @Synchronized
    private fun connect() {
        if (stopped || socket != null) return
        val token = ApiClient.getToken() ?: return
        val url = webSocketRequestUrl(ApiClient.getBaseUrl()) ?: return
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("X-MVBar-Client", "android")
            .build()
        socket = client.newWebSocket(request, Listener())
    }

    /** OkHttp upgrades HTTP(S) requests to WS(S) inside newWebSocket(). */
    internal fun webSocketRequestUrl(baseUrl: String): HttpUrl? =
        baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/ws")
            ?.build()

    @Synchronized
    private fun disconnected(webSocket: WebSocket) {
        if (socket !== webSocket) return
        socket = null
        if (stopped) return
        reconnectJob?.cancel()
        val waitMs = (2_000L shl attempts.coerceAtMost(4)).coerceAtMost(30_000L)
        attempts++
        reconnectJob = scope.launch {
            delay(waitMs)
            synchronized(this@SocialRealtimeManager) { connect() }
        }
    }

    private class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempts = 0
            DebugLog.i("SocialWS", "Connected")
            // Events are not replayed after a disconnect, so catch up from the API.
            requestRefresh()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val root = json.parseToJsonElement(text).jsonObject
                val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return
                if (type == "ping") {
                    webSocket.send("{\"type\":\"pong\"}")
                    return
                }
                if (type.startsWith("social:") || type.startsWith("playlist:collaborator_")) {
                    handleSocialEvent(type, root["data"]?.jsonObject)
                    requestRefresh()
                }
            } catch (e: Exception) {
                DebugLog.e("SocialWS", "Invalid event", e)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            disconnected(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            DebugLog.d("SocialWS", "Disconnected: ${t.message}")
            disconnected(webSocket)
        }
    }

    private fun handleSocialEvent(
        type: String,
        data: kotlinx.serialization.json.JsonObject?
    ) {
        val context = appContext ?: return
        when (type) {
            "social:friend_request" -> {
                val id = data?.get("relationshipId")?.jsonPrimitive?.intOrNull ?: return
                val email = data["user"]?.jsonObject?.get("email")?.jsonPrimitive?.contentOrNull.orEmpty()
                SocialNotificationManager.notifyFriendRequest(context, id, email)
            }
            "social:friend_accepted" -> {
                val id = data?.get("relationshipId")?.jsonPrimitive?.intOrNull ?: return
                val email = data["user"]?.jsonObject?.get("email")?.jsonPrimitive?.contentOrNull.orEmpty()
                SocialNotificationManager.notifyFriendAccepted(context, id, email)
            }
            "social:track_shared" -> {
                val id = data?.get("shareId")?.jsonPrimitive?.intOrNull ?: return
                val email = data["sender"]?.jsonObject?.get("email")?.jsonPrimitive?.contentOrNull.orEmpty()
                val title = data["track"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                SocialNotificationManager.notifyTrackShared(context, id, email, title)
            }
            "social:playlist_shared" -> {
                val playlist = data?.get("playlist")?.jsonObject ?: return
                val id = playlist["id"]?.jsonPrimitive?.intOrNull ?: return
                val name = playlist["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val email = data["sender"]?.jsonObject?.get("email")?.jsonPrimitive?.contentOrNull.orEmpty()
                val sharedAt = data["sharedAt"]?.jsonPrimitive?.contentOrNull
                SocialNotificationManager.notifyPlaylistShared(context, id, email, name, sharedAt)
            }
        }
    }
}
