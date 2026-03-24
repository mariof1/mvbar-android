package com.mvbar.android.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.SearchAlbum
import com.mvbar.android.data.model.SearchArtist
import com.mvbar.android.data.model.SearchPlaylist
import com.mvbar.android.data.model.SearchResults
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun SearchScreen(
    results: SearchResults?,
    isLoading: Boolean,
    currentTrackId: Int?,
    onSearch: (String) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onArtistClick: (SearchArtist) -> Unit,
    onAlbumClick: (SearchAlbum) -> Unit,
    onPlaylistClick: (SearchPlaylist) -> Unit,
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null,
    onClose: () -> Unit,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            placeholder = { Text("Search songs, artists, albums...") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Cyan500) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Cyan500,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (query.isNotEmpty() && !isLoading) {
                        IconButton(onClick = {
                            query = ""
                            onSearch("")
                        }) {
                            Icon(Icons.Filled.Close, "Clear", tint = OnSurfaceDim)
                        }
                    }
                    if (query.isEmpty()) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, "Close", tint = OnSurfaceDim)
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
        )

        val hasQuery = query.trim().isNotEmpty()
        val hasResults = results != null && (
            results.hits.isNotEmpty() ||
            results.artists.isNotEmpty() ||
            results.albums.isNotEmpty() ||
            results.playlists.isNotEmpty()
        )

        if (hasResults) {
            val listState = rememberLazyListState()
            val totalItems = (results!!.artists.take(4).size +
                results.albums.take(4).size +
                results.playlists.take(4).size +
                results.hits.size +
                // section headers
                (if (results.artists.isNotEmpty()) 1 else 0) +
                (if (results.albums.isNotEmpty()) 1 else 0) +
                (if (results.playlists.isNotEmpty()) 1 else 0) +
                (if (results.hits.isNotEmpty()) 1 else 0))
            LaunchedEffect(listState, hasMore, isLoadingMore, totalItems) {
                snapshotFlow {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= totalItems - 5 && hasMore && !isLoadingMore
                }.collect { if (it) onLoadMore() }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Artists section
                val artists = results!!.artists
                if (artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(artists.take(4)) { artist ->
                        ArtistSearchRow(artist = artist, onClick = { onArtistClick(artist) })
                    }
                }

                // Albums section
                val albums = results.albums
                if (albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(albums.take(4)) { album ->
                        AlbumSearchRow(album = album, onClick = { onAlbumClick(album) })
                    }
                }

                // Playlists section
                val playlists = results.playlists
                if (playlists.isNotEmpty()) {
                    item { SectionHeader("Playlists") }
                    items(playlists.take(4)) { playlist ->
                        PlaylistSearchRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                    }
                }

                // Songs section
                val hits = results.hits
                if (hits.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    items(hits) { track ->
                        val trackWithFav = track.copy(isFavorite = track.id in favoriteIds)
                        TrackListItem(
                            track = trackWithFav,
                            isPlaying = track.id == currentTrackId,
                            onPlay = { onPlayTrack(track, hits) },
                            onFavorite = onToggleFavorite?.let { { it(track.id) } },
                            onMore = onTrackLongPress?.let { handler -> { handler(track) } },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                if (isLoadingMore) {
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
        } else if (hasQuery && !isLoading) {
            // No results
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Search, null,
                        tint = OnSurfaceDim.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No results found", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDim)
                    Spacer(Modifier.height(4.dp))
                    Text("Try a different search term", style = MaterialTheme.typography.bodySmall, color = OnSurfaceSubtle)
                }
            }
        } else if (!hasQuery) {
            // Initial state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Search, null,
                        tint = OnSurfaceDim.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Search your library", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDim)
                    Spacer(Modifier.height(4.dp))
                    Text("Find songs, artists, and albums", style = MaterialTheme.typography.bodySmall, color = OnSurfaceSubtle)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = OnSurfaceDim,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun ArtistSearchRow(artist: SearchArtist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                getInitials(artist.name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            if (artist.artPath != null) {
                val url = ApiClient.artPathUrl(artist.artPath) +
                    (artist.artHash?.let { "?h=$it" } ?: "")
                AsyncImage(
                    model = url, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${artist.trackCount} tracks · ${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim, maxLines = 1
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            tint = OnSurfaceSubtle, modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun AlbumSearchRow(album: SearchAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = OnSurfaceDim, modifier = Modifier.size(18.dp))
            if (album.artTrackId != null) {
                AsyncImage(
                    model = ApiClient.trackArtUrl(album.artTrackId),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (album.artPath != null) {
                val url = ApiClient.artPathUrl(album.artPath) +
                    (album.artHash?.let { "?h=$it" } ?: "")
                AsyncImage(
                    model = url, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.album,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${album.displayArtist ?: "Unknown Artist"} · ${album.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            tint = OnSurfaceSubtle, modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun PlaylistSearchRow(playlist: SearchPlaylist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Cyan500.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Cyan400, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))

        Text(
            buildString {
                append(playlist.name)
                if (playlist.kind == "smart") append(" (Smart)")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            tint = OnSurfaceSubtle, modifier = Modifier.size(14.dp)
        )
    }
}

private fun getInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val a = parts.firstOrNull()?.firstOrNull()?.uppercase() ?: "?"
    val b = if (parts.size > 1) parts.last().firstOrNull()?.uppercase() ?: "" else ""
    return a + b
}
