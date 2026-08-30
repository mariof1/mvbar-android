package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val hasDownloadedApk = state.downloadedFile?.exists() == true
    val needsInstallPermission = state.installPermissionNeeded
    val accent = if (needsInstallPermission || hasDownloadedApk || update != null) Orange400 else Cyan500

    OutlinedButton(
        onClick = onClick,
        enabled = !state.isDownloading,
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = accent,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                when {
                    needsInstallPermission -> Icons.Filled.Security
                    hasDownloadedApk -> Icons.Filled.InstallMobile
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
                needsInstallPermission -> "Permit"
                hasDownloadedApk -> "Install"
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
                    state.installPermissionNeeded -> "Android needs permission before mvbar can install the downloaded APK."
                    state.downloadedFile != null && update != null -> "Version ${update.version} is downloaded and ready to install."
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
                    "${update.assetName} - ${formatBytes(update.sizeBytes)}${if (state.downloadedFile != null) " - downloaded" else ""}",
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

            if (update == null && state.hasChecked && state.error == null && !state.isChecking) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Settings checks for updates automatically while it is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
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
internal fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        border = BorderStroke(1.dp, WhiteOverlay10)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Cyan500.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Cyan400, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingsRowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(WhiteOverlay5, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun SettingsDivider(indented: Boolean = true) {
    HorizontalDivider(
        modifier = if (indented) Modifier.padding(start = 48.dp) else Modifier,
        color = WhiteOverlay10
    )
}

@Composable
internal fun SettingsValueBadge(text: String, color: Color = Cyan400) {
    Surface(
        color = color.copy(alpha = 0.13f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
internal fun SettingsConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        title = { Text(title, color = OnSurface) },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (destructive) MaterialTheme.colorScheme.error else Cyan500,
                    contentColor = if (destructive) Color.White else Color.Black
                )
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurfaceVariant)
            }
        }
    )
}

@Composable
internal fun SettingsSlider(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        SettingsInfoRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            trailing = { SettingsValueBadge(valueLabel) }
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(horizontal = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = Cyan400,
                activeTrackColor = Cyan500,
                inactiveTrackColor = WhiteOverlay10
            )
        )
    }
}

@Composable
internal fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Cyan500,
                uncheckedThumbColor = OnSurfaceDim,
                uncheckedTrackColor = SurfaceDark
            )
        )
    }
}
