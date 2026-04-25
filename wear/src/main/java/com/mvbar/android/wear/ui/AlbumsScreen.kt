package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.mvbar.android.wear.net.Album
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun AlbumsScreen(
    backend: Backend,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        albums = backend.albums()
        loading = false
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text("Albums", color = WearTheme.OnSurface) },
                secondaryLabel = { Text("${albums.size}", color = WearTheme.OnSurfaceDim) }
            )
        }
        if (loading) {
            item { Text("Loading…", color = WearTheme.OnSurfaceDim) }
        } else {
            items(albums) { album ->
                AlbumChip(backend, album, onClick = { onOpenAlbum(album.displayName) })
            }
        }
    }
}

@Composable
private fun AlbumChip(backend: Backend, album: Album, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = {
            val art = backend.artworkUrl(album.artPath)
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Icon(Icons.Default.Album, contentDescription = null, tint = WearTheme.Cyan)
            }
        },
        label = {
            Text(
                album.displayName.ifEmpty { "Unknown" },
                color = WearTheme.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption1
            )
        },
        secondaryLabel = {
            Text(
                album.displayArtistName.ifEmpty { "" },
                color = WearTheme.OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption2
            )
        }
    )
}

@Composable
fun AlbumDetailScreen(
    backend: Backend,
    albumName: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    var tracks by remember { mutableStateOf<List<com.mvbar.android.wear.net.Track>>(emptyList()) }
    LaunchedEffect(albumName) { tracks = backend.albumTracks(albumName) }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = WearTheme.Cyan) },
                label = { Text(albumName, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
        if (tracks.isNotEmpty()) {
            item {
                Chip(
                    onClick = {
                        val items = tracks.map { PlayableItem.Music(it) }
                        WearPlayerHolder.playQueue(backend.context, items, 0)
                        onOpenNowPlaying()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Cyan),
                    label = { Text("Play all (${tracks.size})", color = WearTheme.OnSurface) }
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
