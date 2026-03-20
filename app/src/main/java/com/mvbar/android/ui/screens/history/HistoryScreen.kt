package com.mvbar.android.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun HistoryScreen(
    history: List<Track>,
    currentTrackId: Int?,
    onPlayTrack: (Track) -> Unit,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)? = null,
    isLoading: Boolean = false,
    error: String? = null,
    onTrackLongPress: ((Track) -> Unit)? = null,
    favoriteIds: Set<Int> = emptySet(),
    onToggleFavorite: ((Int) -> Unit)? = null
) {
    LaunchedEffect(Unit) { onRefresh() }

    val pullRefreshState = rememberPullToRefreshState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                }
            }
            Text(
                "Recently Played",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurface,
                modifier = Modifier.padding(horizontal = if (onBack != null) 4.dp else 12.dp)
            )
        }

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
                isLoading && history.isEmpty() -> {
                    CircularProgressIndicator(
                        color = Cyan500,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                history.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No listening history", color = OnSurfaceDim)
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(history) { track ->
                            val trackWithFav = track.copy(isFavorite = track.id in favoriteIds)
                            TrackListItem(
                                track = trackWithFav,
                                isPlaying = track.id == currentTrackId,
                                onPlay = { onPlayTrack(track) },
                                onFavorite = onToggleFavorite?.let { { it(track.id) } },
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
