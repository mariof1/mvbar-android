package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.cache.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CacheSettingsScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var bytes by remember { mutableLongStateOf(0L) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        bytes = withContext(Dispatchers.IO) { MediaCache.usageBytes(ctx) }
    }

    val mb = bytes / (1024 * 1024)
    val maxMb = MediaCache.MAX_BYTES / (1024 * 1024)

    ScalingLazyColumn(modifier = Modifier
        .fillMaxSize()
        .background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.Storage, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text("Cache", color = WearTheme.OnSurface) },
                secondaryLabel = { Text("$mb / $maxMb MB", color = WearTheme.OnSurfaceDim) }
            )
        }
        item {
            Chip(
                onClick = {
                    val app = ctx
                    Thread {
                        MediaCache.clear(app)
                        refreshKey++
                    }.start()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Pink),
                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                label = { Text("Clear cache", color = WearTheme.OnSurface) }
            )
        }
        item {
            Text(
                "Cache holds streamed and downloaded media. Older items are evicted automatically when the limit is reached.",
                color = WearTheme.OnSurfaceDim,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
