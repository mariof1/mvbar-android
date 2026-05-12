package com.mvbar.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionBottomSheet(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    placeholderIcon: ImageVector = Icons.Filled.Album,
    trackCount: Int,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayAll: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSurfaceDim) }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    model = artworkUrl,
                    contentDescription = null,
                    placeholderIcon = placeholderIcon,
                    iconSize = 24.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = listOfNotNull(subtitle, "$trackCount tracks").joinToString(" • ")
                    Text(
                        sub,
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

            onPlayAll?.let {
                CollectionAction(Icons.Filled.PlayArrow, "Play All", onClick = { it(); onDismiss() })
            }
            CollectionAction(Icons.Filled.SkipNext, "Play Next", onClick = { onPlayNext(); onDismiss() })
            CollectionAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to Queue", onClick = { onAddToQueue(); onDismiss() })
            CollectionAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", onClick = { onAddToPlaylist(); onDismiss() })
        }
    }
}

@Composable
private fun CollectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = OnSurfaceDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
    }
}
