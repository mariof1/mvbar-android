package com.mvbar.android.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.RecBucket
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.ArtGrid
import com.mvbar.android.ui.components.BucketCard
import com.mvbar.android.ui.components.ErrorMessage
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.HomeState

@Composable
fun HomeScreen(
    state: HomeState,
    currentTrackId: Int?,
    favoriteIds: Set<Int> = emptySet(),
    onPlayTrack: (Track, List<Track>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: ((Int) -> Unit)? = null,
    onTrackLongPress: ((Track) -> Unit)? = null
) {
    LaunchedEffect(Unit) { onRefresh() }

    var selectedBucket by remember { mutableStateOf<RecBucket?>(null) }

    // Swipe back from bucket detail returns to home content
    BackHandler(enabled = selectedBucket != null) {
        selectedBucket = null
    }

    AnimatedContent(
        targetState = selectedBucket,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
        },
        label = "bucket_detail"
    ) { bucket ->
        if (bucket != null) {
            BucketDetailView(
                bucket = bucket,
                currentTrackId = currentTrackId,
                favoriteIds = favoriteIds,
                onPlayTrack = onPlayTrack,
                onToggleFavorite = onToggleFavorite,
                onBack = { selectedBucket = null }
            )
        } else {
            HomeContent(
                state = state,
                currentTrackId = currentTrackId,
                favoriteIds = favoriteIds,
                onPlayTrack = onPlayTrack,
                onBucketClick = { selectedBucket = it },
                onAlbumClick = onAlbumClick,
                onToggleFavorite = onToggleFavorite,
                onTrackLongPress = onTrackLongPress
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState,
    currentTrackId: Int?,
    favoriteIds: Set<Int> = emptySet(),
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBucketClick: (RecBucket) -> Unit,
    onAlbumClick: (String) -> Unit,
    onToggleFavorite: ((Int) -> Unit)? = null,
    onTrackLongPress: ((Track) -> Unit)? = null
) {
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { /* already auto-loads via LaunchedEffect in parent */ },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val bucketColumns = when {
            screenWidthDp > 900 -> 4
            screenWidthDp > 600 -> 3
            else -> 2
        }
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

            // Error state
            if (state.error != null) {
                item {
                    ErrorMessage(
                        message = state.error,
                        onRetry = null,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // Recommendation buckets grid
            if (state.buckets.isNotEmpty()) {
                item {
                    Text(
                        "Recommended",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                val rows = state.buckets.chunked(bucketColumns)
                itemsIndexed(rows) { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for ((colIndex, bucket) in row.withIndex()) {
                            BucketCard(
                                bucket = bucket,
                                onClick = { onBucketClick(bucket) },
                                onPlay = {
                                    if (bucket.tracks.isNotEmpty()) {
                                        onPlayTrack(bucket.tracks.first(), bucket.tracks)
                                    }
                                },
                                bucketIndex = rowIndex * bucketColumns + colIndex,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(bucketColumns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Recently Added
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
                    val trackWithFav = track.copy(isFavorite = track.id in favoriteIds)
                    TrackListItem(
                        track = trackWithFav,
                        isPlaying = track.id == currentTrackId,
                        onPlay = { onPlayTrack(track, state.recentlyAdded) },
                        onFavorite = onToggleFavorite?.let { { it(track.id) } },
                        onMore = onTrackLongPress?.let { { it(track) } },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            if (state.isLoading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Cyan500)
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketDetailView(
    bucket: RecBucket,
    currentTrackId: Int?,
    favoriteIds: Set<Int> = emptySet(),
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: ((Int) -> Unit)? = null,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Header with art grid
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                ArtGrid(artPaths = bucket.artPaths)

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
                        bucket.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    if (!bucket.subtitle.isNullOrBlank()) {
                        Text(
                            bucket.subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceDim
                        )
                    }
                    Text(
                        "${bucket.tracks.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSubtle
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (bucket.tracks.isNotEmpty()) {
                                onPlayTrack(bucket.tracks.first(), bucket.tracks)
                            }
                        },
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

        // Track list
        itemsIndexed(bucket.tracks) { index, track ->
            val trackWithFav = track.copy(isFavorite = track.id in favoriteIds)
            TrackListItem(
                track = trackWithFav,
                index = index,
                isPlaying = track.id == currentTrackId,
                onPlay = { onPlayTrack(track, bucket.tracks) },
                onFavorite = onToggleFavorite?.let { { it(track.id) } },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

