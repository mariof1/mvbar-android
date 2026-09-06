package com.mvbar.android.tv.data

import android.os.Build
import android.util.Log
import com.mvbar.android.tv.BuildConfig
import com.mvbar.android.tv.playback.PlaybackKind
import com.mvbar.android.tv.playback.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class TvRealtimeRefresh { ALL_CONTENT, RECOMMENDATIONS, LONG_FORM, NONE }

@Serializable
data class TvConnectTrack(
    val id: Int,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artPath: String? = null,
    val durationMs: Long? = null
)

@Serializable
data class TvConnectPlaybackState(
    val track: TvConnectTrack? = null,
    val queueIndex: Int = -1,
    val queueLength: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Double? = null,
    val updatedAt: Long = 0
)

@Serializable
data class TvConnectDevice(
    val id: String,
    val name: String,
    val type: String = "unknown",
    val appVersion: String? = null,
    val platform: String? = null,
    val capabilities: List<String> = emptyList(),
    val state: TvConnectPlaybackState = TvConnectPlaybackState()
)

@Serializable
private data class TvConnectDevicesPayload(val devices: List<TvConnectDevice> = emptyList())

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
    private val onEvent: (String) -> Unit,
    private val onConnectCommand: (String, JsonObject) -> Unit,
    private val onConnectDevices: (List<TvConnectDevice>) -> Unit,
    private val onConnectError: (String) -> Unit
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
    private var latestPlaybackState: JsonObject = playbackStateJson(PlaybackSnapshot())
    private var lastStateSignature = ""
    private var lastStateSentAt = 0L

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
        onConnectDevices(emptyList())
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
            .header("X-MVBar-Version", BuildConfig.VERSION_NAME)
            .header("X-MVBar-Device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .header("X-MVBar-Platform", "Android TV ${Build.VERSION.RELEASE}")
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
        onConnectDevices(emptyList())
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
            send(webSocket, "connect:register", buildJsonObject {
                put("deviceId", clientId)
                put("name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                put("type", "android-tv")
                put("appVersion", BuildConfig.VERSION_NAME)
                put("platform", "Android TV ${Build.VERSION.RELEASE}")
                put("capabilities", buildJsonArray {
                    add(JsonPrimitive("music"))
                    add(JsonPrimitive("remote-control"))
                    add(JsonPrimitive("transfer"))
                })
                put("state", latestPlaybackState)
            })
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val root = json.parseToJsonElement(text).jsonObject
                val type = root["type"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: return
                if (type == "ping") {
                    webSocket.send("{\"type\":\"pong\"}")
                } else if (type == "connect:command") {
                    val data = root["data"]?.jsonObject ?: return
                    val command = data["command"]?.jsonPrimitive?.contentOrNull ?: return
                    onConnectCommand(command, data["payload"]?.jsonObject ?: JsonObject(emptyMap()))
                } else if (type == "connect:devices") {
                    val devices = root["data"]?.let {
                        json.decodeFromJsonElement<TvConnectDevicesPayload>(it)
                    }?.devices.orEmpty()
                    onConnectDevices(devices)
                } else if (type == "connect:command_ack") {
                    val data = root["data"]?.jsonObject
                    if (data?.get("accepted")?.jsonPrimitive?.contentOrNull == "false") {
                        onConnectError(
                            data["error"]?.jsonPrimitive?.contentOrNull
                                ?: "The selected player is unavailable"
                        )
                    }
                } else if (type == "connect:error") {
                    onConnectError(
                        root["data"]?.jsonObject?.get("error")?.jsonPrimitive?.contentOrNull
                            ?: "MVBar Connect error"
                    )
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

    fun publishPlayback(snapshot: PlaybackSnapshot) {
        latestPlaybackState = playbackStateJson(snapshot)
        val signature = buildString {
            append(snapshot.item?.mediaId)
            append(':').append(snapshot.isPlaying)
            append(':').append(snapshot.currentIndex)
            append(':').append(snapshot.queue.joinToString(",") { it.mediaId })
        }
        val now = System.currentTimeMillis()
        if (signature == lastStateSignature && now - lastStateSentAt < 3_000) return
        lastStateSignature = signature
        lastStateSentAt = now
        socket?.let { send(it, "connect:state", latestPlaybackState) }
    }

    fun sendCommand(targetDeviceId: String, command: String, payload: JsonObject = JsonObject(emptyMap())): Boolean {
        val active = socket ?: return false
        if (targetDeviceId.isBlank() || targetDeviceId == clientId) return false
        send(active, "connect:command", buildJsonObject {
            put("targetDeviceId", targetDeviceId)
            put("commandId", "tv_${System.currentTimeMillis()}_$command")
            put("command", command)
            put("payload", payload)
        })
        return true
    }

    fun transferPlayback(sourceDeviceId: String, targetDeviceId: String): Boolean {
        val active = socket ?: return false
        if (sourceDeviceId.isBlank() || targetDeviceId.isBlank() || sourceDeviceId == targetDeviceId) return false
        send(active, "connect:transfer", buildJsonObject {
            put("sourceDeviceId", sourceDeviceId)
            put("targetDeviceId", targetDeviceId)
            put("commandId", "tv_transfer_${System.currentTimeMillis()}")
        })
        return true
    }

    fun deviceId(): String = clientId

    private fun playbackStateJson(snapshot: PlaybackSnapshot): JsonObject = buildJsonObject {
        val musicQueue = snapshot.queue.filter { it.kind == PlaybackKind.MUSIC && it.trackId != null }.take(500)
        val current = snapshot.item?.takeIf { it.kind == PlaybackKind.MUSIC && it.trackId != null }
        fun trackJson(item: com.mvbar.android.tv.playback.PlaybackItem) = buildJsonObject {
            put("id", item.trackId!!)
            put("title", item.title)
            put("artist", item.artist)
            put("album", item.album)
            put("durationMs", item.durationMs)
        }
        put("track", current?.let(::trackJson) ?: kotlinx.serialization.json.JsonNull)
        put("queue", buildJsonArray { musicQueue.forEach { add(trackJson(it)) } })
        put("queueIndex", current?.let { active -> musicQueue.indexOfFirst { it.mediaId == active.mediaId }.coerceAtLeast(0) } ?: -1)
        put("isPlaying", current != null && snapshot.isPlaying)
        put("positionMs", if (current == null) 0 else snapshot.positionMs)
        put("durationMs", if (current == null) 0 else snapshot.durationMs)
    }

    private fun send(webSocket: WebSocket, type: String, data: JsonObject) {
        webSocket.send(buildJsonObject {
            put("type", type)
            put("data", data)
        }.toString())
    }
}
