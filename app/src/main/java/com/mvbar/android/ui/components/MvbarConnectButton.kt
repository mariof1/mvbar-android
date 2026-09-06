package com.mvbar.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.connect.ConnectDevice
import com.mvbar.android.ui.theme.Cyan400
import com.mvbar.android.ui.theme.Cyan500
import com.mvbar.android.ui.theme.OnSurface
import com.mvbar.android.ui.theme.OnSurfaceDim
import com.mvbar.android.ui.theme.OnSurfaceSubtle

@Composable
fun MvbarConnectButton(
    devices: List<ConnectDevice>,
    selectedDeviceId: String?,
    localDeviceId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDevices by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedDeviceId }
    BadgedBox(
        badge = {
            if (devices.size > 1) {
                Badge(containerColor = Cyan500) { Text(devices.size.toString()) }
            }
        },
        modifier = modifier
    ) {
        IconButton(onClick = { showDevices = true }) {
            Icon(
                Icons.Filled.Devices,
                contentDescription = "MVBar Connect players",
                tint = if (selected?.id != null && selected.id != localDeviceId) Cyan400 else OnSurfaceDim
            )
        }
    }

    if (showDevices) {
        AlertDialog(
            onDismissRequest = { showDevices = false },
            icon = { Icon(Icons.Filled.Devices, contentDescription = null, tint = Cyan400) },
            title = { Text("MVBar Connect") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Choose where playback happens",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    Spacer(Modifier.width(1.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(devices, key = { it.id }) { device ->
                            val isSelected = device.id == selectedDeviceId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Cyan500.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable {
                                        onSelect(device.id)
                                        showDevices = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Cyan500.copy(alpha = if (isSelected) 0.18f else 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Devices, contentDescription = null, tint = if (isSelected) Cyan400 else OnSurfaceDim)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            device.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) Cyan400 else OnSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (device.id == localDeviceId) {
                                            Text(
                                                "  THIS DEVICE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OnSurfaceSubtle
                                            )
                                        }
                                    }
                                    Text(
                                        device.state.track?.let {
                                            "${if (device.state.isPlaying) "Playing" else "Paused"} · ${it.title ?: "Untitled"}"
                                        } ?: listOfNotNull(device.type.uppercase(), device.platform).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                device.state.isPlaying -> androidx.compose.ui.graphics.Color(0xFF34D399)
                                                isSelected -> Cyan400
                                                else -> OnSurfaceSubtle
                                            }
                                        )
                                )
                            }
                        }
                        if (devices.isEmpty()) {
                            item {
                                Text(
                                    "Connecting this player…",
                                    color = OnSurfaceDim,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "Only players signed in to this MVBar account are visible.",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceSubtle,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevices = false }) { Text("Done") }
            }
        )
    }
}
