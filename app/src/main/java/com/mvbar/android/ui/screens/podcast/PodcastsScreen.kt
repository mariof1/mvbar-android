package com.mvbar.android.ui.screens.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Episode
import com.mvbar.android.data.model.Podcast
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.theme.*

@Composable
fun PodcastsScreen(
    podcasts: List<Podcast>,
    continueListening: List<Episode>,
    isLoading: Boolean,
    onPodcastClick: (Podcast) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onMarkPlayed: (Int, Boolean) -> Unit,
    onSubscribeClick: () -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    var currentView by remember { mutableStateOf("new") } // "new" or "subscriptions"

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips + Subscribe button
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentView == "new",
                onClick = { currentView = "new" },
                label = { Text("Continue Listening") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OnSurface,
                    selectedLabelColor = BackgroundDark,
                    containerColor = SurfaceElevated,
                    labelColor = OnSurface
                ),
                shape = RoundedCornerShape(50)
            )
            FilterChip(
                selected = currentView == "subscriptions",
                onClick = { currentView = "subscriptions" },
                label = { Text("Subscriptions") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OnSurface,
                    selectedLabelColor = BackgroundDark,
                    containerColor = SurfaceElevated,
                    labelColor = OnSurface
                ),
                shape = RoundedCornerShape(50)
            )
            FilledTonalButton(
                onClick = onSubscribeClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Cyan600,
                    contentColor = OnSurface
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Subscribe")
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange500)
            }
        } else if (currentView == "new") {
            // Continue Listening
            if (continueListening.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No episodes in progress", color = OnSurfaceDim)
                        Text(
                            "Subscribe to podcasts to see episodes here",
                            color = OnSurfaceSubtle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                    items(continueListening, key = { it.id }) { episode ->
                        EpisodeListItem(
                            episode = episode,
                            onPlay = { onEpisodePlay(episode) },
                            onMarkPlayed = { onMarkPlayed(episode.id, !episode.played) }
                        )
                    }
                }
            }
        } else {
            // Subscriptions grid
            if (podcasts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No subscriptions yet", color = OnSurfaceDim)
                        Text(
                            "Click \"Subscribe\" to add a podcast",
                            color = OnSurfaceSubtle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp,
                        top = 8.dp, bottom = 140.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(podcasts, key = { it.id }) { podcast ->
                        PodcastGridItem(
                            podcast = podcast,
                            onClick = { onPodcastClick(podcast) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastGridItem(podcast: Podcast, onClick: () -> Unit) {
    val artUrl = podcast.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: ApiClient.podcastArtUrl(podcast.id)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated)
        ) {
            ArtworkImage(
                model = artUrl,
                contentDescription = podcast.title,
                placeholderIcon = Icons.Filled.Podcasts,
                iconSize = 32.dp,
                modifier = Modifier.fillMaxSize()
            )
            // Unplayed badge
            if (podcast.unplayedCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = Cyan600
                ) {
                    Text(
                        "${podcast.unplayedCount}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = OnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            podcast.title,
            color = OnSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        podcast.author?.let {
            Text(
                it,
                color = OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EpisodeListItem(
    episode: Episode,
    showPodcastTitle: Boolean = true,
    onPlay: () -> Unit,
    onMarkPlayed: () -> Unit
) {
    val isOnline = LocalIsOnline.current
    val isPlayable = remember(episode.id, isOnline) {
        isOnline || AudioCacheManager.isEpisodeCached(episode.id)
    }

    val artUrl = episode.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: episode.podcastImagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: ApiClient.episodeArtUrl(episode.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isPlayable) 1f else 0.38f }
            .clickable(enabled = isPlayable, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(if (episode.played) Modifier.background(BackgroundDark.copy(alpha = 0.6f)) else Modifier),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Episode art with play overlay
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            ArtworkImage(
                model = artUrl,
                contentDescription = null,
                placeholderIcon = Icons.Filled.Podcasts,
                iconSize = 24.dp,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(50),
                color = BackgroundDark.copy(alpha = 0.7f)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    "Play",
                    modifier = Modifier.padding(4.dp),
                    tint = OnSurface
                )
            }
        }

        // Episode info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                episode.title,
                color = if (episode.played) OnSurfaceDim else OnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (showPodcastTitle && episode.podcastTitle != null) {
                Text(
                    episode.podcastTitle,
                    color = Cyan400,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (episode.publishedFormatted.isNotEmpty()) {
                    Text(
                        episode.publishedFormatted,
                        color = OnSurfaceSubtle,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (episode.durationFormatted.isNotEmpty()) {
                    Text(
                        episode.durationFormatted,
                        color = OnSurfaceSubtle,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (episode.positionMs > 0 && !episode.played) {
                    Text(
                        "${episode.progressPercent}% played",
                        color = Cyan400,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Progress bar
            if (episode.positionMs > 0 && !episode.played && episode.durationMs != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { episode.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Orange500,
                    trackColor = WhiteOverlay10
                )
            }
        }

        // Mark played button
        IconButton(
            onClick = onMarkPlayed,
            modifier = Modifier.size(32.dp)
        ) {
            if (episode.played) {
                Icon(
                    Icons.Filled.CheckCircle,
                    "Mark unplayed",
                    tint = Orange500,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    Icons.Filled.Podcasts,
                    "Mark played",
                    tint = OnSurfaceSubtle,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

