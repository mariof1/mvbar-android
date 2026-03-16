package com.mvbar.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.SmartPlaylist
import com.mvbar.android.ui.components.CreatePlaylistDialog
import com.mvbar.android.ui.theme.*

@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist> = emptyList(),
    onPlaylistClick: (Int) -> Unit,
    onSmartPlaylistClick: (Int) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onCreateSmartPlaylist: () -> Unit = {},
    onRefresh: () -> Unit,
    onHistoryClick: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) { onRefresh() }

    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = onCreatePlaylist
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Library",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurface
            )
        }

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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }

            // Playlists header with create button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, "Create Playlist", tint = Cyan500)
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No playlists yet", color = OnSurfaceDim)
                    }
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // Smart playlists header
            item {
                HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Smart Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    IconButton(onClick = onCreateSmartPlaylist) {
                        Icon(Icons.Filled.Add, "Create Smart Playlist", tint = Pink500)
                    }
                }
            }

            if (smartPlaylists.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No smart playlists yet", color = OnSurfaceDim)
                    }
                }
            }

            items(smartPlaylists) { sp ->
                ListItem(
                    headlineContent = { Text(sp.name, color = OnSurface) },
                    supportingContent = { Text("Sort: ${sp.sort}", color = OnSurfaceDim) },
                    leadingContent = {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Pink500, modifier = Modifier.size(40.dp))
                    },
                    modifier = Modifier
                        .clickable { onSmartPlaylistClick(sp.id) }
                        .padding(horizontal = 8.dp),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
