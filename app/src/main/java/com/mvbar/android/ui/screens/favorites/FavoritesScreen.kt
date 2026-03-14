package com.mvbar.android.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@Composable
fun FavoritesScreen(
    favorites: List<Track>,
    currentTrackId: Int?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Favorites",
            style = MaterialTheme.typography.headlineLarge,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No favorites yet", color = OnSurfaceDim)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                items(favorites) { track ->
                    TrackListItem(
                        track = track.copy(isFavorite = true),
                        isPlaying = track.id == currentTrackId,
                        onPlay = { onPlayTrack(track, favorites) },
                        onFavorite = { onToggleFavorite(track.id) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
