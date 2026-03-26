package com.mvbar.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.mvbar.android.data.model.SmartPlaylistResponse
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun SmartPlaylistDetailScreen(
    detail: SmartPlaylistResponse?,
    isLoading: Boolean,
    currentTrackId: Int?,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null
) {
    val tracks = detail?.tracks ?: emptyList()

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
                        .background(Pink500.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Pink500, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        detail?.name ?: "Smart Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${detail?.trackCount ?: 0} tracks • ${detail?.sort ?: "random"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSubtle
                    )
                }
                if (tracks.isNotEmpty()) {
                    IconButton(
                        onClick = onPlayAll,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play All", tint = Cyan500, modifier = Modifier.size(24.dp))
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Edit, "Edit", tint = Cyan400, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Delete, "Delete", tint = Pink500, modifier = Modifier.size(20.dp))
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
                    Text("No tracks match these filters", color = OnSurfaceDim)
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
