package com.mvbar.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QueueMusic
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
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

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

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No playlists yet", color = OnSurfaceDim)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                items(playlists) { playlist ->
                    ListItem(
                        headlineContent = {
                            Text(playlist.name, color = OnSurface)
                        },
                        supportingContent = {
                            Text("${playlist.itemCount} tracks", color = OnSurfaceDim)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.QueueMusic,
                                contentDescription = null,
                                tint = Cyan500,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        modifier = Modifier
                            .clickable { onPlaylistClick(playlist.id) }
                            .padding(horizontal = 8.dp),
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }
}
