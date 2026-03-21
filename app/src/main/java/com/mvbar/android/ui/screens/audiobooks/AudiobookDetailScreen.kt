package com.mvbar.android.ui.screens.audiobooks

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

            itemsIndexed(chapters, key = { _, ch -> ch.id }) { _, chapter ->
                val isCurrent = progress != null && chapter.id == progress.chapterId
                val resumeMs = if (isCurrent) progress!!.positionMs else 0L

                ChapterListItem(
                    chapter = chapter,
                    isCurrent = isCurrent,
                    onClick = { onPlayChapter(chapter, resumeMs) }
                )
            }
        }
    }
}

@Composable
private fun ChapterListItem(
    chapter: AudiobookChapter,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) Cyan900.copy(alpha = 0.2f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chapter number
        Text(
            "${chapter.position + 1}",
            color = if (isCurrent) Cyan400 else OnSurfaceSubtle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )

        // Chapter info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chapter.title,
                color = if (isCurrent) Cyan400 else OnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
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

        // Play icon
        if (isCurrent) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Now playing",
                tint = Cyan400,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
