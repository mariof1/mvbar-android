package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.downloads.WearDownloads
import com.mvbar.android.wear.net.Episode
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun EpisodesScreen(
    backend: Backend,
    podcastId: Int,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val downloads by WearDownloads.active.collectAsState()

    LaunchedEffect(podcastId) {
        episodes = backend.podcastEpisodes(podcastId)
        loading = false
    }

    ScalingLazyColumn(modifier = Modifier
        .fillMaxSize()
        .background(WearTheme.Background)) {
        item {
            Chip(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WearTheme.Cyan) },
                label = { Text("Back", color = WearTheme.OnSurface) }
            )
        }
        if (loading) {
            item { Text("Loading…", color = WearTheme.OnSurfaceDim) }
        } else {
            items(episodes) { ep ->
                Column {
                    EpisodeChip(backend, ep, onClick = {
                        WearPlayerHolder.play(backend.context, PlayableItem.PodcastEp(ep))
                        onOpenNowPlaying()
                    })
                    val st = downloads[ep.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            st == null -> {
                                Chip(
                                    onClick = {
                                        WearDownloads.download(
                                            backend.context,
                                            PlayableItem.PodcastEp(ep)
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Background),
                                    icon = { Icon(Icons.Default.Download, contentDescription = null, tint = WearTheme.Cyan) },
                                    label = {
                                        Text(
                                            "Download",
                                            color = WearTheme.OnSurfaceDim,
                                            style = MaterialTheme.typography.caption2
                                        )
                                    }
                                )
                            }
                            st.done -> Text(
                                "Downloaded",
                                color = WearTheme.Cyan,
                                style = MaterialTheme.typography.caption2,
                                fontWeight = FontWeight.SemiBold
                            )
                            st.error != null -> Text(
                                "Error: ${st.error}",
                                color = WearTheme.Orange,
                                style = MaterialTheme.typography.caption2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            else -> Text(
                                "Downloading… ${st.percent}%",
                                color = WearTheme.OnSurfaceDim,
                                style = MaterialTheme.typography.caption2
                            )
                        }
                    }
                }
            }
        }
    }
}
