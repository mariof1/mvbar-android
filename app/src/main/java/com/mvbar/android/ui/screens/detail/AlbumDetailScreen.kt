package com.mvbar.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun AlbumDetailScreen(
    albumName: String,
    tracks: List<Track>,
    currentTrackId: Int?,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit
) {
    val coverTrackId = tracks.firstOrNull()?.id
    val albumArtist = tracks.firstOrNull()?.let { it.albumArtist ?: it.artist } ?: ""

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(300.dp)
            ) {
                coverTrackId?.let {
                    AsyncImage(
                        model = ApiClient.artUrl(it),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, BackgroundDark),
                                startY = 80f
                            )
                        )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
                ) {
                    Text(
                        albumName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    if (albumArtist.isNotEmpty()) {
                        Text(albumArtist, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceDim)
                    }
                    Text(
                        "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSubtle
                    )
                    Spacer(Modifier.height(12.dp))
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

        itemsIndexed(tracks) { index, track ->
            TrackListItem(
                track = track,
                index = index,
                isPlaying = track.id == currentTrackId,
                onPlay = { onPlayTrack(track, tracks) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
