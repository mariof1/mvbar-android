package com.mvbar.android.wear.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.AuthTokenStore
import com.mvbar.android.wear.BuildConfig
import com.mvbar.android.wear.cache.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsConfirmation {
    ClearCache,
    SignOut
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableLongStateOf(0L) }
    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
    var isClearing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        bytes = withContext(Dispatchers.IO) { MediaCache.usageBytes(appContext) }
    }

    when (confirmation) {
        SettingsConfirmation.ClearCache -> {
            SettingsConfirmationScreen(
                title = "Clear offline audio?",
                message = "Downloaded and cached audio will be removed from this watch. Your mvbar library stays unchanged.",
                confirmLabel = "Clear audio",
                icon = Icons.Default.DeleteOutline,
                onDismiss = { confirmation = null },
                onConfirm = {
                    confirmation = null
                    isClearing = true
                    status = null
                    scope.launch {
                        withContext(Dispatchers.IO) { MediaCache.clear(appContext) }
                        bytes = withContext(Dispatchers.IO) { MediaCache.usageBytes(appContext) }
                        isClearing = false
                        status = "Offline audio removed"
                    }
                }
            )
            return
        }
        SettingsConfirmation.SignOut -> {
            SettingsConfirmationScreen(
                title = "Sign out?",
                message = "The watch will stop connecting to mvbar until account access is sent from the phone again.",
                confirmLabel = "Sign out",
                icon = Icons.AutoMirrored.Filled.Logout,
                onDismiss = { confirmation = null },
                onConfirm = {
                    confirmation = null
                    AuthTokenStore.save(appContext, "", "")
                    onSignOut()
                }
            )
            return
        }
        null -> Unit
    }

    val usedMb = bytes / (1024 * 1024)
    val maxMb = MediaCache.MAX_BYTES / (1024 * 1024)
    val usageFraction = (bytes.toFloat() / MediaCache.MAX_BYTES.toFloat()).coerceIn(0f, 1f)
    val serverUrl = AuthTokenStore.serverUrl(appContext).orEmpty()
    val serverName = remember(serverUrl) {
        runCatching { Uri.parse(serverUrl).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: serverUrl.removeSuffix("/").ifBlank { "Not connected" }
    }

    WearList {
        item { WearHeaderChip("Settings", "Watch preferences", onBack) }

        item { SectionLabel("Device audio") }
        item {
            SettingsActionChip(
                title = "Bluetooth audio",
                subtitle = "Pair earbuds or headphones",
                icon = Icons.Default.Bluetooth
            ) {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                runCatching { context.startActivity(intent) }
            }
        }
        item {
            SettingsActionChip(
                title = "Sound",
                subtitle = "Volume and audio settings",
                icon = Icons.AutoMirrored.Filled.VolumeUp
            ) {
                val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                runCatching { context.startActivity(intent) }
            }
        }

        item { SectionLabel("Downloads & storage") }
        item {
            WearInfoPanel {
                Column {
                    WearInfoText(
                        title = "Offline audio",
                        subtitle = "$usedMb MB used of ${maxMb / 1024} GB"
                    )
                    Spacer(Modifier.height(9.dp))
                    WearProgressBar(progress = usageFraction)
                }
            }
        }
        item {
            SettingsActionChip(
                title = if (isClearing) "Clearing audio…" else "Clear offline audio",
                subtitle = "Free storage used by downloads and playback cache",
                icon = Icons.Default.DeleteOutline,
                accent = WearTheme.Pink,
                enabled = !isClearing
            ) {
                confirmation = SettingsConfirmation.ClearCache
            }
        }
        status?.let { message ->
            item {
                WearStatusChip(
                    title = message,
                    subtitle = "Storage is ready",
                    icon = Icons.Default.Storage,
                    accent = WearTheme.Green
                )
            }
        }

        item { SectionLabel("Account") }
        item {
            WearStatusChip(
                title = "Connected server",
                subtitle = serverName,
                icon = Icons.Default.Dns,
                accent = WearTheme.Cyan
            )
        }
        item {
            SettingsActionChip(
                title = "Sign out",
                subtitle = "Remove mvbar access from this watch",
                icon = Icons.AutoMirrored.Filled.Logout,
                accent = WearTheme.Pink
            ) {
                confirmation = SettingsConfirmation.SignOut
            }
        }

        item {
            Text(
                "mvbar Wear ${BuildConfig.VERSION_NAME}",
                color = WearTheme.OnSurfaceSubtle,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun SettingsActionChip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color = WearTheme.Cyan,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = { Icon(icon, contentDescription = null, tint = accent) },
        label = {
            Text(
                title,
                color = WearTheme.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        secondaryLabel = {
            Text(
                subtitle,
                color = WearTheme.OnSurfaceDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption2
            )
        }
    )
}

@Composable
private fun SettingsConfirmationScreen(
    title: String,
    message: String,
    confirmLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WearList {
        item { WearHeaderChip(title, "Confirmation", onDismiss, WearTheme.Pink) }
        item {
            WearInfoPanel {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = WearTheme.Pink)
                    Text(
                        message,
                        color = WearTheme.OnSurfaceDim,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        item {
            Chip(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Pink),
                icon = { Icon(icon, contentDescription = null, tint = Color.Black) },
                label = { Text(confirmLabel, color = Color.Black, fontWeight = FontWeight.SemiBold) }
            )
        }
        item {
            Chip(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.Close, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text("Cancel", color = WearTheme.OnSurface) }
            )
        }
    }
}
