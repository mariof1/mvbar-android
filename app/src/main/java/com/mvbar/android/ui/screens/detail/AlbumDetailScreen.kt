package com.mvbar.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Album
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun AlbumDetailScreen(
    album: Album?,
    albumName: String,
    tracks: List<Track>,
    currentTrackId: Int?,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit,
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null
) {
    val artUrl = album?.artPath?.let { ApiClient.artPathUrl(it) }
        ?: tracks.firstOrNull()?.id?.let { ApiClient.trackArtUrl(it) }
    val albumArtist = album?.displayArtist ?: album?.artist
        ?: tracks.firstOrNull()?.let { it.albumArtist ?: it.artist } ?: ""

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    model = artUrl,
                    contentDescription = null,
                    placeholderIcon = Icons.Filled.Album,
                    iconSize = 28.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        albumName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (albumArtist.isNotEmpty()) {
                        Text(albumArtist, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "${tracks.size} tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceSubtle
                    )
                }
                Button(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Play All", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        itemsIndexed(tracks) { index, track ->
            val trackWithFav = track.copy(isFavorite = track.id in favoriteIds)
            TrackListItem(
                track = trackWithFav,
                index = index,
                isPlaying = track.id == currentTrackId,
                onPlay = { onPlayTrack(track, tracks) },
                onFavorite = onToggleFavorite?.let { { it(track.id) } },
                onMore = onTrackLongPress?.let { { it(track) } },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
