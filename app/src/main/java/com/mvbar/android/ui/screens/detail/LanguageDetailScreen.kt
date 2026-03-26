package com.mvbar.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun LanguageDetailScreen(
    languageName: String,
    tracks: List<Track>,
    isLoading: Boolean,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    currentTrackId: Int?,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit,
    onLoadMore: () -> Unit = {},
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, hasMore, isLoadingMore, tracks.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && tracks.isNotEmpty() && lastVisible >= tracks.size - 5
        }.collect { if (it) onLoadMore() }
    }

    LazyColumn(
        state = listState,
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
                        .background(Color(0xFF6A1B9A).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗣", style = MaterialTheme.typography.titleLarge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        languageName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                }
                Button(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp),
                    enabled = tracks.isNotEmpty()
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Play All", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan500)
                }
            }
        } else {
            itemsIndexed(tracks) { index, track ->
                val trackWithFav = track.copy(isFavorite = track.id in favoriteIds)
                TrackListItem(
                    track = trackWithFav,
                    index = index,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, tracks) },
                    onFavorite = onToggleFavorite?.let { { it(track.id) } },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan500, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
