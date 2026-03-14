package com.mvbar.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.theme.*

@Composable
fun TrackListItem(
    track: Track,
    index: Int? = null,
    isPlaying: Boolean = false,
    onPlay: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (isPlaying) Cyan500.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
        label = "trackBg"
    )
    val textColor by animateColorAsState(
        if (isPlaying) Cyan400 else OnSurface,
        label = "trackText"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                modifier = Modifier.width(28.dp)
            )
        }

        AsyncImage(
            model = ApiClient.artUrl(track.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
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

        if (isPlaying) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Cyan400)
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            track.durationFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDim
        )

        onFavorite?.let {
            IconButton(onClick = it, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (track.isFavorite) Pink500 else OnSurfaceDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
