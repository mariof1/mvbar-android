package com.mvbar.android.wearbridge

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.mvbar.android.player.PlayerManager
import com.mvbar.android.shared.WearProtocol

/**
 * Receives play/pause/next/etc. commands from a paired Wear OS device
 * and forwards them to the phone's [PlayerManager].
 *
 * Registered via the manifest with an intent-filter on
 * `com.google.android.gms.wearable.MESSAGE_RECEIVED`, scheme `wear`,
 * pathPrefix `/mvbar/cmd/`.
 */
class WearCommandReceiver : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val pm = PlayerManager.getInstance(applicationContext)
        when (event.path) {
            WearProtocol.PATH_CMD_PLAY_PAUSE -> pm.togglePlay()
            WearProtocol.PATH_CMD_NEXT -> pm.next()
            WearProtocol.PATH_CMD_PREV -> pm.previous()
            WearProtocol.PATH_CMD_SEEK_FORWARD -> pm.skipForward()
            WearProtocol.PATH_CMD_SEEK_BACK -> pm.skipBackward()
            WearProtocol.PATH_CMD_FAVORITE -> {
                val cur = pm.state.value.isFavorite
                pm.setFavorite(!cur)
            }
        }
    }
}
