package com.mvbar.android.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var episodes by remember(podcastId) { mutableStateOf<List<Episode>>(emptyList()) }
    var loading by remember(podcastId) { mutableStateOf(true) }
    val downloads by WearDownloads.active.collectAsState()

    LaunchedEffect(podcastId) {
        loading = true
        episodes = backend.podcastEpisodes(podcastId)
        loading = false
    }

    WearList {
        item { WearHeaderChip("Episodes", if (loading) null else "${episodes.size}", onBack, WearTheme.Orange) }
        when {
            loading -> item { LoadingChip("Loading episodes") }
            episodes.isEmpty() -> item { EmptyChip("No episodes", "Nothing available for this show") }
            else -> items(episodes) { episode ->
                Column {
                    EpisodeChip(backend, episode) {
                        WearPlayerHolder.play(backend.context, PlayableItem.PodcastEp(episode))
                        onOpenNowPlaying()
                    }
                    DownloadStatusChip(
                        episode = episode,
                        status = downloads[episode.id],
                        onDownload = {
                            WearDownloads.download(backend.context, PlayableItem.PodcastEp(episode))
                        }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DownloadStatusChip(
    episode: Episode,
    status: WearDownloads.Status?,
    onDownload: () -> Unit
) {
    when {
        status == null -> {
            Chip(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 4.dp),
                colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Background),
                icon = { Icon(Icons.Default.Download, contentDescription = null, tint = WearTheme.Cyan) },
                label = {
                    Text("Download", color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2)
                },
                secondaryLabel = episode.durationFormatted.takeIf { it.isNotBlank() }?.let {
                    { Text(it, color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2) }
                }
            )
        }
        status.done -> Text(
            "Downloaded",
            color = WearTheme.Cyan,
            style = MaterialTheme.typography.caption2,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 18.dp, bottom = 6.dp)
        )
        status.error != null -> Text(
            "Download failed: ${status.error}",
            color = WearTheme.Orange,
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp)
        )
        else -> Text(
            "Downloading ${status.percent}%",
            color = WearTheme.OnSurfaceDim,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.padding(start = 18.dp, bottom = 6.dp)
        )
    }
}
