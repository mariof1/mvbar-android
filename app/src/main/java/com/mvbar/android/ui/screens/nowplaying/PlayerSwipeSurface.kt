package com.mvbar.android.ui.screens.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** One continuous drag, with nested lists retaining their normal scrolling at either stop. */
@Composable
internal fun PlayerSwipeSurface(
    initialQueueOpen: Boolean,
    onQueueOpenChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBackdropBlurChanged: (Float) -> Unit,
    artwork: Any?,
    player: @Composable () -> Unit,
    queue: @Composable () -> Unit,
    overlay: @Composable () -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        val height = with(density) { maxHeight.toPx() }
        val upper = height * 0.65f
        var queueOpen by remember { mutableStateOf(initialQueueOpen) }
        var offset by remember(height) { mutableFloatStateOf(if (queueOpen) -upper else 0f) }
        var moved by remember { mutableStateOf(false) }
        val animation = remember { Animatable(offset) }
        val currentDismiss by rememberUpdatedState(onDismiss)
        val currentQueueChanged by rememberUpdatedState(onQueueOpenChanged)
        val currentBlurChanged by rememberUpdatedState(onBackdropBlurChanged)
        val downDp = with(density) { offset.coerceAtLeast(0f).toDp().value }
        val blur = 40f * (1f - (downDp / 600f).coerceIn(0f, 1f))
        SideEffect { currentBlurChanged(blur) }
        DisposableEffect(Unit) { onDispose { currentBlurChanged(0f) } }

        fun move(delta: Float): Float {
            val previous = offset
            offset = (offset + delta).coerceIn(-upper, if (queueOpen) 0f else height)
            if (offset != previous) moved = true
            return offset - previous
        }
        suspend fun settle() {
            if (!moved) return
            moved = false
            val dismiss = !queueOpen && offset > minOf(with(density) { 96.dp.toPx() }, height * 0.15f)
            val expanded = !dismiss && offset < -upper / 2f
            animation.snapTo(offset)
            animation.animateTo(if (dismiss) height else if (expanded) -upper else 0f, tween(180)) {
                offset = value
            }
            queueOpen = expanded
            currentQueueChanged(expanded)
            if (dismiss) currentDismiss()
        }
        val scrollState = rememberScrollableState { move(it) }
        val nested = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Expand the surface before scrolling a list. Mid-drag reversals follow the finger.
                return if ((available.y < 0 && offset > -upper) || (offset > -upper && offset < 0f) || offset > 0f) {
                    Offset(0f, move(available.y))
                } else Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!moved) return Velocity.Zero
                settle()
                return available
            }
        }
        val fling = object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                settle()
                return 0f
            }
        }

        // A stationary scrim protects the revealed app while the foreground slides down.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f * (1f - (downDp / 480f).coerceIn(0f, 1f)))))
        AsyncImage(
            model = artwork, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(100.dp).graphicsLayer {
                alpha = 0.3f * (1f - (downDp / 600f).coerceIn(0f, 1f))
            }
        )
        Column(
            Modifier.fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(viewportHeight * 1.65f)
                .offset { IntOffset(0, offset.roundToInt()) }
                .nestedScroll(nested)
                .scrollable(scrollState, Orientation.Vertical, flingBehavior = fling)
                .semantics { contentDescription = if (queueOpen) "Expanded player queue" else "Full player" }
        ) {
            Box(Modifier.fillMaxWidth().height(viewportHeight * 0.9f)) { player() }
            Box(Modifier.fillMaxWidth().height(viewportHeight * 0.75f).navigationBarsPadding()) { queue() }
        }
        overlay()
    }
}
