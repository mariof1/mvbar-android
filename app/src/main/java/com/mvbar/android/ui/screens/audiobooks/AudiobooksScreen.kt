@file:Suppress("DEPRECATION")

package com.mvbar.android.ui.screens.audiobooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Audiobook
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.theme.*

@Composable
fun AudiobooksScreen(
    audiobooks: List<Audiobook>,
    isLoading: Boolean,
    onAudiobookClick: (Audiobook) -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading&& audiobooks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan500)
            }
        } else if (audiobooks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = OnSurfaceSubtle
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No audiobooks yet", color = OnSurfaceDim)
                    Text(
                        "Add audiobooks on the server to see them here",
                        color = OnSurfaceSubtle,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = 8.dp, bottom = 140.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(audiobooks, key = { it.id }) { book ->
                    AudiobookGridItem(
                        audiobook = book,
                        onClick = { onAudiobookClick(book) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookGridItem(audiobook: Audiobook, onClick: () -> Unit) {
    val artUrl = ApiClient.audiobookArtUrl(audiobook.id)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated)
        ) {
            ArtworkImage(
                model = artUrl,
                contentDescription = audiobook.title,
                placeholderIcon = Icons.Filled.MenuBook,
                iconSize = 32.dp,
                modifier = Modifier.fillMaxSize()
            )

            // Finished badge
            if (audiobook.isFinished) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = Cyan600
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = OnSurface
                        )
                        Text(
                            "Done",
                            color = OnSurface,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Progress bar at bottom
            val percent = audiobook.progressPercent
            if (percent > 0 && !audiobook.isFinished) {
                com.mvbar.android.ui.components.GlowingProgressLine(
                    progress = percent / 100f,
                    accent = Cyan500,
                    accentHighlight = Cyan400,
                    heightDp = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            audiobook.title,
            color = OnSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        audiobook.author?.let {
            Text(
                it,
                color = OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
