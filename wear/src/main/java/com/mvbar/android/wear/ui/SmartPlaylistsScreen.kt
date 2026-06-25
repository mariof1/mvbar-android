package com.mvbar.android.wear.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.net.SmartPlaylistInfo
import com.mvbar.android.wear.net.Track
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun SmartPlaylistsScreen(
    backend: Backend,
    onBack: () -> Unit,
    onOpen: (Int, String) -> Unit
) {
    var items by remember { mutableStateOf<List<SmartPlaylistInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        items = backend.smartPlaylists()
        loading = false
    }

    WearList {
        item { WearHeaderChip("Smart playlists", "${items.size}", onBack) }
        when {
            loading -> item { LoadingChip("Loading rules") }
            items.isEmpty() -> item { EmptyChip("No smart playlists", "Create one on the phone first") }
            else -> items(items) { sp ->
                Chip(
                    onClick = { onOpen(sp.id, sp.name) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = WearTheme.Pink) },
                    label = {
                        Text(sp.name, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun TrackListScreen(
    backend: Backend,
    title: String,
    loader: suspend () -> List<Track>,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    var tracks by remember(title) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(title) { mutableStateOf(true) }
    LaunchedEffect(title) {
        loading = true
        tracks = loader()
        loading = false
    }

    WearList {
        item { WearHeaderChip(title, if (loading) null else "${tracks.size} tracks", onBack) }
        when {
            loading -> item { LoadingChip("Loading tracks") }
            tracks.isEmpty() -> item { EmptyChip("No tracks", "Nothing to play here") }
            else -> {
                item {
                    Chip(
                        onClick = {
                            val queue = tracks.map { PlayableItem.Music(it) }
                            WearPlayerHolder.playQueue(backend.context, queue, 0)
                            onOpenNowPlaying()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Cyan),
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        label = { Text("Play all", color = WearTheme.OnSurface) },
                        secondaryLabel = { Text("${tracks.size} tracks", color = WearTheme.OnSurface) }
                    )
                }
                items(tracks) { track ->
                    TrackChip(backend, track) {
                        val queue = tracks.map { PlayableItem.Music(it) }
                        WearPlayerHolder.playQueue(backend.context, queue, tracks.indexOf(track).coerceAtLeast(0))
                        onOpenNowPlaying()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
