package com.mvbar.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackBottomSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSurfaceDim) }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // Track header
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${track.displayArtist} • ${track.displayAlbum}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(4.dp))

            // Actions
            SheetAction(Icons.Filled.SkipNext, "Play Next", onClick = { onPlayNext(); onDismiss() })
            SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to Queue", onClick = { onAddToQueue(); onDismiss() })
            onAddToPlaylist?.let {
                SheetAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", onClick = { it(); onDismiss() })
            }
            SheetAction(
                if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (track.isFavorite) "Remove from Favorites" else "Add to Favorites",
                tint = if (track.isFavorite) Pink500 else OnSurfaceDim,
                onClick = { onToggleFavorite(); onDismiss() }
            )
            onGoToArtist?.let {
                SheetAction(Icons.Filled.Person, "Go to Artist", onClick = { it(); onDismiss() })
            }
            onGoToAlbum?.let {
                SheetAction(Icons.Filled.Album, "Go to Album", onClick = { it(); onDismiss() })
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = OnSurfaceDim,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
    }
}
