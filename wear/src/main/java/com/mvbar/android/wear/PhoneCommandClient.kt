package com.mvbar.android.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.mvbar.android.shared.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sends fire-and-forget commands to the phone app via the Wearable
 * Data Layer. Looks up the connected phone node lazily; if no phone is
 * reachable the call is dropped silently (UI just won't respond).
 */
class PhoneCommandClient(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)

    fun playPause() = send(WearProtocol.PATH_CMD_PLAY_PAUSE)
    fun next() = send(WearProtocol.PATH_CMD_NEXT)
    fun previous() = send(WearProtocol.PATH_CMD_PREV)
    fun seekForward() = send(WearProtocol.PATH_CMD_SEEK_FORWARD)
    fun seekBack() = send(WearProtocol.PATH_CMD_SEEK_BACK)
    fun toggleFavorite() = send(WearProtocol.PATH_CMD_FAVORITE)

    private fun send(path: String) {
        scope.launch {
            runCatching {
                val info = capabilityClient
                    .getCapability(
                        WearProtocol.CAPABILITY_PHONE_APP,
                        CapabilityClient.FILTER_REACHABLE
                    )
                    .await()
                val node = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
                if (node != null) {
                    messageClient.sendMessage(node.id, path, ByteArray(0)).await()
                }
            }
        }
    }
}
