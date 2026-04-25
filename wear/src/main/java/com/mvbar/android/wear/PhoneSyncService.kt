package com.mvbar.android.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.mvbar.android.shared.WearProtocol

/**
 * Background receiver — wakes up when the phone publishes a new
 * now-playing snapshot or auth payload. Keeps the in-process
 * NowPlayingRepository fresh (so Tile reads see the latest state)
 * and forwards auth tokens into a private DataStore for any future
 * standalone playback work.
 */
class PhoneSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var nowPlayingChanged = false
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val path = event.dataItem.uri.path ?: return@forEach
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            when {
                path.endsWith(WearProtocol.PATH_NOW_PLAYING) -> {
                    NowPlayingRepository.attach(applicationContext)
                    nowPlayingChanged = true
                }
                path.endsWith(WearProtocol.PATH_AUTH) -> {
                    AuthTokenStore.save(
                        applicationContext,
                        token = map.getString(WearProtocol.KEY_AUTH_TOKEN).orEmpty(),
                        serverUrl = map.getString(WearProtocol.KEY_SERVER_URL).orEmpty()
                    )
                }
            }
        }
        events.release()
        if (nowPlayingChanged) {
            NowPlayingTileService.requestUpdate(applicationContext)
        }
    }
}

