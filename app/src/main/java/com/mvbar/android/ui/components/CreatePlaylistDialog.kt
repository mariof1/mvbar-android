package com.mvbar.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvbar.android.ui.theme.*

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        title = {
            Text("New Playlist", color = OnSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan500,
                    unfocusedBorderColor = OnSurfaceDim,
                    focusedLabelColor = Cyan500,
                    cursorColor = Cyan500,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = if (name.isNotBlank()) Cyan500 else OnSurfaceDim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurfaceDim)
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<com.mvbar.android.data.model.Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        title = {
            Text("Add to Playlist", color = OnSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text("No playlists yet", color = OnSurfaceDim)
                } else {
                    playlists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                onSelect(playlist.id)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                playlist.name,
                                color = OnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${playlist.itemCount} tracks",
                                color = OnSurfaceDim,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurfaceDim)
            }
        }
    )
}
