package com.mvbar.android.social

import android.content.Context
import android.os.Build
import com.mvbar.android.BuildConfig
import com.mvbar.android.connect.ConnectCommandPayload
import com.mvbar.android.connect.ConnectDevice
import com.mvbar.android.connect.ConnectDevicesPayload
import com.mvbar.android.connect.ConnectTrack
import com.mvbar.android.connect.toConnectTrack
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.PlayerManager
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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

    private val _connectDevices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    val connectDevices: StateFlow<List<ConnectDevice>> = _connectDevices.asStateFlow()

    private val _selectedConnectDeviceId = MutableStateFlow<String?>(null)
    val selectedConnectDeviceId: StateFlow<String?> = _selectedConnectDeviceId.asStateFlow()

    private var appContext: Context? = null
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var connectStateJob: Job? = null
    private var stopped = true
    private var attempts = 0
    private var lastConnectStateSignature = ""
    private var lastConnectStateSentAt = 0L

    @Synchronized
    fun start(context: Context) {
        appContext = context.applicationContext
        stopped = false
        if (socket != null || ApiClient.getToken().isNullOrBlank()) return
        startConnectStatePublisher(context.applicationContext)
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
        connectStateJob?.cancel()
        connectStateJob = null
        socket?.close(1000, "Signed out")
        socket = null
        attempts = 0
        _connectDevices.value = emptyList()
        _selectedConnectDeviceId.value = null
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
            .header("X-MVBar-Client-Id", ApiClient.getClientId())
            .header("X-MVBar-Version", BuildConfig.VERSION_NAME)
            .header("X-MVBar-Device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .header("X-MVBar-Platform", "Android ${Build.VERSION.RELEASE}")
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
        _connectDevices.value = emptyList()
        _selectedConnectDeviceId.value = null
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
            sendConnectRegistration(webSocket)
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
                if (type == "connect:devices") {
                    val payload = root["data"]?.let { json.decodeFromJsonElement<ConnectDevicesPayload>(it) }
                        ?: ConnectDevicesPayload()
                    _connectDevices.value = payload.devices
                    val selected = _selectedConnectDeviceId.value
                    if (selected == null || payload.devices.none { it.id == selected }) {
                        _selectedConnectDeviceId.value = payload.devices.firstOrNull {
                            it.id == ApiClient.getClientId()
                        }?.id ?: payload.devices.firstOrNull { it.state.isPlaying }?.id
                    }
                    return
                }
                if (type == "connect:command") {
                    val command = root["data"]?.let { json.decodeFromJsonElement<ConnectCommandPayload>(it) }
                        ?: return
                    handleConnectCommand(command)
                    return
                }
                if (type == "connect:command_ack") {
                    val data = root["data"]?.jsonObject
                    if (data?.get("accepted")?.jsonPrimitive?.booleanOrNull == false) {
                        val error = data["error"]?.jsonPrimitive?.contentOrNull ?: "The selected player is unavailable"
                        com.mvbar.android.ui.components.ToastManager.show(
                            error,
                            com.mvbar.android.ui.components.ToastIcon.ERROR
                        )
                    }
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

    private fun startConnectStatePublisher(context: Context) {
        if (connectStateJob != null) return
        connectStateJob = scope.launch {
            PlayerManager.getInstance(context).state.collect { state ->
                val signature = buildString {
                    append(state.currentTrack?.id)
                    append(':').append(state.isPlaying)
                    append(':').append(state.queueIndex)
                    append(':').append(state.queue.joinToString(",") { it.id.toString() })
                }
                val now = System.currentTimeMillis()
                if (signature != lastConnectStateSignature || now - lastConnectStateSentAt >= 3_000) {
                    lastConnectStateSignature = signature
                    lastConnectStateSentAt = now
                    sendConnectState(state)
                }
            }
        }
    }

    private fun connectStateJson(state: com.mvbar.android.player.PlayerState) = buildJsonObject {
        val musicQueue = state.queue.filter { it.id > 0 }.take(500)
        val activeTrack = state.currentTrack?.takeIf { it.id > 0 }
        put("track", activeTrack?.let { json.encodeToJsonElement(it.toConnectTrack()) }
            ?: kotlinx.serialization.json.JsonNull)
        put("queue", buildJsonArray {
            musicQueue.forEach { add(json.encodeToJsonElement(it.toConnectTrack())) }
        })
        put("queueIndex", if (activeTrack == null) -1 else musicQueue.indexOfFirst { it.id == activeTrack.id }.coerceAtLeast(0))
        put("isPlaying", activeTrack != null && state.isPlaying)
        put("positionMs", if (activeTrack == null) 0 else state.position)
        put("durationMs", if (activeTrack == null) 0 else state.duration)
    }

    private fun sendConnectRegistration(webSocket: WebSocket) {
        val context = appContext ?: return
        val state = PlayerManager.getInstance(context).state.value
        val payload = buildJsonObject {
            put("deviceId", ApiClient.getClientId())
            put("name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("type", "android")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("platform", "Android ${Build.VERSION.RELEASE}")
            put("capabilities", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("music"))
                add(kotlinx.serialization.json.JsonPrimitive("remote-control"))
                add(kotlinx.serialization.json.JsonPrimitive("transfer"))
            })
            put("state", connectStateJson(state))
        }
        webSocket.send(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject {
            put("type", "connect:register")
            put("data", payload)
        }))
    }

    private fun sendConnectState(state: com.mvbar.android.player.PlayerState) {
        sendJson("connect:state", connectStateJson(state))
    }

    private fun sendJson(type: String, data: kotlinx.serialization.json.JsonObject): Boolean {
        val message = buildJsonObject {
            put("type", type)
            put("data", data)
        }
        return socket?.send(
            json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), message)
        ) == true
    }

    private fun handleConnectCommand(command: ConnectCommandPayload) {
        val context = appContext ?: return
        scope.launch(Dispatchers.Main) {
            val player = PlayerManager.getInstance(context)
            val payload = command.payload
            when (command.command) {
                "play" -> player.play()
                "pause" -> player.pause()
                "toggle" -> player.togglePlay()
                "next" -> player.next()
                "previous" -> player.previous()
                "seek" -> player.seekTo(payload["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L)
                "stop" -> player.clearQueue()
                "play_index" -> player.playQueueIndex(payload["index"]?.jsonPrimitive?.intOrNull ?: 0)
                "remove_index" -> player.removeFromQueue(payload["index"]?.jsonPrimitive?.intOrNull ?: 0)
                "reorder" -> player.moveInQueue(
                    payload["from"]?.jsonPrimitive?.intOrNull ?: 0,
                    payload["to"]?.jsonPrimitive?.intOrNull ?: 0
                )
                "clear_queue" -> player.clearQueue()
                "play_tracks", "add_tracks" -> {
                    val tracks = payload["tracks"]?.jsonArray?.mapNotNull { element ->
                        runCatching { json.decodeFromJsonElement<ConnectTrack>(element).toTrack() }.getOrNull()
                    }.orEmpty()
                    if (command.command == "add_tracks") {
                        player.appendTracks(tracks)
                    } else if (tracks.isNotEmpty()) {
                        val index = (payload["queueIndex"]?.jsonPrimitive?.intOrNull ?: 0)
                            .coerceIn(0, tracks.lastIndex)
                        if (player.playTracks(tracks, index)) {
                            val position = payload["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L
                            if (position > 0) player.seekTo(position)
                            if (payload["isPlaying"]?.jsonPrimitive?.booleanOrNull == false) player.pause()
                        }
                    }
                }
            }
        }
    }

    fun selectedConnectDevice(): ConnectDevice? = _connectDevices.value.firstOrNull {
        it.id == _selectedConnectDeviceId.value
    }

    fun isControllingRemote(): Boolean = selectedConnectDevice()?.id?.let { it != ApiClient.getClientId() } == true

    fun selectConnectDevice(deviceId: String) {
        val target = _connectDevices.value.firstOrNull { it.id == deviceId } ?: return
        val current = selectedConnectDevice()
        val local = _connectDevices.value.firstOrNull { it.id == ApiClient.getClientId() }
        val source = current?.takeIf { it.state.track != null }
            ?: local?.takeIf { it.state.track != null }
            ?: _connectDevices.value.firstOrNull { it.state.isPlaying }
        if (source != null && source.id != target.id) {
            sendJson("connect:transfer", buildJsonObject {
                put("sourceDeviceId", source.id)
                put("targetDeviceId", target.id)
                put("commandId", "transfer_${System.currentTimeMillis()}")
            })
        }
        _selectedConnectDeviceId.value = target.id
    }

    fun sendCommandToSelected(command: String, payload: kotlinx.serialization.json.JsonObject = buildJsonObject {}): Boolean {
        val target = selectedConnectDevice()?.takeIf { it.id != ApiClient.getClientId() } ?: return false
        return sendJson("connect:command", buildJsonObject {
            put("targetDeviceId", target.id)
            put("commandId", "android_${System.currentTimeMillis()}_${command}")
            put("command", command)
            put("payload", payload)
        })
    }

    fun playTracksOnSelected(tracks: List<Track>, startIndex: Int): Boolean {
        val musicTracks = tracks.filter { it.id > 0 }.take(500)
        if (musicTracks.isEmpty()) return false
        val selectedId = tracks.getOrNull(startIndex)?.id
        val safeIndex = musicTracks.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        return sendCommandToSelected("play_tracks", buildJsonObject {
            put("tracks", buildJsonArray {
                musicTracks.forEach { add(json.encodeToJsonElement(it.toConnectTrack())) }
            })
            put("queueIndex", safeIndex)
            put("positionMs", 0)
            put("isPlaying", true)
        })
    }

    fun addTracksOnSelected(tracks: List<Track>): Boolean {
        val musicTracks = tracks.filter { it.id > 0 }.take(500)
        if (musicTracks.isEmpty()) return false
        return sendCommandToSelected("add_tracks", buildJsonObject {
            put("tracks", buildJsonArray {
                musicTracks.forEach { add(json.encodeToJsonElement(it.toConnectTrack())) }
            })
        })
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
