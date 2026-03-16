package com.mvbar.android.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.player.PlayMode
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    lyrics: List<com.mvbar.android.data.model.LyricLine> = emptyList(),
    lyricsLoading: Boolean = false,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayQueueItem: (Int) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onLoadLyrics: ((Int) -> Unit)? = null
) {
    val track = state.currentTrack ?: return
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Back gesture minimizes the player
    BackHandler(onBack = onBack)

    // Load lyrics when switching to lyrics view or track changes
    LaunchedEffect(showLyrics, track.id) {
        if (showLyrics) onLoadLyrics?.invoke(track.id)
    }

    // Swipe-down-to-dismiss state
    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    // Dismiss if any meaningful swipe down detected (low threshold)
    val dismissThreshold = screenHeightPx * 0.08f

    // Use dragOffset directly during drag for instant feedback,
    // only animate when snapping back
    val displayOffset = if (isDismissing) screenHeightPx else dragOffset

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = displayOffset
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > dismissThreshold) {
                            isDismissing = true
                            onBack()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                    }
                )
            }
            .background(BackgroundDark)
            .background(
                Brush.verticalGradient(
                    listOf(Cyan900.copy(alpha = 0.4f), Color.Transparent)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = OnSurface, modifier = Modifier.size(32.dp))
                }
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurfaceDim
                )
                Row {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Filled.MusicNote,
                            "Lyrics",
                            tint = if (showLyrics) Cyan500 else OnSurfaceDim
                        )
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = OnSurfaceDim)
                    }
                }
            }

            Spacer(Modifier.weight(0.5f))

            // Album art or lyrics view
            if (showLyrics) {
                com.mvbar.android.ui.components.LyricsView(
                    lyrics = lyrics,
                    isLoading = lyricsLoading,
                    positionMs = state.position,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark.copy(alpha = 0.5f))
                )
            } else {
                // Album art
                AsyncImage(
                    model = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                )
            }

            Spacer(Modifier.height(32.dp))

            // Track info
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                track.displayAlbum,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Progress bar
            val progress = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f
            Slider(
                value = progress,
                onValueChange = { onSeek((it * state.duration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Cyan500,
                    activeTrackColor = Cyan500,
                    inactiveTrackColor = WhiteOverlay15
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(state.position), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                Text(formatTime(state.duration), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
            }

            Spacer(Modifier.height(16.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCyclePlayMode) {
                    Icon(
                        when (state.playMode) {
                            PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                            PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        "Play Mode",
                        tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(36.dp))
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(72.dp)
                        .background(Cyan500, CircleShape)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(36.dp))
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Favorite",
                        tint = if (state.isFavorite) Pink500 else OnSurfaceDim,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }

        // Queue bottom sheet
        if (showQueue) {
            QueueSheet(
                queue = state.queue,
                currentIndex = state.queueIndex,
                onDismiss = { showQueue = false },
                onPlayItem = onPlayQueueItem,
                onRemoveItem = onRemoveFromQueue,
                onClearQueue = { onClearQueue(); showQueue = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSurfaceDim) }
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.65f)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${queue.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    if (queue.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onClearQueue) {
                            Text("Clear", color = Pink500, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 20.dp))

            if (queue.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Queue is empty", color = OnSurfaceDim)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(queue) { index, track ->
                        val isActive = index == currentIndex
                        QueueItem(
                            track = track,
                            isActive = isActive,
                            onPlay = { onPlayItem(index) },
                            onRemove = { onRemoveItem(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    track: Track,
    isActive: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val bgColor = if (isActive) Cyan500.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Cyan400)
            )
            Spacer(Modifier.width(10.dp))
        }

        AsyncImage(
            model = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) Cyan400 else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            track.durationFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDim
        )

        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, "Remove", tint = OnSurfaceDim, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
