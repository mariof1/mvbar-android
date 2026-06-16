package com.mvbar.android.ui.screens.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Episode
import com.mvbar.android.data.model.Podcast
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.theme.*

private enum class EpisodeFilter { ALL, UNPLAYED, PROGRESS }

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

    var filter by remember(podcast?.id) { mutableStateOf(EpisodeFilter.ALL) }
    val unplayedCount = episodes.count { !it.played }
    val progressCount = episodes.count { it.positionMs > 0 && !it.played }
    val filteredEpisodes = remember(episodes, filter) {
        when (filter) {
            EpisodeFilter.ALL -> episodes
            EpisodeFilter.UNPLAYED -> episodes.filter { !it.played }
            EpisodeFilter.PROGRESS -> episodes.filter { it.positionMs > 0 && !it.played }
        }
    }
    val continueEpisode = episodes.firstOrNull { it.positionMs > 0 && !it.played }
    val latestEpisode = episodes.firstOrNull { !it.played } ?: episodes.firstOrNull()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            PodcastDetailHeader(
                podcast = podcast,
                episodeCount = episodes.size,
                unplayedCount = unplayedCount,
                continueEpisode = continueEpisode,
                latestEpisode = latestEpisode,
                onPlayEpisode = onPlayEpisode,
                onRefresh = onRefresh,
                onUnsubscribe = onUnsubscribe
            )
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
                EpisodeFilterBar(
                    selected = filter,
                    totalCount = episodes.size,
                    unplayedCount = unplayedCount,
                    progressCount = progressCount,
                    onSelected = { filter = it }
                )
            }

            if (filteredEpisodes.isEmpty()) {
                item {
                    Text(
                        "Nothing here",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(filteredEpisodes, key = { it.id }) { episode ->
                    EpisodeListItem(
                        episode = episode,
                        showPodcastTitle = false,
                        showDescription = true,
                        onPlay = { onPlayEpisode(episode) },
                        onMarkPlayed = { onMarkPlayed(episode.id, !episode.played) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastDetailHeader(
    podcast: Podcast?,
    episodeCount: Int,
    unplayedCount: Int,
    continueEpisode: Episode?,
    latestEpisode: Episode?,
    onPlayEpisode: (Episode) -> Unit,
    onRefresh: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val artUrl = podcast?.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: podcast?.imageUrl
        ?: podcast?.let { ApiClient.podcastArtUrl(it.id) }
    val description = remember(podcast?.description) { cleanPodcastDescription(podcast?.description) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(WhiteOverlay10, BackgroundDark, BackgroundDark)
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            ArtworkImage(
                model = artUrl,
                contentDescription = podcast?.title,
                placeholderIcon = Icons.Filled.Podcasts,
                iconSize = 40.dp,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    podcast?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                podcast?.author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountPill("$episodeCount episodes")
                    if (unplayedCount > 0) CountPill("$unplayedCount unplayed", accent = true)
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, "More options", tint = OnSurfaceDim)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = SurfaceDark
                ) {
                    DropdownMenuItem(
                        text = { Text("Refresh", color = OnSurface) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = OnSurfaceDim) },
                        onClick = {
                            showMenu = false
                            onRefresh()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Unsubscribe", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onUnsubscribe()
                        }
                    )
                }
            }
        }

        if (description.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                description,
                color = OnSurfaceDim,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val primaryEpisode = continueEpisode ?: latestEpisode
            Button(
                onClick = { primaryEpisode?.let(onPlayEpisode) },
                enabled = primaryEpisode != null,
                colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = OnSurface),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (continueEpisode != null) "Continue" else "Play latest")
            }

            if (continueEpisode != null && latestEpisode != null && latestEpisode.id != continueEpisode.id) {
                OutlinedButton(
                    onClick = { onPlayEpisode(latestEpisode) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Latest", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CountPill(text: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (accent) Cyan600 else SurfaceElevated
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = OnSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeFilterBar(
    selected: EpisodeFilter,
    totalCount: Int,
    unplayedCount: Int,
    progressCount: Int,
    onSelected: (EpisodeFilter) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == EpisodeFilter.ALL,
            onClick = { onSelected(EpisodeFilter.ALL) },
            label = { Text("All $totalCount") },
            leadingIcon = if (selected == EpisodeFilter.ALL) {
                { Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = podcastFilterColors(),
            shape = RoundedCornerShape(50)
        )
        FilterChip(
            selected = selected == EpisodeFilter.UNPLAYED,
            onClick = { onSelected(EpisodeFilter.UNPLAYED) },
            label = { Text("Unplayed $unplayedCount") },
            colors = podcastFilterColors(),
            shape = RoundedCornerShape(50)
        )
        FilterChip(
            selected = selected == EpisodeFilter.PROGRESS,
            onClick = { onSelected(EpisodeFilter.PROGRESS) },
            label = { Text("In progress $progressCount") },
            colors = podcastFilterColors(),
            shape = RoundedCornerShape(50)
        )
    }
}

@Composable
private fun podcastFilterColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = OnSurface,
    selectedLabelColor = BackgroundDark,
    selectedLeadingIconColor = BackgroundDark,
    containerColor = SurfaceElevated,
    labelColor = OnSurface
)
