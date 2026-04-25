package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.mvbar.android.wear.net.Episode
import com.mvbar.android.wear.net.Podcast
import com.mvbar.android.wear.net.Track
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun PodcastsTab(backend: Backend, onOpenNowPlaying: () -> Unit) {
    var podcasts by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var newEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        newEpisodes = backend.newEpisodes()
        podcasts = backend.podcasts()
        loading = false
    }

    var openedPodcastId by remember { mutableStateOf<Int?>(null) }
    val opened = openedPodcastId
    if (opened != null) {
        EpisodesScreen(backend, opened, onBack = { openedPodcastId = null }, onOpenNowPlaying = onOpenNowPlaying)
        return
    }

    ScalingLazyColumn(modifier = Modifier
        .fillMaxSize()
        .background(WearTheme.Background)) {
        if (loading) {
            item { Text("Loading…", color = WearTheme.OnSurfaceDim) }
        } else {
            item {
                Text(
                    "Latest episodes",
                    color = WearTheme.OnSurfaceDim,
                    style = MaterialTheme.typography.caption2,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
            items(newEpisodes.take(10)) { ep ->
                EpisodeChip(backend, ep, onClick = {
                    WearPlayerHolder.play(backend.context, PlayableItem.PodcastEp(ep))
                    onOpenNowPlaying()
                })
            }
            item {
                Text(
                    "Subscribed",
                    color = WearTheme.OnSurfaceDim,
                    style = MaterialTheme.typography.caption2,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }
            items(podcasts) { p ->
                PodcastChip(backend, p, onClick = { openedPodcastId = p.id })
            }
        }
    }
}

@Composable
private fun PodcastChip(backend: Backend, podcast: Podcast, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = {
            val art = backend.artworkUrl(podcast.imagePath ?: podcast.imageUrl)
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WearTheme.Background)
                )
            }
        },
        label = {
            Text(
                podcast.title,
                color = WearTheme.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = if (podcast.unplayedCount > 0) {
            { Text("${podcast.unplayedCount} new", color = WearTheme.Orange) }
        } else null
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
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WearTheme.Background)
                )
            }
        },
        label = {
            Text(
                episode.title,
                color = WearTheme.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
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

@Composable
fun TrackChip(backend: Backend, track: Track, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = {
            val art = backend.artworkUrl(track.artPath)
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WearTheme.Background)
                )
            }
        },
        label = { Text(track.displayTitle, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(track.displayArtist, color = WearTheme.OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}
