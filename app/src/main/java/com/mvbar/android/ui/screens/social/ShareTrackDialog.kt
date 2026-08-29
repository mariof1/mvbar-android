package com.mvbar.android.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.ShareDialogState

@Composable
fun ShareTrackDialog(
    track: Track,
    state: ShareDialogState,
    onDismiss: () -> Unit,
    onShare: (recipientIds: List<String>, message: String?) -> Unit
) {
    var selected by remember(track.id) { mutableStateOf(emptySet<String>()) }
    var message by remember(track.id) { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!state.isSending) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = SurfaceContainerDark,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Share, null, tint = Cyan500)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Share song", style = MaterialTheme.typography.titleLarge, color = OnSurface)
                        Text(
                            track.displayTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Cyan500) }

                    state.targets.isEmpty() -> Text(
                        "Add a friend first, then you can share songs with them.",
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )

                    else -> {
                        Text("Send to", style = MaterialTheme.typography.labelLarge, color = OnSurfaceDim)
                        LazyColumn(Modifier.heightIn(max = 230.dp)) {
                            items(state.targets, key = { it.id }) { target ->
                                val enabled = target.canAccess && !state.isSending
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = enabled) {
                                            selected = if (target.id in selected) {
                                                selected - target.id
                                            } else {
                                                selected + target.id
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = target.id in selected,
                                        onCheckedChange = null,
                                        enabled = enabled,
                                        colors = CheckboxDefaults.colors(checkedColor = Cyan500)
                                    )
                                    SocialAvatar(target.email, target.avatarPath)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(target.email, color = if (enabled) OnSurface else OnSurfaceSubtle)
                                        if (!target.canAccess) {
                                            Text(
                                                "Does not have access to this song",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OnSurfaceSubtle
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it.take(500) },
                            label = { Text("Message (optional)") },
                            minLines = 2,
                            maxLines = 4,
                            enabled = !state.isSending,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("${message.length}/500") }
                        )
                    }
                }

                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !state.isSending) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onShare(selected.toList(), message.takeIf { it.isNotBlank() }) },
                        enabled = selected.isNotEmpty() && !state.isLoading && !state.isSending,
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = BackgroundDark
                            )
                        } else {
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}
