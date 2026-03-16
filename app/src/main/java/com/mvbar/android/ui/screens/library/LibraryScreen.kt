package com.mvbar.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.ui.theme.*

@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    onPlaylistClick: (Int) -> Unit,
    onRefresh: () -> Unit,
    onHistoryClick: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) { onRefresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineLarge,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
            // History shortcut
            onHistoryClick?.let { onClick ->
                item {
                    ListItem(
                        headlineContent = { Text("Recently Played", color = OnSurface) },
                        supportingContent = { Text("Your listening history", color = OnSurfaceDim) },
                        leadingContent = {
                            Icon(Icons.Filled.History, null, tint = Cyan500, modifier = Modifier.size(40.dp))
                        },
                        modifier = Modifier
                            .clickable(onClick = onClick)
                            .padding(horizontal = 8.dp),
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }

            // Playlists header
            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        "Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceDim,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            items(playlists) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name, color = OnSurface) },
                    supportingContent = { Text("${playlist.itemCount} tracks", color = OnSurfaceDim) },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Cyan500, modifier = Modifier.size(40.dp))
                    },
                    modifier = Modifier
                        .clickable { onPlaylistClick(playlist.id) }
                        .padding(horizontal = 8.dp),
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No playlists yet", color = OnSurfaceDim)
                    }
                }
            }
        }
    }
}
