package com.mvbar.android.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun PairingScreen() {
    WearList {
        item {
            WearInfoPanel {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = WearTheme.Cyan,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        "Connect mvbar",
                        color = WearTheme.OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Send your server and account securely from the Android app.",
                        color = WearTheme.OnSurfaceDim,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item { SectionLabel("On your phone") }
        item {
            WearStatusChip(
                title = "1. Open mvbar",
                subtitle = "Keep the phone near your watch",
                icon = Icons.Default.PhoneAndroid
            )
        }
        item {
            WearStatusChip(
                title = "2. Open Settings",
                subtitle = "Choose the Wear OS section",
                icon = Icons.Default.Settings
            )
        }
        item {
            WearStatusChip(
                title = "3. Tap Send to watch",
                subtitle = "This screen will continue automatically",
                icon = Icons.AutoMirrored.Filled.SendToMobile,
                accent = WearTheme.Cyan
            )
        }

        item { Spacer(Modifier.height(4.dp)) }
        item {
            WearStatusChip(
                title = "Waiting for your phone",
                subtitle = "Checking for account access…",
                icon = Icons.Default.Sync,
                accent = WearTheme.Orange
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}
