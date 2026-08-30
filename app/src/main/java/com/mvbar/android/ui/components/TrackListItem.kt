package com.mvbar.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.theme.*

@Composable
fun TrackListItem(
    track: Track,
    index: Int? = null,
    leadingText: String? = null,
    isPlaying: Boolean = false,
    onPlay: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val availability = trackAvailability(track.id)
    val isPlayable = availability.isPlayable

    val bgColor by animateColorAsState(
        if (isPlaying) Cyan500.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(200),
        label = "trackBg"
    )
    val textColor by animateColorAsState(
        if (isPlaying) Cyan400 else OnSurface,
        animationSpec = tween(200),
        label = "trackText"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isPlayable) 1f else 0.38f }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(enabled = isPlayable, onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayedLeadingText = leadingText ?: index?.let { "${it + 1}" }
        if (displayedLeadingText != null) {
            Text(
                displayedLeadingText,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                modifier = Modifier.width(28.dp)
            )
        }

        ArtworkImage(
            model = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id),
            contentDescription = null,
            placeholderIcon = Icons.Filled.MusicNote,
            iconSize = 20.dp,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.displayArtist,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AvailabilityBadge(
                    availability = availability,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
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

        if (track.durationFormatted.isNotEmpty()) {
            Text(
                track.durationFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim
            )
        }

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

        onMore?.let {
            IconButton(onClick = it, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = OnSurfaceDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
