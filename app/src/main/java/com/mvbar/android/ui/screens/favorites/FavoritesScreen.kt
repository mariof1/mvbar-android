package com.mvbar.android.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.ErrorMessage
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<Track>,
    currentTrackId: Int?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    onTrackLongPress: ((Track) -> Unit)? = null
) {
    LaunchedEffect(Unit) { onRefresh() }

    val pullRefreshState = rememberPullToRefreshState()

    Column(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                error != null -> {
                    ErrorMessage(
                        message = error,
                        onRetry = onRefresh,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                isLoading && favorites.isEmpty() -> {
                    CircularProgressIndicator(
                        color = Cyan500,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                favorites.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No favorites yet", color = OnSurfaceDim)
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(favorites) { track ->
                            TrackListItem(
                                track = track.copy(isFavorite = true),
                                isPlaying = track.id == currentTrackId,
                                onPlay = { onPlayTrack(track, favorites) },
                                onFavorite = { onToggleFavorite(track.id) },
                                onMore = onTrackLongPress?.let { { it(track) } },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
