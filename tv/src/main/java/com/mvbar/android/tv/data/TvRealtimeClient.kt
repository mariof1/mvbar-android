package com.mvbar.android.tv.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class TvRealtimeRefresh { ALL_CONTENT, RECOMMENDATIONS, LONG_FORM, NONE }

internal fun realtimeRefreshForEvent(type: String): TvRealtimeRefresh = when {
    type == "connected" -> TvRealtimeRefresh.ALL_CONTENT
    type == "library:update" -> TvRealtimeRefresh.ALL_CONTENT
    type.startsWith("playlist:") -> TvRealtimeRefresh.ALL_CONTENT
    type.startsWith("favorite:") -> TvRealtimeRefresh.ALL_CONTENT
    type == "history:added" -> TvRealtimeRefresh.RECOMMENDATIONS
    type == "podcast:progress" -> TvRealtimeRefresh.LONG_FORM
    else -> TvRealtimeRefresh.NONE
}

internal fun tvWebSocketUrl(baseUrl: String): HttpUrl? = baseUrl.toHttpUrlOrNull()
    ?.newBuilder()
    ?.addPathSegments("api/ws")
    ?.build()

class TvRealtimeClient(
    private val scope: CoroutineScope,
    private val onEvent: (String) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var session: TvSession? = null
    private var clientId: String = ""
    private var stopped = true
    private var attempts = 0

    @Synchronized
    fun start(session: TvSession, clientId: String) {
        stopSocket("Reconnect")
        this.session = session
        this.clientId = clientId
        stopped = false
        attempts = 0
        connect()
    }

    @Synchronized
    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopSocket("Signed out")
        session = null
        attempts = 0
    }

    @Synchronized
    private fun connect() {
        if (stopped || socket != null) return
        val activeSession = session ?: return
        val url = tvWebSocketUrl(activeSession.serverUrl) ?: return
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${activeSession.token}")
            .header("X-MVBar-Client", "android-tv")
            .header("X-MVBar-Client-Id", clientId)
            .build()
        socket = client.newWebSocket(request, Listener())
    }

    @Synchronized
    private fun stopSocket(reason: String) {
        val active = socket
        socket = null
        active?.close(1000, reason)
    }

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
            synchronized(this@TvRealtimeClient) { connect() }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempts = 0
            Log.i("MvbarTvWS", "Connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val type = json.parseToJsonElement(text).jsonObject["type"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: return
                if (type == "ping") {
                    webSocket.send("{\"type\":\"pong\"}")
                } else {
                    onEvent(type)
                }
            }.onFailure { error ->
                Log.w("MvbarTvWS", "Ignoring malformed event", error)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            disconnected(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            Log.d("MvbarTvWS", "Disconnected: ${error.message}")
            disconnected(webSocket)
        }
    }
}
