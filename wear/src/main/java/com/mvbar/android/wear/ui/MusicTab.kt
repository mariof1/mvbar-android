package com.mvbar.android.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.net.Playlist
import com.mvbar.android.wear.net.Track
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun MusicTab(
    backend: Backend,
    onOpenNowPlaying: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenSmart: () -> Unit,
    onOpenPlaylist: (Int, String) -> Unit,
    onOpenTrackList: (String, suspend () -> List<Track>) -> Unit
) {
    var openedSearch by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    if (openedSearch) {
        SearchScreen(backend, onBack = { openedSearch = false }, onOpenNowPlaying = onOpenNowPlaying)
        return
    }

    LaunchedEffect(Unit) {
        playlists = backend.playlists()
        loading = false
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            HubChip("Search", Icons.Default.Mic, WearTheme.Cyan, primary = true) { openedSearch = true }
        }
        item {
            HubChip("Recently played", Icons.Default.Schedule, WearTheme.Cyan) {
                onOpenTrackList("Recently played") { backend.history() }
            }
        }
        item {
            HubChip("Favorites", Icons.Default.Favorite, WearTheme.Pink) {
                onOpenTrackList("Favorites") { backend.favorites() }
            }
        }
        item {
            HubChip("Albums", Icons.Default.Album, WearTheme.Cyan) { onOpenAlbums() }
        }
        item {
            HubChip("Smart playlists", Icons.Default.AutoAwesome, WearTheme.Pink) { onOpenSmart() }
        }
        item {
            HubChip("Recent tracks", Icons.Default.History, WearTheme.Cyan) {
                onOpenTrackList("Recent tracks") { backend.recentTracks() }
            }
        }
        if (playlists.isNotEmpty()) {
            item {
                Text(
                    "Playlists",
                    color = WearTheme.OnSurfaceDim,
                    style = MaterialTheme.typography.caption2
                )
            }
            items(playlists) { pl ->
                Chip(
                    onClick = { onOpenPlaylist(pl.id, pl.name) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = WearTheme.Cyan) },
                    label = { Text(pl.name, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text("${pl.trackCount} tracks", color = WearTheme.OnSurfaceDim) }
                )
            }
        }
    }
}

@Composable
private fun HubChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary)
            ChipDefaults.primaryChipColors(backgroundColor = accent)
        else
            ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = { Icon(icon, contentDescription = null, tint = if (primary) WearTheme.OnSurface else accent) },
        label = { Text(label, color = WearTheme.OnSurface) }
    )
}

@Composable
fun SearchScreen(
    backend: Backend,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (text.isNotBlank()) query = text
    }

    LaunchedEffect(query) {
        tracks = if (query.isNotBlank()) backend.search(query).tracks else emptyList()
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.History, contentDescription = "Back", tint = WearTheme.Cyan) },
                label = { Text("Back", color = WearTheme.OnSurface) }
            )
        }
        item {
            Chip(
                onClick = {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search")
                    }
                    voiceLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Cyan),
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                label = { Text(if (query.isBlank()) "Speak…" else query, color = WearTheme.OnSurface) }
            )
        }
        items(tracks) { t ->
            TrackChip(backend, t, onClick = {
                val list = tracks.map { PlayableItem.Music(it) }
                val idx = tracks.indexOf(t).coerceAtLeast(0)
                WearPlayerHolder.playQueue(backend.context, list, idx)
                onOpenNowPlaying()
            })
        }
    }
}

@Composable
fun PlaylistTracksScreen(
    backend: Backend,
    playlistId: Int,
    title: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    TrackListScreen(
        backend = backend,
        title = title,
        loader = { backend.playlistTracks(playlistId) },
        onBack = onBack,
        onOpenNowPlaying = onOpenNowPlaying
    )
}
