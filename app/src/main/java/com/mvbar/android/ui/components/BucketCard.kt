package com.mvbar.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.RecBucket
import com.mvbar.android.ui.theme.*

@Composable
fun BucketCard(
    bucket: RecBucket,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit = {},
    bucketIndex: Int = 0,
    compact: Boolean = false,
    artAspectRatio: Float = 1f,
    isAvailable: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (isAvailable) 1f else 0.38f }
            .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
            .clickable(onClick = onClick)
    ) {
        // 2x2 art grid like the web app
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / artAspectRatio)
                .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
                .background(SurfaceDark)
        ) {
            ArtGrid(artPaths = bucket.artPaths, artHashes = bucket.artHashes)

            // Play button overlay — plays bucket directly
            IconButton(
                onClick = onPlay,
                enabled = isAvailable,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (compact) 36.dp else 44.dp)
                    .background(Cyan500.copy(alpha = 0.9f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play ${bucket.name}",
                    tint = Color.Black,
                    modifier = Modifier.size(if (compact) 20.dp else 26.dp)
                )
            }
        }

        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                bucket.name,
                style = if (compact) MaterialTheme.typography.bodySmall
                       else MaterialTheme.typography.titleSmall,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDetails,
                modifier = Modifier.size(if (compact) 28.dp else 32.dp)
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Why ${bucket.name} was recommended",
                    tint = OnSurfaceSubtle,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                )
            }
        }
        if (!bucket.subtitle.isNullOrBlank()) {
            Text(
                bucket.subtitle,
                style = if (compact) MaterialTheme.typography.labelSmall
                       else MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (bucket.count > 0 && !compact) {
            Text(
                "${bucket.count} songs",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceSubtle
            )
        }
    }
}

@Composable
fun ArtGrid(
    artPaths: List<String>,
    artHashes: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val paths = artPaths.take(4)
    fun artworkUrl(index: Int): String {
        val base = ApiClient.artPathUrl(paths[index])
        val hash = artHashes.getOrNull(index)?.takeIf { it.isNotBlank() }
        return if (hash != null) "$base?h=$hash" else base
    }
    if (paths.isEmpty()) {
        // Placeholder gradient
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0E7490),
                            Color(0xFF1E3A5F)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
        }
        return
    }

    when (paths.size) {
        1 -> {
            ArtworkImage(
                model = artworkUrl(0),
                contentDescription = null,
                placeholderIcon = Icons.Filled.MusicNote,
                iconSize = 32.dp,
                modifier = modifier.fillMaxSize()
            )
        }
        2 -> {
            Row(modifier = modifier.fillMaxSize()) {
                ArtworkImage(
                    model = artworkUrl(0),
                    contentDescription = null,
                    placeholderIcon = Icons.Filled.MusicNote,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Spacer(Modifier.width(1.dp))
                ArtworkImage(
                    model = artworkUrl(1),
                    contentDescription = null,
                    placeholderIcon = Icons.Filled.MusicNote,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        3 -> {
            Column(modifier = modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ArtworkImage(
                        model = artworkUrl(0),
                        contentDescription = null,
                        placeholderIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(Modifier.width(1.dp))
                    ArtworkImage(
                        model = artworkUrl(1),
                        contentDescription = null,
                        placeholderIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Spacer(Modifier.height(1.dp))
                ArtworkImage(
                    model = artworkUrl(2),
                    contentDescription = null,
                    placeholderIcon = Icons.Filled.MusicNote,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
        else -> {
            // 2x2 grid
            Column(modifier = modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ArtworkImage(
                        model = artworkUrl(0),
                        contentDescription = null,
                        placeholderIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(Modifier.width(1.dp))
                    ArtworkImage(
                        model = artworkUrl(1),
                        contentDescription = null,
                        placeholderIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Spacer(Modifier.height(1.dp))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ArtworkImage(
                        model = artworkUrl(2),
                        contentDescription = null,
                        placeholderIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(Modifier.width(1.dp))
                    ArtworkImage(
                        model = artworkUrl(3),
                        contentDescription = null,
                        placeholderIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}
