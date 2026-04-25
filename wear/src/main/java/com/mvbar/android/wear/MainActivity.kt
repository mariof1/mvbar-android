package com.mvbar.android.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NowPlayingRepository.attach(applicationContext)

        setContent {
            MaterialTheme {
                val state by NowPlayingRepository.state.collectAsState()
                val commands = remember { PhoneCommandClient(applicationContext) }
                Scaffold(timeText = { TimeText() }) {
                    NowPlayingScreen(state = state, commands = commands)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingScreen(state: NowPlayingState, commands: PhoneCommandClient) {
    val listState = rememberScalingLazyListState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        if (state.isEmpty) {
            Text(
                text = "Open mvbar on your phone\nto start playing.",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
            return@Box
        }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    state.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item {
                Text(
                    state.artist,
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { TransportRow(state, commands) }
            item { Spacer(Modifier.height(8.dp)) }
            item { SecondaryRow(state, commands) }
        }
    }
}

@Composable
private fun TransportRow(state: NowPlayingState, commands: PhoneCommandClient) {
    val accent = if (state.isPodcast || state.isAudiobook) Color(0xFFF97316) else Color(0xFF06B6D4)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircleButton(onClick = { commands.previous() }) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { commands.playPause() },
            colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = Color.White
            )
        }
        Spacer(Modifier.width(8.dp))
        CircleButton(onClick = { commands.next() }) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
        }
    }
}

@Composable
private fun SecondaryRow(state: NowPlayingState, commands: PhoneCommandClient) {
    if (state.isPodcast || state.isAudiobook || !state.favorite && !state.isPodcast) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!state.isPodcast && !state.isAudiobook) {
                CircleButton(onClick = { commands.toggleFavorite() }, size = 36) {
                    Icon(
                        if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (state.favorite) Color(0xFFEC4899) else Color.White
                    )
                }
            } else {
                CircleButton(onClick = { commands.seekBack() }, size = 36) {
                    Text("-15", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                CircleButton(onClick = { commands.seekForward() }, size = 36) {
                    Text("+15", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    size: Int = 44,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0x33FFFFFF)),
        modifier = Modifier.size(size.dp)
    ) { content() }
}
