package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvbar.android.BuildConfig
import com.mvbar.android.ui.theme.*

@Composable
internal fun CompactUpdateButton(
    state: UpdateUiState,
    onClick: () -> Unit
) {
    val update = state.availableUpdate
    val busy = state.isChecking || state.isDownloading

    OutlinedButton(
        onClick = onClick,
        enabled = !state.isDownloading,
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (update != null) Orange400 else Cyan500)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = if (update != null) Orange400 else Cyan500,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                when {
                    update != null -> Icons.Filled.SystemUpdate
                    state.hasChecked -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Refresh
                },
                null,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            when {
                state.isChecking -> "Checking"
                state.isDownloading -> "Loading"
                update != null -> "Update"
                state.hasChecked -> "Latest"
                else -> "Check"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit
) {
    val update = state.availableUpdate
    val changelogScroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariantDark,
        titleContentColor = OnSurface,
        textContentColor = OnSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, null, tint = Cyan500)
                Spacer(Modifier.width(10.dp))
                Text("App Update")
            }
        },
        text = {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                when {
                    state.isChecking -> "Checking GitHub releases..."
                    update != null -> "Version ${update.version} is available."
                    state.hasChecked -> "You are on the latest version."
                    else -> "Current version ${BuildConfig.VERSION_NAME}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
            state.lastCheckedAt?.let { checkedAt ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Checked ${formatUpdateCheckedAt(checkedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }

            if (update != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = SurfaceDark)
                Spacer(Modifier.height(12.dp))

                Text(update.releaseName, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                Text(
                    "${update.assetName} - ${formatBytes(update.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )

                Spacer(Modifier.height(12.dp))
                Text("Changelog", style = MaterialTheme.typography.labelLarge, color = Cyan500)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .background(SurfaceDark, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .verticalScroll(changelogScroll)
                ) {
                    Text(
                        update.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            state.message?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = Cyan500)
            }

            state.error?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (state.installPermissionNeeded) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onOpenInstallSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange400)
                ) {
                    Icon(Icons.Filled.Security, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Allow APK Installs")
                }
            }
        }
        },
        confirmButton = {
            val busy = state.isChecking || state.isDownloading
            when {
                state.downloadedFile != null -> {
                    Button(
                        onClick = onInstall,
                        enabled = !busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                    ) {
                        Icon(Icons.Filled.InstallMobile, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Install", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
                update != null -> {
                    Button(
                        onClick = onDownload,
                        enabled = !busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                    ) {
                        if (state.isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Download, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.isDownloading) "Downloading..." else "Download",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                else -> {
                    if (state.error != null || (!state.hasChecked && !state.isChecking)) {
                        OutlinedButton(
                            onClick = onCheck,
                            enabled = !busy,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500)
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    state.isChecking -> "Checking..."
                                    state.error != null -> "Retry"
                                    else -> "Check"
                                }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OnSurfaceVariant)
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "unknown size"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

private fun formatUpdateCheckedAt(timestamp: Long): String {
    val elapsedMs = System.currentTimeMillis() - timestamp
    if (elapsedMs in 0 until 60_000) return "just now"
    if (elapsedMs in 60_000 until 60 * 60_000) {
        val minutes = elapsedMs / 60_000
        return "$minutes minute${if (minutes == 1L) "" else "s"} ago"
    }
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timestamp))
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        ),
        color = Cyan500,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
internal fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Cyan500,
                uncheckedThumbColor = OnSurfaceDim,
                uncheckedTrackColor = SurfaceDark
            )
        )
    }
}

