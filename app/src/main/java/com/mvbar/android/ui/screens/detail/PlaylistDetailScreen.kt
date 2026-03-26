package com.mvbar.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    tracks: List<Track>,
    isLoading: Boolean,
    currentTrackId: Int?,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null
) {
    val name = playlist?.name ?: "Playlist"

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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Cyan900.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Cyan500, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSubtle
                    )
                }
                if (tracks.isNotEmpty()) {
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
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan500)
                }
            }
        } else if (tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No tracks in this playlist", color = OnSurfaceDim)
                }
            }
        }

        itemsIndexed(tracks) { index, track ->
            val trackWithFav = track.copy(isFavorite = track.id in favoriteIds)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackListItem(
                    track = trackWithFav,
                    index = index,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, tracks) },
                    onFavorite = onToggleFavorite?.let { { it(track.id) } },
                    onMore = onTrackLongPress?.let { { it(track) } },
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                IconButton(
                    onClick = { onRemoveTrack(track.id) },
                    modifier = Modifier.size(36.dp).padding(end = 8.dp)
                ) {
                    Icon(Icons.Filled.Delete, "Remove", tint = OnSurfaceDim, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
