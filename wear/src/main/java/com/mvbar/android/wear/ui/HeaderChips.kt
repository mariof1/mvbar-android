package com.mvbar.android.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.NowPlayingRepository
import com.mvbar.android.wear.player.WearPlayerHolder

/**
 * Top-of-list header chips: a "Now playing" pill (when something is active)
 * and a Settings pill. Used in both Music and Podcasts tabs because the round
 * watch face clips the top corners — header chips inside the scaling list
 * are always reachable.
 */
@Composable
fun NowPlayingHeaderChip(onOpenNowPlaying: () -> Unit) {
    val local by WearPlayerHolder.state.collectAsState()
    val remote by NowPlayingRepository.state.collectAsState()
    val title = when {
        local.isActive -> local.item?.title.orEmpty()
        !remote.isEmpty -> remote.title
        else -> null
    }
    val subtitle = when {
        local.isActive -> local.item?.subtitle.orEmpty()
        !remote.isEmpty -> remote.artist
        else -> null
    }
    if (title == null) return

    val accent = when {
        local.isActive && local.item?.isPodcast == true -> WearTheme.Orange
        !remote.isEmpty && (remote.isPodcast || remote.isAudiobook) -> WearTheme.Orange
        else -> WearTheme.Cyan
    }

    Chip(
        onClick = onOpenNowPlaying,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors(backgroundColor = accent.copy(alpha = 0.6f)),
        icon = {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Now playing",
                tint = WearTheme.OnSurface,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                title,
                color = WearTheme.OnSurface,
                style = MaterialTheme.typography.caption1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = subtitle?.takeIf { it.isNotBlank() }?.let {
            {
                Text(
                    it,
                    color = WearTheme.OnSurface.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
fun SettingsHeaderChip(onOpenSettings: () -> Unit) {
    Chip(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = WearTheme.OnSurfaceDim,
                modifier = Modifier.size(16.dp)
            )
        },
        label = { Text("Settings", color = WearTheme.OnSurface, style = MaterialTheme.typography.caption2) }
    )
}
