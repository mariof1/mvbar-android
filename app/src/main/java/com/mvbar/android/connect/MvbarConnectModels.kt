package com.mvbar.android.connect

import com.mvbar.android.data.model.Track
import com.mvbar.android.player.PlayerState
import kotlinx.serialization.Serializable

@Serializable
data class ConnectTrack(
    val id: Int,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artPath: String? = null,
    val durationMs: Long? = null
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artPath = artPath,
        durationMs = durationMs?.toDouble()
    )
}

fun Track.toConnectTrack(): ConnectTrack = ConnectTrack(
    id = id,
    title = title,
    artist = displayArtist,
    album = album,
    artPath = artPath,
    durationMs = durationMs?.toLong()
)

@Serializable
data class ConnectDevicePlaybackState(
    val track: ConnectTrack? = null,
    val queue: List<ConnectTrack> = emptyList(),
    val queueIndex: Int = -1,
    val queueLength: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Double? = null,
    val updatedAt: Long = 0
) {
    val hasNext: Boolean get() = queueIndex >= 0 && queueIndex < queueLength - 1
}

@Serializable
data class ConnectDevice(
    val id: String,
    val name: String,
    val type: String = "unknown",
    val appVersion: String? = null,
    val platform: String? = null,
    val capabilities: List<String> = emptyList(),
    val state: ConnectDevicePlaybackState = ConnectDevicePlaybackState()
) {
    fun asRemotePlayerState(): PlayerState {
        val remoteQueue = state.queue.map { it.toTrack() }.ifEmpty {
            state.track?.let { listOf(it.toTrack()) }.orEmpty()
        }
        val remoteIndex = when {
            remoteQueue.isEmpty() -> -1
            state.queueIndex in remoteQueue.indices -> state.queueIndex
            else -> remoteQueue.indexOfFirst { it.id == state.track?.id }.coerceAtLeast(0)
        }
        return PlayerState(
            currentTrack = state.track?.toTrack() ?: remoteQueue.getOrNull(remoteIndex),
            isPlaying = state.isPlaying,
            position = state.positionMs,
            duration = state.durationMs,
            queue = remoteQueue,
            queueIndex = remoteIndex
        )
    }
}

@Serializable
data class ConnectDevicesPayload(val devices: List<ConnectDevice> = emptyList())

@Serializable
data class ConnectCommandPayload(
    val commandId: String = "",
    val sourceDeviceId: String = "",
    val command: String = "",
    val payload: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap())
)
