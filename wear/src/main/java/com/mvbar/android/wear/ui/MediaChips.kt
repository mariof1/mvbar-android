package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.mvbar.android.wear.net.Episode
import com.mvbar.android.wear.net.Track

@Composable
fun TrackChip(backend: Backend, track: Track, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = {
            val art = backend.artworkUrl(track.artPath)
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(WearTheme.Background),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = WearTheme.Cyan)
                }
            }
        },
        label = { Text(track.displayTitle, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(track.displayArtist, color = WearTheme.OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}

@Composable
fun EpisodeChip(backend: Backend, episode: Episode, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = {
            val art = backend.artworkUrl(episode.imagePath ?: episode.podcastImagePath)
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(WearTheme.Background),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(Icons.Default.Podcasts, contentDescription = null, tint = WearTheme.Orange)
                }
            }
        },
        label = { Text(episode.title, color = WearTheme.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            val parts = listOfNotNull(
                episode.podcastTitle?.takeIf { it.isNotBlank() },
                episode.durationFormatted.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (parts.isNotEmpty()) {
                Text(parts, color = WearTheme.OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}
