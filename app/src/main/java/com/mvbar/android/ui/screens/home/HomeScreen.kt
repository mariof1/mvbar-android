package com.mvbar.android.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.RecBucket
import com.mvbar.android.data.model.Track
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.components.BucketCard
import com.mvbar.android.ui.components.ErrorMessage
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.HomeState

@Composable
fun HomeScreen(
    state: HomeState,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onRefresh: () -> Unit,
    onInitialLoad: () -> Unit = onRefresh,
    feedbackBusy: Boolean = false,
    onHideBucket: (RecBucket) -> Unit = {},
    onRestoreHiddenBuckets: () -> Unit = {}
) {
    LaunchedEffect(Unit) { onInitialLoad() }
    val isOnline = LocalIsOnline.current

    var detailsBucket by remember { mutableStateOf<RecBucket?>(null) }

    HomeContent(
        state = state,
        onPlayTrack = onPlayTrack,
        onBucketDetails = { detailsBucket = it },
        onRefresh = onRefresh,
        feedbackBusy = feedbackBusy,
        onRestoreHiddenBuckets = onRestoreHiddenBuckets
    )

    detailsBucket?.let { bucket ->
        AlertDialog(
            onDismissRequest = { detailsBucket = null },
            title = { Text(bucket.name) },
            text = {
                Text(
                    bucket.reason
                        ?: bucket.subtitle
                        ?: "Selected from your listening activity and music library."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        detailsBucket = null
                        onHideBucket(bucket)
                    },
                    enabled = isOnline && !feedbackBusy
                ) {
                    Text("Hide this mix", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { detailsBucket = null }) {
                    Text("Keep this mix")
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBucketDetails: (RecBucket) -> Unit,
    onRefresh: () -> Unit,
    feedbackBusy: Boolean,
    onRestoreHiddenBuckets: () -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    val isOnline = LocalIsOnline.current

    PullToRefreshBox(
        isRefreshing = state.isLoading || state.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val isPhoneLandscape = configuration.smallestScreenWidthDp < 600 &&
                screenWidthDp > configuration.screenHeightDp
        val bucketColumns = when {
            screenWidthDp > 900 -> 4
            screenWidthDp > 600 -> if (isPhoneLandscape) 3 else 4
            else -> 2
        }
        val bucketAspectRatio = if (isPhoneLandscape) 0.85f else 1f
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Error state
            if (state.error != null) {
                item(key = "error") {
                    ErrorMessage(
                        message = state.error,
                        onRetry = null,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // Recommendation buckets grid
            if (state.buckets.isNotEmpty()) {
                item(key = "recommended_header") {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (isPhoneLandscape) 16.dp else 20.dp,
                            vertical = if (isPhoneLandscape) 4.dp else 8.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Made for you",
                                style = if (isPhoneLandscape) MaterialTheme.typography.titleMedium
                                       else MaterialTheme.typography.titleLarge,
                                color = OnSurface
                            )
                            Text(
                                "A focused mix of favourites, rediscovery, and new finds",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceSubtle,
                                maxLines = 1
                            )
                        }
                        if (state.serverRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Cyan500,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                val rows = state.buckets.chunked(bucketColumns)
                itemsIndexed(rows, key = { idx, row -> "bucket_row_$idx" }) { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isPhoneLandscape) 12.dp else 16.dp,
                                vertical = if (isPhoneLandscape) 4.dp else 6.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(if (isPhoneLandscape) 8.dp else 12.dp)
                    ) {
                        for ((colIndex, bucket) in row.withIndex()) {
                            val bucketAvailable = remember(isOnline, bucket.tracks) {
                                isOnline || bucket.tracks.any { it.id > 0 && AudioCacheManager.isTrackCached(it.id) }
                            }
                            BucketCard(
                                bucket = bucket,
                                onClick = {
                                    if (bucket.tracks.isNotEmpty()) {
                                        onPlayTrack(bucket.tracks.first(), bucket.tracks)
                                    }
                                },
                                onPlay = {
                                    if (bucket.tracks.isNotEmpty()) {
                                        onPlayTrack(bucket.tracks.first(), bucket.tracks)
                                    }
                                },
                                onDetails = { onBucketDetails(bucket) },
                                bucketIndex = rowIndex * bucketColumns + colIndex,
                                compact = isPhoneLandscape,
                                artAspectRatio = bucketAspectRatio,
                                isAvailable = bucketAvailable,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(bucketColumns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (state.buckets.isEmpty() && !state.isLoading && !state.serverRefreshing) {
                item(key = "recommendation_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.MusicOff,
                            contentDescription = null,
                            tint = OnSurfaceSubtle,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (state.hiddenMixCount > 0) "All recommendation mixes are hidden"
                            else if (state.recommendationProfile == com.mvbar.android.data.model.RecommendationProfile.NEW) "Start listening"
                            else "No mixes available yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.hiddenMixCount > 0) {
                                "You hid ${state.hiddenMixCount} ${if (state.hiddenMixCount == 1) "mix" else "mixes"}. Restore them whenever you want a fresh selection."
                            } else if (state.recommendationProfile == com.mvbar.android.data.model.RecommendationProfile.NEW) {
                                "Play some music and your personalized recommendations will appear here."
                            } else {
                                "mvbar could not build a varied mix from the currently available music."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceDim
                        )
                        if (state.hiddenMixCount > 0) {
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = onRestoreHiddenBuckets,
                                enabled = isOnline && !feedbackBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                            ) {
                                if (feedbackBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Restore hidden mixes", color = Color.Black)
                            }
                        }
                    }
                }
            }

            if (state.buckets.isEmpty() && state.serverRefreshing && !state.isLoading) {
                item(key = "recommendation_rebuilding") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Cyan500)
                        Spacer(Modifier.height(14.dp))
                        Text("Building fresh mixes", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        Text("This normally takes only a few seconds.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                    }
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
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
