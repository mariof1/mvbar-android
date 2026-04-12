package com.mvbar.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.mvbar.android.ui.theme.SurfaceElevated

/**
 * Artwork image with a placeholder icon shown while loading/on error/when URL is null.
 */
@Composable
fun ArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Filled.MusicNote,
    iconSize: Dp = 24.dp,
    contentScale: ContentScale = ContentScale.Crop,
    backgroundColor: Color = SurfaceElevated
) {
    if (model == null) {
        ArtworkPlaceholder(
            icon = placeholderIcon,
            iconSize = iconSize,
            backgroundColor = backgroundColor,
            modifier = modifier
        )
        return
    }

    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            ArtworkPlaceholder(
                icon = placeholderIcon,
                iconSize = iconSize,
                backgroundColor = backgroundColor,
                modifier = Modifier.fillMaxSize()
            )
        },
        error = {
            ArtworkPlaceholder(
                icon = placeholderIcon,
                iconSize = iconSize,
                backgroundColor = backgroundColor,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

@Composable
fun ArtworkPlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    backgroundColor: Color = SurfaceElevated
) {
    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize)
        )
    }
}
