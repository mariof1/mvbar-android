package com.mvbar.android.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.AlbumCard
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.HomeState

@Composable
fun HomeScreen(
    state: HomeState,
    currentTrackId: Int?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Text(
                "For You",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        if (state.recommendations.isNotEmpty()) {
            item {
                Text(
                    "Recommended",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(state.recommendations.take(10)) { track ->
                TrackListItem(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, state.recommendations) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Recently Added",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(state.recentlyAdded.take(15)) { track ->
                TrackListItem(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, state.recentlyAdded) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp)) {
                    CircularProgressIndicator(
                        color = Cyan500,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
