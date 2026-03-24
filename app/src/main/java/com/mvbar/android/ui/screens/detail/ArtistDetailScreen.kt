package com.mvbar.android.ui.screens.detail

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Album
import com.mvbar.android.data.model.Artist
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun ArtistDetailScreen(
    artist: Artist?,
    tracks: List<Track>,
    albums: List<Album> = emptyList(),
    appearsOn: List<Album> = emptyList(),
    currentTrackId: Int?,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit,
    onAlbumClick: ((String) -> Unit)? = null,
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null,
    hasMoreTracks: Boolean = false,
    isLoadingMoreTracks: Boolean = false,
    onLoadMoreTracks: () -> Unit = {}
) {
    if (artist == null) return

    val listState = rememberLazyListState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 5 && hasMoreTracks && !isLoadingMoreTracks
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onLoadMoreTracks()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                artist.id?.let { id ->
                    AsyncImage(
                        model = ApiClient.artistArtUrl(id),
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
                                startY = 100f
                            )
                        )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(8.dp)
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
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Text(
                        "${artist.trackCount} tracks • ${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDim
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

        // Albums section
        if (albums.isNotEmpty()) {
            item {
                Text(
                    "Albums",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            item {
                AlbumRow(albums = albums, onAlbumClick = onAlbumClick)
            }
        }

        // Appears On section
        if (appearsOn.isNotEmpty()) {
            item {
                Text(
                    "Appears On",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            item {
                AlbumRow(albums = appearsOn, onAlbumClick = onAlbumClick)
            }
        }

        // Tracks header
        if (tracks.isNotEmpty()) {
            item {
                Text(
                    "Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
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

        if (isLoadingMoreTracks) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Cyan500,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(
    albums: List<Album>,
    onAlbumClick: ((String) -> Unit)?
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums) { album ->
            Card(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onAlbumClick?.invoke(album.displayName) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column {
                    val artUrl = album.artPath?.let { ApiClient.artPathUrl(it) }
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            album.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${album.trackCount} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceDim
                        )
                    }
                }
            }
        }
    }
}
