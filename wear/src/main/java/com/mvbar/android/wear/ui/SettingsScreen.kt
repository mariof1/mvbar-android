package com.mvbar.android.wear.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.AuthTokenStore
import com.mvbar.android.wear.cache.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var bytes by remember { mutableLongStateOf(0L) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        bytes = withContext(Dispatchers.IO) { MediaCache.usageBytes(ctx) }
    }

    val mb = bytes / (1024 * 1024)
    val maxMb = MediaCache.MAX_BYTES / (1024 * 1024)

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WearTheme.Cyan) },
                label = { Text("Settings", color = WearTheme.OnSurface) }
            )
        }
        item {
            Chip(
                onClick = {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text("Bluetooth", color = WearTheme.OnSurface) },
                secondaryLabel = { Text("Pair earbuds / headphones", color = WearTheme.OnSurfaceDim) }
            )
        }
        item {
            Chip(
                onClick = {
                    val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.VolumeUp, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text("Sound", color = WearTheme.OnSurface) }
            )
        }
        item {
            Chip(
                onClick = { /* readonly */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.Storage, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text("Cache", color = WearTheme.OnSurface) },
                secondaryLabel = { Text("$mb / $maxMb MB", color = WearTheme.OnSurfaceDim) }
            )
        }
        item {
            Chip(
                onClick = { Thread { MediaCache.clear(ctx); refreshKey++ }.start() },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Background),
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = WearTheme.Pink) },
                label = { Text("Clear cache", color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2) }
            )
        }
        item {
            Chip(
                onClick = {
                    AuthTokenStore.save(ctx, "", "")
                    onSignOut()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Pink),
                icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                label = { Text("Sign out", color = WearTheme.OnSurface) }
            )
        }
        item {
            Text(
                "mvbar wear • streams + caches up to 2 GB",
                color = WearTheme.OnSurfaceDim,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
