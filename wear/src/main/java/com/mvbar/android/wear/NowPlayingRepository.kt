package com.mvbar.android.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.mvbar.android.shared.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Singleton holder for the now-playing state replicated from the phone
 * via the Wearable DataClient. Listens while at least one observer is
 * subscribed.
 */
object NowPlayingRepository : DataClient.OnDataChangedListener {

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private var attached = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attach(context: Context) {
        if (attached) return
        attached = true
        val client = Wearable.getDataClient(context.applicationContext)
        client.addListener(this)
        // Pull any existing snapshot the phone has already published.
        scope.launch {
            runCatching {
                val items = client.dataItems.await()
                items.forEach { item ->
                    if (item.uri.path?.endsWith(WearProtocol.PATH_NOW_PLAYING) == true) {
                        _state.value = DataMapItem.fromDataItem(item).dataMap.toNowPlayingState()
                    }
                }
                items.release()
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path?.endsWith(WearProtocol.PATH_NOW_PLAYING) == true
            ) {
                _state.value = DataMapItem.fromDataItem(event.dataItem).dataMap.toNowPlayingState()
            }
        }
        events.release()
    }
}
