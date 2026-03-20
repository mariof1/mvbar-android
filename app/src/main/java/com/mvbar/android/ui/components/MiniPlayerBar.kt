package com.mvbar.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.theme.*

@Composable
fun MiniPlayerBar(
    state: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return
    val progress = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f
    val isPodcast = state.isPodcastMode
    val artUrl = if (isPodcast) {
        ApiClient.episodeArtUrl(-track.id)
    } else {
        track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
    }

    Column(modifier = modifier) {
        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = if (isPodcast) Orange500 else Cyan500,
            trackColor = WhiteOverlay10,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(SurfaceDark.copy(alpha = 0.95f), SurfaceDark)
                    )
                )
                .clickable(onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPodcast && onPrevious != null) {
                // -15s button for podcasts
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Text("-15", color = OnSurfaceDim, style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = OnSurface,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (isPodcast) {
                // +15s button for podcasts
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Text("+15", color = OnSurfaceDim, style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            } else {
                val hasNext = state.queueIndex < state.queue.size - 1
                IconButton(onClick = onNext, enabled = hasNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = if (hasNext) OnSurface else OnSurfaceDim.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
