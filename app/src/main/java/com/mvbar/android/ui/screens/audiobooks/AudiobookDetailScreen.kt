package com.mvbar.android.ui.screens.audiobooks

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Audiobook
import com.mvbar.android.data.model.AudiobookChapter
import com.mvbar.android.data.model.AudiobookDetailProgress
import com.mvbar.android.ui.theme.*

@Composable
fun AudiobookDetailScreen(
    audiobook: Audiobook?,
    chapters: List<AudiobookChapter>,
    progress: AudiobookDetailProgress?,
    playingChapterId: Int?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayChapter: (AudiobookChapter, Long) -> Unit,
    onContinueListening: () -> Unit,
    onMarkFinished: () -> Unit
) {
    if (audiobook == null && !isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Audiobook not found", color = OnSurfaceDim)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Cyan900.copy(alpha = 0.5f), BackgroundDark)
                        )
                    )
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                Column {
                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 4.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Cover art
                        val artUrl = audiobook?.let { ApiClient.audiobookArtUrl(it.id) }

                        AsyncImage(
                            model = artUrl,
                            contentDescription = audiobook?.title,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceElevated),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                audiobook?.title ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            audiobook?.author?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceDim
                                )
                            }
                            audiobook?.narrator?.let {
                                Text(
                                    "Narrated by $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceSubtle
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                audiobook?.let {
                                    Text(
                                        it.durationFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceSubtle
                                    )
                                    Text(
                                        "${it.chapterCount} chapters",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceSubtle
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val hasProgress = progress != null && !progress.finished
                        Button(
                            onClick = onContinueListening,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cyan600,
                                contentColor = OnSurface
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (hasProgress) "Continue Listening" else "Play")
                        }

                        if (audiobook?.isFinished != true) {
                            FilledTonalButton(
                                onClick = onMarkFinished,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = SurfaceElevated,
                                    contentColor = OnSurface
                                ),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Finished", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (isLoading && chapters.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Cyan500)
                }
            }
        } else if (chapters.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No chapters", color = OnSurfaceDim)
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${chapters.size} chapters",
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            itemsIndexed(chapters, key = { _, ch -> ch.id }) { idx, chapter ->
                val isPlaying = chapter.id == playingChapterId
                val hasProgress = progress != null && chapter.id == progress.chapterId
                val resumeMs = if (progress != null && hasProgress) progress.positionMs else 0L

                ChapterListItem(
                    chapter = chapter,
                    index = idx,
                    isPlaying = isPlaying,
                    hasProgress = hasProgress,
                    onClick = { onPlayChapter(chapter, resumeMs) }
                )
            }
        }
    }
}

@Composable
private fun ChapterListItem(
    chapter: AudiobookChapter,
    index: Int,
    isPlaying: Boolean,
    hasProgress: Boolean,
    onClick: () -> Unit
) {
    val highlight = isPlaying || hasProgress
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                when {
                    isPlaying -> Cyan900.copy(alpha = 0.3f)
                    hasProgress -> Cyan900.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chapter number
        Text(
            "${index + 1}",
            color = if (isPlaying) Cyan400 else if (hasProgress) Cyan600 else OnSurfaceSubtle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )

        // Chapter info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chapter.title,
                color = if (isPlaying) Cyan400 else if (hasProgress) Cyan600 else OnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (chapter.durationFormatted.isNotEmpty()) {
                Text(
                    chapter.durationFormatted,
                    color = OnSurfaceSubtle,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Now playing indicator
        if (isPlaying) {
            NowPlayingIndicator()
        } else if (hasProgress) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Has progress",
                tint = Cyan600,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NowPlayingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val bars = listOf(0, 100, 200)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        bars.forEach { delayMs ->
            val height by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delayMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .background(Cyan400, RoundedCornerShape(1.dp))
            )
        }
    }
}
