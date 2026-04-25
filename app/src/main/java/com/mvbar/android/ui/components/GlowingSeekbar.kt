package com.mvbar.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mvbar.android.ui.theme.Cyan400
import com.mvbar.android.ui.theme.Cyan500

/**
 * Modern seekbar with glowing thumb, gradient active track and a faint
 * animated shimmer running along the filled portion. Custom-drawn so we
 * have full control over the look across music / podcasts / audiobooks.
 *
 * [progress] is in [0f..1f]. While the user is dragging, updates are
 * streamed via [onProgressChange]; [onSeekFinished] is invoked on
 * release so callers can commit the seek.
 */
@Composable
fun GlowingSeekbar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Cyan500,
    accentHighlight: Color = Cyan400,
) {
    var dragging by remember { mutableStateOf(false) }

    val glowAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.65f else 0.28f,
        animationSpec = tween(durationMillis = 260),
        label = "seekbar-glow"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "seekbar-thumb-scale"
    )

    val shimmer = rememberInfiniteTransition(label = "seekbar-shimmer")
    val shimmerPhase by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "seekbar-shimmer-phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onProgressChange((down.position.x / w).coerceIn(0f, 1f))
                    drag(down.id) { change ->
                        onProgressChange((change.position.x / w).coerceIn(0f, 1f))
                        change.consume()
                    }
                    dragging = false
                    onSeekFinished()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackHeightPx = 6.dp.toPx()
            val trackRadius = trackHeightPx / 2f
            val clamped = progress.coerceIn(0f, 1f)
            val activeWidth = size.width * clamped

            // Inactive track (thin white overlay)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(0f, centerY - trackRadius),
                size = Size(size.width, trackHeightPx),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )

            if (activeWidth > 0.5f) {
                // Outer glow halo behind active track
                val glowH = trackHeightPx * 5f
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = glowAlpha * 0.4f),
                            accent.copy(alpha = glowAlpha)
                        ),
                        endX = activeWidth
                    ),
                    topLeft = Offset(0f, centerY - glowH / 2f),
                    size = Size(activeWidth, glowH),
                    cornerRadius = CornerRadius(glowH / 2f, glowH / 2f)
                )

                // Active track — cyan gradient
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = 0.85f), accentHighlight),
                        endX = activeWidth
                    ),
                    topLeft = Offset(0f, centerY - trackRadius),
                    size = Size(activeWidth, trackHeightPx),
                    cornerRadius = CornerRadius(trackRadius, trackRadius)
                )

                // Subtle shimmer sweeping along the active track
                val shimmerW = (size.width * 0.18f).coerceAtLeast(48f)
                val shimmerStart = shimmerPhase * (activeWidth + shimmerW) - shimmerW
                val shimmerEnd = shimmerStart + shimmerW
                val clipStart = shimmerStart.coerceAtLeast(0f)
                val clipEnd = shimmerEnd.coerceAtMost(activeWidth)
                if (clipEnd > clipStart) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0f)
                            ),
                            startX = shimmerStart,
                            endX = shimmerEnd
                        ),
                        topLeft = Offset(clipStart, centerY - trackRadius),
                        size = Size(clipEnd - clipStart, trackHeightPx),
                        cornerRadius = CornerRadius(trackRadius, trackRadius)
                    )
                }
            }

            // Thumb with glow halo
            val thumbRadiusPx = 9.dp.toPx() * thumbScale
            val thumbX = activeWidth.coerceIn(thumbRadiusPx, size.width - thumbRadiusPx)
            val haloRadius = thumbRadiusPx * 2.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(thumbX, centerY),
                    radius = haloRadius
                ),
                center = Offset(thumbX, centerY),
                radius = haloRadius
            )
            drawCircle(
                color = Color.White,
                center = Offset(thumbX, centerY),
                radius = thumbRadiusPx
            )
            drawCircle(
                color = accent,
                center = Offset(thumbX, centerY),
                radius = thumbRadiusPx * 0.55f
            )
        }
    }
}

/**
 * Non-interactive glowing progress line — used by the mini-player pill
 * and list rows (podcasts / audiobooks) so progress indicators match
 * the main seekbar look.
 */
@Composable
fun GlowingProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color = Cyan500,
    accentHighlight: Color = Cyan400,
    heightDp: Int = 3,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        val h = size.height
        val radius = h / 2f
        val clamped = progress.coerceIn(0f, 1f)
        val activeWidth = size.width * clamped

        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, h),
            cornerRadius = CornerRadius(radius, radius)
        )
        if (activeWidth > 0.5f) {
            // Subtle glow halo
            val glowH = h * 3.5f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accent.copy(alpha = 0.2f), accent.copy(alpha = 0.55f)),
                    endX = activeWidth
                ),
                topLeft = Offset(0f, (h - glowH) / 2f),
                size = Size(activeWidth, glowH),
                cornerRadius = CornerRadius(glowH / 2f, glowH / 2f)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accent.copy(alpha = 0.9f), accentHighlight),
                    endX = activeWidth
                ),
                topLeft = Offset(0f, 0f),
                size = Size(activeWidth, h),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}
