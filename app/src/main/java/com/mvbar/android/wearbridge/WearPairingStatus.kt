package com.mvbar.android.wearbridge

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.mvbar.android.shared.WearProtocol
import kotlinx.coroutines.tasks.await

data class WearNode(val id: String, val displayName: String, val isNearby: Boolean)

object WearPairingStatus {
    suspend fun reachableWatches(context: Context): List<WearNode> {
        val client = Wearable.getCapabilityClient(context.applicationContext)
        return runCatching {
            val info = client
                .getCapability(WearProtocol.CAPABILITY_WEAR_APP, CapabilityClient.FILTER_REACHABLE)
                .await()
            info.nodes.map { WearNode(it.id, it.displayName, it.isNearby) }
        }.getOrDefault(emptyList())
    }
}
