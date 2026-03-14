package com.mvbar.android.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.SearchResults
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.ArtistCard
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    results: SearchResults?,
    currentTrackId: Int?,
    onSearch: (String) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.length >= 2) onSearch(it)
            },
            placeholder = { Text("Search tracks, artists, albums...") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                IconButton(onClick = {
                    if (query.isNotEmpty()) { query = "" } else onClose()
                }) {
                    Icon(Icons.Filled.Close, "Clear")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
        )

        results?.let { r ->
            LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                if (r.tracks.isNotEmpty()) {
                    item {
                        Text(
                            "Tracks",
                            style = MaterialTheme.typography.titleMedium,
                            color = Cyan500,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(r.tracks.take(10)) { track ->
                        TrackListItem(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onPlay = { onPlayTrack(track, r.tracks) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                if (r.artists.isNotEmpty()) {
                    item {
                        Text(
                            "Artists",
                            style = MaterialTheme.typography.titleMedium,
                            color = Cyan500,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(r.artists.take(6)) { artist ->
                        ListItem(
                            headlineContent = { Text(artist.name, color = OnSurface) },
                            supportingContent = { Text("${artist.trackCount} tracks", color = OnSurfaceDim) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }

                if (r.albums.isNotEmpty()) {
                    item {
                        Text(
                            "Albums",
                            style = MaterialTheme.typography.titleMedium,
                            color = Cyan500,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(r.albums.take(6)) { album ->
                        ListItem(
                            headlineContent = { Text(album.name, color = OnSurface) },
                            supportingContent = {
                                Text(
                                    "${album.albumArtist ?: album.artist ?: ""} • ${album.trackCount} tracks",
                                    color = OnSurfaceDim
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
