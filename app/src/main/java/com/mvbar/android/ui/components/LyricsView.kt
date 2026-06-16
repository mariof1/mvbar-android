package com.mvbar.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.LyricLine
import com.mvbar.android.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    isLoading: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
    LaunchedEffect(currentIndex, hasSyncedLyrics) {
        if (hasSyncedLyrics && currentIndex >= 0) {
            scope.launch {
                listState.animateScrollToItem(
                    index = currentIndex,
                    scrollOffset = -200
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
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
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top spacer for visual centering
                    item { Spacer(Modifier.height(if (hasSyncedLyrics) 100.dp else 12.dp)) }

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
                    item { Spacer(Modifier.height(200.dp)) }
                }
            }
        }
    }
}
