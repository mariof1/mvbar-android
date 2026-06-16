package com.mvbar.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.LyricLine
import com.mvbar.android.ui.theme.*
import kotlin.math.abs

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    isLoading: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
    lineSpacing: Dp = 12.dp,
    syncedTopSpacer: Dp = 100.dp,
    unsyncedTopSpacer: Dp = 12.dp,
    bottomSpacer: Dp = 200.dp,
    scrollAnimationMillis: Int = 1200
) {
    val listState = rememberLazyListState()
    val hasSyncedLyrics = remember(lyrics) { lyrics.any { it.timeMs >= 0L } }

    // Find current line index based on playback position
    val currentIndex = remember(lyrics, positionMs, hasSyncedLyrics) {
        if (lyrics.isEmpty() || !hasSyncedLyrics) -1
        else {
            var idx = -1
            for (i in lyrics.indices) {
                if (lyrics[i].timeMs <= positionMs) idx = i
                else break
            }
            idx
        }
    }

    // Auto-scroll to current line
    LaunchedEffect(currentIndex, hasSyncedLyrics, lyrics) {
        if (hasSyncedLyrics && currentIndex >= 0) {
            withFrameNanos { }
            listState.animateItemToCenter(
                itemIndex = currentIndex + 1,
                durationMillis = scrollAnimationMillis
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centerSpacer = maxHeight / 2
        val activeTopSpacer = if (syncedTopSpacer > centerSpacer) syncedTopSpacer else centerSpacer
        val activeBottomSpacer = if (bottomSpacer > centerSpacer) bottomSpacer else centerSpacer

        when {
            isLoading -> {
                CircularProgressIndicator(
                    color = Cyan500,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            lyrics.isEmpty() -> {
                Text(
                    "No lyrics available",
                    color = OnSurfaceDim,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(lineSpacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top spacer for visual centering
                    item { Spacer(Modifier.height(if (hasSyncedLyrics) activeTopSpacer else unsyncedTopSpacer)) }

                    itemsIndexed(lyrics) { index, line ->
                        val isActive = hasSyncedLyrics && index == currentIndex
                        val isPast = hasSyncedLyrics && index < currentIndex

                        val color by animateColorAsState(
                            when {
                                isActive -> Cyan400
                                isPast -> OnSurfaceDim.copy(alpha = 0.5f)
                                else -> OnSurfaceDim
                            },
                            label = "lyricColor"
                        )

                        Text(
                            text = line.text,
                            style = if (isActive)
                                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            else
                                MaterialTheme.typography.titleMedium,
                            color = color,
                            textAlign = TextAlign.Start,
                            maxLines = if (hasSyncedLyrics) 3 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Bottom spacer
                    item { Spacer(Modifier.height(if (hasSyncedLyrics) activeBottomSpacer else bottomSpacer)) }
                }
            }
        }
    }
}

private suspend fun LazyListState.animateItemToCenter(itemIndex: Int, durationMillis: Int) {
    if (layoutInfo.totalItemsCount == 0) return

    val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
    if (visibleItem == null) {
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        scrollToItem(itemIndex, scrollOffset = -(viewportHeight / 2))
        withFrameNanos { }
    }

    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = item.offset + item.size / 2
    val distance = itemCenter - viewportCenter

    if (abs(distance) <= 2) return

    animateScrollBy(
        value = distance.toFloat(),
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
    )
}
