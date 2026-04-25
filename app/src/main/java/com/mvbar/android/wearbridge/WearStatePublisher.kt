package com.mvbar.android.wearbridge

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.player.PlayerManager
import com.mvbar.android.player.PlayerState
import com.mvbar.android.shared.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Mirrors the phone PlayerState onto the Wearable DataClient so paired
 * watches can show a now-playing UI. Idempotent.
 */
object WearStatePublisher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastPublishedToken: String? = null
    private var lastPublishedServer: String? = null

    fun start(context: Context) {
        if (job != null) return
        val app = context.applicationContext
        val client = Wearable.getDataClient(app)
        val pm = PlayerManager.getInstance(app)
        job = scope.launch {
            pm.state.collectLatest { state ->
                runCatching {
                    publishAuthIfChanged(client)
                    publish(client, state)
                }
            }
        }
    }

    /** Force-publish auth credentials (e.g. just after login). */
    fun publishAuth(context: Context) {
        scope.launch {
            runCatching {
                publishAuthIfChanged(Wearable.getDataClient(context.applicationContext))
            }
        }
    }

    private suspend fun publishAuthIfChanged(client: DataClient) {
        val token = ApiClient.getToken()
        val server = ApiClient.getBaseUrl()
        if (token == lastPublishedToken && server == lastPublishedServer) return
        lastPublishedToken = token
        lastPublishedServer = server
        val req = PutDataMapRequest.create(WearProtocol.PATH_AUTH).apply {
            dataMap.putString(WearProtocol.KEY_AUTH_TOKEN, token.orEmpty())
            dataMap.putString(WearProtocol.KEY_SERVER_URL, server)
            dataMap.putLong(WearProtocol.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        client.putDataItem(req).await()
    }

    private suspend fun publish(client: DataClient, state: PlayerState) {
        val req = PutDataMapRequest.create(WearProtocol.PATH_NOW_PLAYING).apply {
            val track = state.currentTrack
            dataMap.putString(WearProtocol.KEY_TITLE, track?.title.orEmpty())
            dataMap.putString(WearProtocol.KEY_ARTIST, track?.artist.orEmpty())
            dataMap.putString(WearProtocol.KEY_ALBUM, track?.album.orEmpty())
            dataMap.putLong(WearProtocol.KEY_DURATION_MS, state.duration)
            dataMap.putLong(WearProtocol.KEY_POSITION_MS, state.position)
            dataMap.putBoolean(WearProtocol.KEY_IS_PLAYING, state.isPlaying)
            dataMap.putBoolean(WearProtocol.KEY_IS_PODCAST, state.isPodcastMode)
            dataMap.putBoolean(WearProtocol.KEY_IS_AUDIOBOOK, state.isAudiobookMode)
            dataMap.putBoolean(WearProtocol.KEY_FAVORITE, state.isFavorite)
            state.artworkUrl?.let { dataMap.putString(WearProtocol.KEY_ARTWORK, it) }
            dataMap.putLong(WearProtocol.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        client.putDataItem(req).await()
    }
}


