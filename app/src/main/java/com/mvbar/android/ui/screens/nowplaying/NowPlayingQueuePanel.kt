package com.mvbar.android.ui.screens.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Episode
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.SmartPlaylist
import com.mvbar.android.data.model.Track
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.player.PlayMode
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.components.GlowingProgressLine
import com.mvbar.android.ui.theme.*

private enum class QueueTab(val label: String) {
    QUEUE("Queue"),
    ALL("All"),
    PLAYLISTS("Playlists"),
    PODCASTS("Podcasts"),
    FAVOURITES("Favourites")
}

@Composable
internal fun QueuePanelContent(
    state: PlayerState,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    favorites: List<Track>,
    podcastContinueListening: List<Episode>,
    playlistTracks: List<Track>,
    playlistTracksLoading: Boolean,
    smartPlaylistTracks: List<Track>,
    smartPlaylistTracksLoading: Boolean,
    allTracks: List<Track>,
    allTracksLoading: Boolean,
    hasMoreAllTracks: Boolean,
    onLoadAllTracks: () -> Unit,
    onLoadMoreAllTracks: () -> Unit,
    onShuffleAllTracks: (Track?) -> Unit,
    onPlayPodcastEpisode: (Episode) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onLoadPlaylistTracks: (Int) -> Unit,
    onLoadSmartPlaylistTracks: (Int) -> Unit,
    onPlayTrackWithQueue: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(QueueTab.QUEUE) }
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var selectedSmartPlaylistId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        // Tab chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QueueTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        selectedPlaylistId = null
                        selectedSmartPlaylistId = null
                    },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = Cyan500.copy(alpha = 0.2f),
                        labelColor = OnSurfaceDim,
                        selectedLabelColor = Cyan500
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = WhiteOverlay15,
                        selectedBorderColor = Cyan500.copy(alpha = 0.4f),
                        enabled = true,
                        selected = selectedTab == tab
                    ),
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 12.dp))

        when (selectedTab) {
            QueueTab.QUEUE -> {
                // Queue header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.queue.size} tracks", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                    if (state.queue.isNotEmpty()) {
                        TextButton(onClick = onClearQueue) {
                            Text("Clear", color = Pink500, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Auto-scroll to currently playing track
                val queueListState = rememberLazyListState()
                LaunchedEffect(state.queueIndex) {
                    if (state.queue.isNotEmpty() && state.queueIndex in state.queue.indices) {
                        queueListState.animateScrollToItem(state.queueIndex)
                    }
                }

                if (state.queue.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Queue is empty", color = OnSurfaceDim)
                    }
                } else {
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(state.queue, key = { index, t -> "q_${index}_${t.id}" }) { index, qTrack ->
                            val isActive = index == state.queueIndex
                            val isPast = index < state.queueIndex
                            Box(modifier = Modifier.graphicsLayer { alpha = if (isPast) 0.5f else 1f }) {
                                QueueItem(track = qTrack, isActive = isActive,
                                    onPlay = { onPlayQueueItem(index) },
                                    onRemove = { onRemoveFromQueue(index) })
                            }
                        }
                    }
                }
            }

            QueueTab.ALL -> {
                // Load first page when tab is first selected
                LaunchedEffect(Unit) {
                    if (allTracks.isEmpty() && !allTracksLoading) onLoadAllTracks()
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${allTracks.size}${if (hasMoreAllTracks) "+" else ""} tracks",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim
                    )
                    TextButton(
                        onClick = { onShuffleAllTracks(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, null, tint = Cyan500, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle All", color = Cyan500, style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (allTracksLoading && allTracks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                    }
                } else if (allTracks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No tracks", color = OnSurfaceDim)
                    }
                } else {
                    val allListState = rememberLazyListState()

                    // Infinite scroll using snapshotFlow — re-launches when list size or hasMore changes
                    LaunchedEffect(allListState, hasMoreAllTracks, allTracks.size) {
                        snapshotFlow {
                            val lastVisible = allListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            hasMoreAllTracks && lastVisible >= allTracks.size - 20
                        }.collect { shouldLoad ->
                            if (shouldLoad) onLoadMoreAllTracks()
                        }
                    }

                    LazyColumn(
                        state = allListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(allTracks, key = { _, t -> "all_${t.id}" }) { index, track ->
                            val isPlaying = track.id == state.currentTrack?.id
                            QueueItem(track = track, isActive = isPlaying,
                                onPlay = {
                                    if (state.playMode == PlayMode.SHUFFLE) {
                                        // In shuffle mode, fetch a random batch from server
                                        onShuffleAllTracks(track)
                                    } else {
                                        // Normal mode: queue a window of ~200 tracks around selection
                                        val windowStart = (index - 100).coerceAtLeast(0)
                                        val windowEnd = (index + 100).coerceAtMost(allTracks.size)
                                        val window = allTracks.subList(windowStart, windowEnd)
                                        onPlayTrackWithQueue(track, window)
                                    }
                                },
                                onRemove = {}, showRemove = false)
                        }
                        if (hasMoreAllTracks) {
                            item(key = "all_loading") {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }

            QueueTab.PLAYLISTS -> {
                when {
                    selectedSmartPlaylistId != null -> {
                        // Track list for selected smart playlist
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedSmartPlaylistId = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                smartPlaylists.firstOrNull { it.id == selectedSmartPlaylistId }?.name ?: "Smart Playlist",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (smartPlaylistTracksLoading) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                            }
                        } else if (smartPlaylistTracks.isEmpty()) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("No tracks", color = OnSurfaceDim)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                                itemsIndexed(smartPlaylistTracks, key = { _, t -> "sp_${t.id}" }) { _, track ->
                                    QueueItem(track = track, isActive = track.id == state.currentTrack?.id,
                                        onPlay = { onPlayTrackWithQueue(track, smartPlaylistTracks) },
                                        onRemove = {}, showRemove = false)
                                }
                            }
                        }
                    }

                    selectedPlaylistId != null -> {
                        // Track list for selected playlist
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedPlaylistId = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                playlists.firstOrNull { it.id == selectedPlaylistId }?.name ?: "Playlist",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (playlistTracksLoading) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                            }
                        } else if (playlistTracks.isEmpty()) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("No tracks", color = OnSurfaceDim)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                                itemsIndexed(playlistTracks, key = { _, t -> "pl_${t.id}" }) { _, track ->
                                    QueueItem(track = track, isActive = track.id == state.currentTrack?.id,
                                        onPlay = { onPlayTrackWithQueue(track, playlistTracks) },
                                        onRemove = {}, showRemove = false)
                                }
                            }
                        }
                    }

                    playlists.isEmpty() && smartPlaylists.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No playlists", color = OnSurfaceDim)
                        }
                    }

                    else -> {
                        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                            if (playlists.isNotEmpty()) {
                                item(key = "playlist_header") {
                                    Text(
                                        "Playlists",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceDim,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(playlists, key = { playlist -> "playlist_${playlist.id}" }) { playlist ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                selectedPlaylistId = playlist.id
                                                onLoadPlaylistTracks(playlist.id)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Cyan500, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(playlist.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${playlist.itemCount} tracks", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                                        }
                                        Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceDim, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            if (smartPlaylists.isNotEmpty()) {
                                item(key = "smart_playlist_header") {
                                    Text(
                                        "Smart Playlists",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceDim,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(smartPlaylists, key = { sp -> "smart_${sp.id}" }) { sp ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                selectedSmartPlaylistId = sp.id
                                                onLoadSmartPlaylistTracks(sp.id)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, null, tint = Pink500, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sp.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Smart • ${sp.sort}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                                        }
                                        Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceDim, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            QueueTab.PODCASTS -> {
                if (podcastContinueListening.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No episodes in progress", color = OnSurfaceDim)
                            Text(
                                "Continue listening episodes appear here",
                                color = OnSurfaceSubtle,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(podcastContinueListening, key = { episode -> "podcast_${episode.id}" }) { episode ->
                            PodcastQueueItem(
                                episode = episode,
                                isActive = state.currentTrack?.id == -episode.id,
                                onPlay = { onPlayPodcastEpisode(episode) }
                            )
                        }
                    }
                }
            }

            QueueTab.FAVOURITES -> {
                val favListState = rememberLazyListState()
                // Auto-scroll to currently playing track in favorites
                LaunchedEffect(state.currentTrack?.id) {
                    val idx = favorites.indexOfFirst { it.id == state.currentTrack?.id }
                    if (idx >= 0) favListState.animateScrollToItem(idx)
                }

                if (favorites.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No favourites", color = OnSurfaceDim)
                    }
                } else {
                    LazyColumn(
                        state = favListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(favorites, key = { _, t -> "fav_${t.id}" }) { _, track ->
                            val isPlaying = track.id == state.currentTrack?.id
                            QueueItem(track = track, isActive = isPlaying,
                                onPlay = { onPlayTrackWithQueue(track, favorites) },
                                onRemove = {}, showRemove = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastQueueItem(
    episode: Episode,
    isActive: Boolean,
    onPlay: () -> Unit
) {
    val isOnline = LocalIsOnline.current
    val isPlayable = remember(episode.id, isOnline) {
        isOnline || AudioCacheManager.isEpisodeCached(episode.id)
    }
    val artModel = episode.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: episode.podcastImagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: ApiClient.episodeArtUrl(episode.id)
    val bgColor = if (isActive) Orange500.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isPlayable) 1f else 0.38f }
            .background(bgColor)
            .clickable(enabled = isPlayable, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            model = artModel,
            contentDescription = null,
            placeholderIcon = Icons.Filled.Podcasts,
            iconSize = 22.dp,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                episode.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) Orange400 else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                episode.podcastTitle ?: "Podcast",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (episode.durationFormatted.isNotEmpty()) {
                    Text(episode.durationFormatted, style = MaterialTheme.typography.labelSmall, color = OnSurfaceSubtle)
                }
                if (episode.positionMs > 0 && !episode.played) {
                    Text("${episode.progressPercent}% played", style = MaterialTheme.typography.labelSmall, color = Orange400)
                }
            }
            if (episode.positionMs > 0 && !episode.played && episode.durationMs != null) {
                Spacer(Modifier.height(4.dp))
                GlowingProgressLine(
                    progress = episode.progressPercent / 100f,
                    accent = Orange500,
                    accentHighlight = Orange400,
                    heightDp = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Icon(Icons.Filled.PlayArrow, "Play", tint = Orange500, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun QueueItem(
    track: Track,
    isActive: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    showRemove: Boolean = true
) {
    val isOnline = LocalIsOnline.current
    val isPlayable = remember(track.id, isOnline) {
        isOnline || if (track.id > 0) AudioCacheManager.isTrackCached(track.id)
        else AudioCacheManager.isEpisodeCached(-track.id)
    }
    val bgColor = if (isActive) Cyan500.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isPlayable) 1f else 0.38f }
            .background(bgColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Cyan400)
            )
            Spacer(Modifier.width(10.dp))
        }

        val queueArtModel = if (track.id < 0) {
            ApiClient.episodeArtUrl(-track.id)
        } else {
            track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
        }
        ArtworkImage(
            model = queueArtModel,
            contentDescription = null,
            placeholderIcon = Icons.Filled.MusicNote,
            iconSize = 20.dp,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) Cyan400 else OnSurface,
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

        Text(
            track.durationFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDim
        )

        if (showRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, "Remove", tint = OnSurfaceDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

