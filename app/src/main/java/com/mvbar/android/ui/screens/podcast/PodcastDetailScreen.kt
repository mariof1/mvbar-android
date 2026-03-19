package com.mvbar.android.ui.screens.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Episode
import com.mvbar.android.data.model.Podcast
import com.mvbar.android.ui.theme.*

@Composable
fun PodcastDetailScreen(
    podcast: Podcast?,
    episodes: List<Episode>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onMarkPlayed: (Int, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    if (podcast == null && !isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Podcast not found", color = OnSurfaceDim)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Orange900.copy(alpha = 0.5f), BackgroundDark)
                        )
                    )
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                Column {
                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Podcast art
                        val artUrl = podcast?.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
                            ?: podcast?.let { ApiClient.podcastArtUrl(it.id) }

                        AsyncImage(
                            model = artUrl,
                            contentDescription = podcast?.title,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceElevated),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                podcast?.title ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            podcast?.author?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceDim
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = onRefresh,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = SurfaceElevated,
                                        contentColor = OnSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Refresh", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = onUnsubscribe,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    "Unsubscribe",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Orange500)
                }
            }
        } else if (episodes.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No episodes", color = OnSurfaceDim)
                }
            }
        } else {
            item {
                // Play all button
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${episodes.size} episodes",
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(episodes, key = { it.id }) { episode ->
                EpisodeListItem(
                    episode = episode,
                    showPodcastTitle = false,
                    onPlay = { onPlayEpisode(episode) },
                    onMarkPlayed = { onMarkPlayed(episode.id, !episode.played) }
                )
            }
        }
    }
}



