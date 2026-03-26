package com.mvbar.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.SmartPlaylist
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.CreatePlaylistDialog
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist> = emptyList(),
    onPlaylistClick: (Int) -> Unit,
    onSmartPlaylistClick: (Int) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onCreateSmartPlaylist: () -> Unit = {},
    onRefresh: () -> Unit,
    history: List<Track> = emptyList(),
    currentTrackId: Int? = null,
    onPlayTrack: (Track) -> Unit = {},
    isHistoryLoading: Boolean = false,
    historyError: String? = null,
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null,
    hasMoreHistory: Boolean = false,
    isLoadingMoreHistory: Boolean = false,
    onLoadMoreHistory: () -> Unit = {}
) {
    LaunchedEffect(Unit) { onRefresh() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) } // 0=Recently Played, 1=Playlists, 2=Smart Playlists

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = onCreatePlaylist
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = currentTab == 0,
                onClick = { currentTab = 0 },
                label = { Text("Recently Played") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OnSurface,
                    selectedLabelColor = BackgroundDark,
                    containerColor = SurfaceElevated,
                    labelColor = OnSurface
                ),
                shape = RoundedCornerShape(50)
            )
            FilterChip(
                selected = currentTab == 1,
                onClick = { currentTab = 1 },
                label = { Text("Playlists") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OnSurface,
                    selectedLabelColor = BackgroundDark,
                    containerColor = SurfaceElevated,
                    labelColor = OnSurface
                ),
                shape = RoundedCornerShape(50)
            )
            FilterChip(
                selected = currentTab == 2,
                onClick = { currentTab = 2 },
                label = { Text("Smart") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OnSurface,
                    selectedLabelColor = BackgroundDark,
                    containerColor = SurfaceElevated,
                    labelColor = OnSurface
                ),
                shape = RoundedCornerShape(50)
            )
        }

        when (currentTab) {
            0 -> RecentlyPlayedTab(
                history = history,
                currentTrackId = currentTrackId,
                onPlayTrack = onPlayTrack,
                isLoading = isHistoryLoading,
                error = historyError,
                onTrackLongPress = onTrackLongPress,
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                hasMore = hasMoreHistory,
                isLoadingMore = isLoadingMoreHistory,
                onLoadMore = onLoadMoreHistory
            )
            1 -> PlaylistsTab(
                playlists = playlists,
                onPlaylistClick = onPlaylistClick,
                onCreatePlaylist = { showCreateDialog = true }
            )
            2 -> SmartPlaylistsTab(
                smartPlaylists = smartPlaylists,
                onSmartPlaylistClick = onSmartPlaylistClick,
                onCreateSmartPlaylist = onCreateSmartPlaylist
            )
        }
    }
}

@Composable
private fun RecentlyPlayedTab(
    history: List<Track>,
    currentTrackId: Int?,
    onPlayTrack: (Track) -> Unit,
    isLoading: Boolean,
    error: String?,
    onTrackLongPress: ((Track) -> Unit)?,
    favoriteIds: Set<Int>,
    onToggleFavorite: ((Int) -> Unit)?,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, hasMore, isLoadingMore, history.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && history.isNotEmpty() && lastVisible >= history.size - 6
        }.collect { if (it) onLoadMore() }
    }

    when {
        isLoading && history.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan500)
            }
        }
        error != null && history.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error, color = OnSurfaceDim)
            }
        }
        history.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No listening history yet", color = OnSurfaceDim)
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                items(history) { track ->
                    TrackListItem(
                        track = track.copy(isFavorite = track.id in favoriteIds),
                        isPlaying = track.id == currentTrackId,
                        onPlay = { onPlayTrack(track) },
                        onFavorite = onToggleFavorite?.let { { it(track.id) } },
                        onMore = onTrackLongPress?.let { { it(track) } },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    onPlaylistClick: (Int) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Create button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = onCreatePlaylist,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Cyan600,
                    contentColor = OnSurface
                ),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create")
            }
        }

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No playlists yet", color = OnSurfaceDim)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists) { playlist ->
                    PlaylistCard(
                        name = playlist.name,
                        subtitle = "${playlist.itemCount} tracks",
                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Cyan500, modifier = Modifier.size(32.dp)) },
                        accentColor = Cyan600,
                        onClick = { onPlaylistClick(playlist.id) }
                    )
                }
                items(2) { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
private fun SmartPlaylistsTab(
    smartPlaylists: List<SmartPlaylist>,
    onSmartPlaylistClick: (Int) -> Unit,
    onCreateSmartPlaylist: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = onCreateSmartPlaylist,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Pink500,
                    contentColor = OnSurface
                ),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create")
            }
        }

        if (smartPlaylists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No smart playlists yet", color = OnSurfaceDim)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(smartPlaylists) { sp ->
                    PlaylistCard(
                        name = sp.name,
                        subtitle = "Sort: ${sp.sort}",
                        icon = { Icon(Icons.Filled.AutoAwesome, null, tint = Pink500, modifier = Modifier.size(32.dp)) },
                        accentColor = Pink500,
                        onClick = { onSmartPlaylistClick(sp.id) }
                    )
                }
                items(2) { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    name: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                fontSize = 12.sp
            )
        }
    }
}
