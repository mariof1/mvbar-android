package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun QueueScreen(onBack: () -> Unit) {
    val state by WearPlayerHolder.state.collectAsState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WearTheme.Cyan) },
                label = { Text("Queue", color = WearTheme.OnSurface, fontWeight = FontWeight.SemiBold) },
                secondaryLabel = { Text("${state.queue.size} items", color = WearTheme.OnSurfaceDim) }
            )
        }
        itemsIndexed(state.queue) { idx, item ->
            val isCurrent = idx == state.index
            val accent = if (item.isPodcast) WearTheme.Orange else WearTheme.Cyan
            Chip(
                onClick = { WearPlayerHolder.seekToQueueIndex(idx) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isCurrent)
                    ChipDefaults.primaryChipColors(backgroundColor = accent.copy(alpha = 0.4f))
                else
                    ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = if (isCurrent) {
                    { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accent) }
                } else null,
                label = {
                    Text(
                        item.title,
                        color = WearTheme.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption1
                    )
                },
                secondaryLabel = {
                    Text(
                        item.subtitle,
                        color = WearTheme.OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption2
                    )
                }
            )
            if (!isCurrent) {
                Chip(
                    onClick = { WearPlayerHolder.removeFromQueue(idx) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Background),
                    icon = { Icon(Icons.Default.Close, contentDescription = null, tint = WearTheme.Pink) },
                    label = { Text("Remove", color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2) }
                )
            }
        }
    }
}
