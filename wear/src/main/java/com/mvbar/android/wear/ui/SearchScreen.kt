package com.mvbar.android.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.net.SearchResults
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
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(SearchResults()) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (text.isNotBlank()) query = text
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = SearchResults()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        results = backend.search(query)
        loading = false
    }

    WearList {
        item { WearHeaderChip("Search", if (query.isBlank()) "Voice search" else query, onBack) }
        item {
            Chip(
                onClick = {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search mvbar")
                    }
                    voiceLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(backgroundColor = WearTheme.Cyan),
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                label = {
                    Text(
                        if (query.isBlank()) "Speak search" else "Search again",
                        color = WearTheme.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = if (query.isNotBlank()) {
                    { Text(query, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else null
            )
        }

        when {
            query.isBlank() -> item { EmptyChip("Say a title, artist, or album", "Tap the microphone") }
            loading -> item { LoadingChip("Searching") }
            results.tracks.isEmpty() -> item { EmptyChip("No track matches", "Try another phrase") }
            else -> {
                item {
                    Chip(
                        onClick = {
                            playTracks(backend, results.tracks, 0)
                            onOpenNowPlaying()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                        icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = WearTheme.Cyan) },
                        label = { Text("Play results", color = WearTheme.OnSurface) },
                        secondaryLabel = { Text("${results.tracks.size} tracks", color = WearTheme.OnSurfaceDim) }
                    )
                }
                items(results.tracks) { track ->
                    TrackChip(backend, track) {
                        playTracks(backend, results.tracks, results.tracks.indexOf(track).coerceAtLeast(0))
                        onOpenNowPlaying()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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

private fun playTracks(backend: Backend, tracks: List<Track>, index: Int) {
    val queue = tracks.map { PlayableItem.Music(it) }
    WearPlayerHolder.playQueue(backend.context, queue, index.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)))
}
