package com.mvbar.android.wear.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.mvbar.android.wear.net.Album
import com.mvbar.android.wear.net.Track
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

    WearList {
        item { WearHeaderChip("Albums", if (loading) null else "${albums.size}", onBack) }
        when {
            loading -> item { LoadingChip("Loading albums") }
            albums.isEmpty() -> item { EmptyChip("No albums", "Sync your library from the phone") }
            else -> items(albums) { album ->
                AlbumChip(backend, album) { onOpenAlbum(album.displayName) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
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
                album.displayArtistName,
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
    var tracks by remember(albumName) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(albumName) { mutableStateOf(true) }
    LaunchedEffect(albumName) {
        loading = true
        tracks = backend.albumTracks(albumName)
        loading = false
    }

    WearList {
        item { WearHeaderChip(albumName, if (loading) null else "${tracks.size} tracks", onBack) }
        when {
            loading -> item { LoadingChip("Loading album") }
            tracks.isEmpty() -> item { EmptyChip("No tracks", "This album has nothing playable") }
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
                        label = { Text("Play album", color = Color.Black, fontWeight = FontWeight.SemiBold) },
                        secondaryLabel = { Text("${tracks.size} tracks", color = Color.Black) }
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
