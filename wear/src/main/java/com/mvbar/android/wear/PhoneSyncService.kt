package com.mvbar.android.wear

import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataEvent
import com.mvbar.android.shared.WearProtocol

/**
 * Background receiver — wakes up when the phone publishes a new
 * now-playing snapshot even if our Activity is not in the foreground,
 * so a Tile / Complication update could later read the latest state.
 */
class PhoneSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path?.endsWith(WearProtocol.PATH_NOW_PLAYING) == true
            ) {
                // Reuse the in-process repository so the UI sees the update too.
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                NowPlayingRepository.attach(applicationContext)
                // The repository already listens; we just made sure it's started.
                @Suppress("UNUSED_VARIABLE")
                val snapshot = map.toNowPlayingState()
            }
        }
        events.release()
    }
}
