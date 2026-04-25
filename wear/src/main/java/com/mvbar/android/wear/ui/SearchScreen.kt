package com.mvbar.android.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.net.Track
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

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
            ?.firstOrNull().orEmpty()
        if (text.isNotBlank()) query = text
    }

    LaunchedEffect(query) {
        tracks = if (query.isNotBlank()) backend.search(query).tracks else emptyList()
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
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
                val list = tracks.map { PlayableItem.Music(it) }
                val idx = tracks.indexOf(t).coerceAtLeast(0)
                WearPlayerHolder.playQueue(backend.context, list, idx)
                onOpenNowPlaying()
            })
        }
    }
}

@Composable
fun PlaylistTracksScreen(
    backend: Backend,
    playlistId: Int,
    title: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    TrackListScreen(
        backend = backend,
        title = title,
        loader = { backend.playlistTracks(playlistId) },
        onBack = onBack,
        onOpenNowPlaying = onOpenNowPlaying
    )
}
