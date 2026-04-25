package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
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
    LaunchedEffect(Unit) { items = backend.smartPlaylists() }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text("Smart playlists", color = WearTheme.OnSurface) }
            )
        }
        items(items) { sp ->
            Chip(
                onClick = { onOpen(sp.id, sp.name) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = WearTheme.Pink) },
                label = { Text(sp.name, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
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
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(title) { tracks = loader() }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text(title, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text("${tracks.size} tracks", color = WearTheme.OnSurfaceDim) }
            )
        }
        if (tracks.isNotEmpty()) {
            item {
                Chip(
                    onClick = {
                        val list = tracks.map { PlayableItem.Music(it) }
                        WearPlayerHolder.playQueue(backend.context, list, 0)
                        onOpenNowPlaying()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Cyan),
                    label = { Text("Play all", color = WearTheme.OnSurface) }
                )
            }
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
