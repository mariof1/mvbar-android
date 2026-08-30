package com.mvbar.android.wear.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun QueueScreen(onBack: () -> Unit) {
    val state by WearPlayerHolder.state.collectAsState()

    WearList {
        item { WearHeaderChip("Queue", "${state.queue.size} items", onBack) }
        if (state.queue.isEmpty()) {
            item { EmptyChip("Queue is empty", "Start something from Library") }
        } else {
            itemsIndexed(state.queue) { index, item ->
                val isCurrent = index == state.index
                val accent = if (item.isPodcast) WearTheme.Orange else WearTheme.Cyan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Chip(
                        onClick = { WearPlayerHolder.seekToQueueIndex(index) },
                        modifier = Modifier.weight(1f),
                        colors = if (isCurrent) {
                            ChipDefaults.primaryChipColors(backgroundColor = accent.copy(alpha = 0.42f))
                        } else {
                            ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface)
                        },
                        icon = if (isCurrent) {
                            { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accent) }
                        } else null,
                        label = {
                            Text(
                                item.title,
                                color = WearTheme.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.caption1,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
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
                        Button(
                            onClick = { WearPlayerHolder.removeFromQueue(index) },
                            colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.SurfaceRaised),
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove ${item.title} from queue",
                                tint = WearTheme.Pink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
