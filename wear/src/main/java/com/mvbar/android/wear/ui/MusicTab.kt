package com.mvbar.android.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.downloads.WearDownloads
import com.mvbar.android.wear.net.Playlist
import com.mvbar.android.wear.net.Track
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun MusicTab(backend: Backend, onOpenNowPlaying: () -> Unit) {
    var openedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var openedSearch by remember { mutableStateOf(false) }
    val opened = openedPlaylistId
    if (opened != null) {
        PlaylistTracksScreen(backend, opened, onBack = { openedPlaylistId = null }, onOpenNowPlaying = onOpenNowPlaying)
        return
    }
    if (openedSearch) {
        SearchScreen(backend, onBack = { openedSearch = false }, onOpenNowPlaying = onOpenNowPlaying)
        return
    }

    var recent by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        recent = backend.recentTracks()
        playlists = backend.playlists()
        loading = false
    }

    ScalingLazyColumn(modifier = Modifier
        .fillMaxSize()
        .background(WearTheme.Background)) {
        item {
            Chip(
                onClick = { openedSearch = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Cyan),
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search", color = WearTheme.OnSurface) }
            )
        }
        if (loading) {
            item { Text("Loading…", color = WearTheme.OnSurfaceDim) }
        } else {
            if (playlists.isNotEmpty()) {
                item { SectionLabel("Playlists") }
                items(playlists) { pl ->
                    Chip(
                        onClick = { openedPlaylistId = pl.id },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                        icon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = WearTheme.Cyan) },
                        label = { Text(pl.name, color = WearTheme.OnSurface) },
                        secondaryLabel = { Text("${pl.trackCount} tracks", color = WearTheme.OnSurfaceDim) }
                    )
                }
            }
            item { SectionLabel("Recent") }
            items(recent) { t ->
                TrackChip(backend, t, onClick = {
                    WearPlayerHolder.play(backend.context, PlayableItem.Music(t))
                    onOpenNowPlaying()
                })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = WearTheme.OnSurfaceDim,
        style = MaterialTheme.typography.caption2,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
    )
}

@Composable
fun PlaylistTracksScreen(
    backend: Backend,
    playlistId: Int,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val downloads by WearDownloads.active.collectAsState()
    LaunchedEffect(playlistId) { tracks = backend.playlistTracks(playlistId) }

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
        items(tracks) { t ->
            TrackChip(backend, t, onClick = {
                WearPlayerHolder.play(backend.context, PlayableItem.Music(t))
                onOpenNowPlaying()
            })
            val st = downloads[t.id]
            if (st == null) {
                Chip(
                    onClick = { WearDownloads.download(backend.context, PlayableItem.Music(t)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Background),
                    icon = { Icon(Icons.Default.Download, contentDescription = null, tint = WearTheme.Cyan) },
                    label = { Text("Download", color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2) }
                )
            } else if (!st.done) {
                Text(
                    "Downloading… ${st.percent}%",
                    color = WearTheme.OnSurfaceDim,
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.padding(start = 12.dp)
                )
            } else {
                Text(
                    "Downloaded",
                    color = WearTheme.Cyan,
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    backend: Backend,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?: ""
        if (text.isNotBlank()) query = text
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            val r = backend.search(query)
            tracks = r.tracks
        } else {
            tracks = emptyList()
        }
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
        item {
            Chip(
                onClick = {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search")
                    }
                    voiceLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Cyan),
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                label = { Text(if (query.isBlank()) "Speak…" else query, color = WearTheme.OnSurface) }
            )
        }
        items(tracks) { t ->
            TrackChip(backend, t, onClick = {
                WearPlayerHolder.play(backend.context, PlayableItem.Music(t))
                onOpenNowPlaying()
            })
        }
    }
}
