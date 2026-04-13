package com.mvbar.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayerBar(
    state: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onTap: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return
    val progress = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f
    val isPodcast = state.isPodcastMode
    val artUrl = if (isPodcast) {
        ApiClient.episodeArtUrl(-track.id)
    } else {
        track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
    }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 100.dp.toPx() }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    // Velocity (px/s) above which a fast fling dismisses regardless of distance
    val flingVelocityThreshold = with(density) { 800.dp.toPx() }
    var dismissed by remember { mutableStateOf(false) }
    var velocityTracker by remember { mutableFloatStateOf(0f) }

    // Floating pill design
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(64.dp)
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (abs(offsetX.value) / dismissThresholdPx).coerceIn(0f, 1f) * 0.4f
            }
            .pointerInput(onDismiss) {
                if (onDismiss != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dismissed) return@detectHorizontalDragGestures
                            val currentOffset = offsetX.value
                            val velocity = velocityTracker
                            val shouldDismiss = abs(currentOffset) > dismissThresholdPx ||
                                    abs(velocity) > flingVelocityThreshold
                            if (shouldDismiss) {
                                dismissed = true
                                val target = if (currentOffset >= 0) screenWidthPx else -screenWidthPx
                                scope.launch {
                                    offsetX.animateTo(
                                        target,
                                        animationSpec = tween(200, easing = FastOutLinearInEasing)
                                    )
                                    onDismiss()
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.snapTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker = dragAmount * 60f // approximate px/s from per-frame delta
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        }
                    )
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap),
        color = SurfaceElevated,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    model = artUrl,
                    contentDescription = null,
                    placeholderIcon = if (isPodcast) Icons.Filled.Podcasts else Icons.Filled.MusicNote,
                    iconSize = 20.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
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

                if (isPodcast && onPrevious != null) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                        Text("-15", color = OnSurfaceDim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = OnSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (isPodcast) {
                    IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                        Text("+15", color = OnSurfaceDim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    val hasNext = state.queueIndex < state.queue.size - 1
                    IconButton(onClick = onNext, enabled = hasNext) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = if (hasNext) OnSurface else OnSurfaceDim.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            
            // Thin progress bar at bottom of pill
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = if (isPodcast) Orange500 else Cyan500,
                trackColor = Color.Transparent,
            )
        }
    }
}
