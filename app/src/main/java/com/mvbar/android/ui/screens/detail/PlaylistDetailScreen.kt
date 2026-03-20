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
    onTrackLongPress: ((Track) -> Unit)? = null
) {
    val name = playlist?.name ?: "Playlist"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Cyan900.copy(alpha = 0.5f), BackgroundDark)
                        )
                    )
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        null,
                        tint = Cyan500,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Text(
                        "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSubtle
                    )
                    Spacer(Modifier.height(12.dp))
                    if (tracks.isNotEmpty()) {
                        Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                            Spacer(Modifier.width(4.dp))
                            Text("Play All", color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackListItem(
                    track = track,
                    index = index,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, tracks) },
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
