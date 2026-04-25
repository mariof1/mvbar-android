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

    // Swipe-to-dismiss: raw state tracks finger synchronously during drag,
    // Animatable only used for end-state animations (snap-back / slide-out)
    var rawOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val animOffset = remember { Animatable(0f) }
    val displayOffset = if (isDragging) rawOffset else animOffset.value
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 100.dp.toPx() }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val flingVelocityThreshold = with(density) { 600.dp.toPx() }
    var dismissed by remember { mutableStateOf(false) }
    var velocityTracker by remember { mutableFloatStateOf(0f) }

    // Floating pill design
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(64.dp)
            .graphicsLayer {
                translationX = displayOffset
                alpha = 1f - (abs(displayOffset) / dismissThresholdPx).coerceIn(0f, 1f) * 0.4f
            }
            .pointerInput(onDismiss) {
                if (onDismiss != null) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            rawOffset = animOffset.value
                        },
                        onDragEnd = {
                            isDragging = false
                            if (dismissed) return@detectHorizontalDragGestures
                            val current = rawOffset
                            val velocity = velocityTracker
                            val shouldDismiss = abs(current) > dismissThresholdPx ||
                                    abs(velocity) > flingVelocityThreshold
                            if (shouldDismiss) {
                                dismissed = true
                                val target = if (current >= 0) screenWidthPx else -screenWidthPx
                                scope.launch {
                                    animOffset.snapTo(current)
                                    animOffset.animateTo(
                                        target,
                                        animationSpec = tween(150, easing = FastOutLinearInEasing)
                                    )
                                    onDismiss()
                                }
                            } else {
                                scope.launch {
                                    animOffset.snapTo(current)
                                    animOffset.animateTo(0f, animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ))
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                animOffset.snapTo(rawOffset)
                                animOffset.animateTo(0f, animationSpec = spring(
                                    stiffness = Spring.StiffnessHigh
                                ))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker = dragAmount * 60f
                            rawOffset += dragAmount
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
            GlowingProgressLine(
                progress = progress,
                accent = if (isPodcast) Orange500 else Cyan500,
                accentHighlight = if (isPodcast) Orange400 else Cyan400,
                heightDp = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
