package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    SettingsSectionCard(
        title = "Wear OS",
        subtitle = "Pair your watch for independent streaming and downloads",
        icon = Icons.Filled.Watch
    ) {
        when {
            loading -> SettingsInfoRow(
                icon = Icons.Filled.Sync,
                title = "Looking for watches",
                subtitle = "Checking nearby and cloud-connected Wear OS devices"
            )
            watches.isEmpty() -> SettingsInfoRow(
                icon = Icons.Filled.Watch,
                title = "No watch detected",
                subtitle = "Install mvbar on your watch and pair it through Wear OS or Galaxy Wearable"
            )
            else -> {
                watches.forEachIndexed { index, node ->
                    if (index > 0) SettingsDivider()
                    SettingsInfoRow(
                        icon = Icons.Filled.Watch,
                        title = node.displayName.ifBlank { "Wear OS watch" },
                        subtitle = if (node.isNearby) {
                            "Connected directly to this phone"
                        } else {
                            "Available through cloud relay"
                        },
                        trailing = {
                            SettingsValueBadge(
                                text = if (node.isNearby) "Nearby" else "Cloud",
                                color = if (node.isNearby) Cyan400 else Orange400
                            )
                        }
                    )
                }
            }
        }

        SettingsDivider(indented = false)
        Text(
            "Send the current server and login securely to your watch so it can work away from the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim,
            lineHeight = 17.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        WearStatePublisher.publishAuth(context)
                        pushStatus = "Credentials sent to your watch"
                        refresh()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan500.copy(alpha = 0.16f),
                    contentColor = Cyan400
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.SendToMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send to watch")
            }
            OutlinedButton(
                onClick = { scope.launch { refresh() } },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }
        pushStatus?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Cyan400)
        }
    }
}
