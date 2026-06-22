package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mvbar.android.ui.theme.*
import com.mvbar.android.wearbridge.WearNode
import com.mvbar.android.wearbridge.WearPairingStatus
import com.mvbar.android.wearbridge.WearStatePublisher
import kotlinx.coroutines.launch

@Composable
internal fun WearOsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var watches by remember { mutableStateOf<List<WearNode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pushStatus by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        watches = WearPairingStatus.reachableWatches(context)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Pairing",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Spacer(Modifier.height(4.dp))
            when {
                loading -> Text(
                    "Looking for paired watches…",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
                watches.isEmpty() -> Text(
                    "No watches detected. Install mvbar on your watch and ensure it is paired via Wear OS / Galaxy Wearable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
                else -> {
                    watches.forEach { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Watch,
                                contentDescription = null,
                                tint = if (node.isNearby) Cyan500 else OnSurfaceDim,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    node.displayName.ifBlank { "Watch" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface
                                )
                                Text(
                                    if (node.isNearby) "Connected" else "Cloud-relayed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDim
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Push the current server URL and login token to the watch so it can stream and download independently.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        scope.launch {
                            WearStatePublisher.publishAuth(context)
                            pushStatus = "Pushed credentials to watch"
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Push to watch")
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { scope.launch { refresh() } }) {
                    Text("Refresh", color = Cyan500)
                }
            }
            pushStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Cyan500)
            }
        }
    }
}

