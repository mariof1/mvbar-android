package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.NowPlayingRepository
import com.mvbar.android.wear.NowPlayingState
import com.mvbar.android.wear.PhoneCommandClient
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
fun WearNowPlayingScreen() {
    val ctx = LocalContext.current
    val local by WearPlayerHolder.state.collectAsState()
    val remote by NowPlayingRepository.state.collectAsState()
    val phone = remember { PhoneCommandClient(ctx.applicationContext) }

    when {
        local.isActive -> LocalNowPlaying(local)
        !remote.isEmpty -> RemoteNowPlaying(remote, phone)
        else -> EmptyNowPlaying()
    }
}

@Composable
private fun EmptyNowPlaying() {
    Box(
        modifier = Modifier.fillMaxSize().background(WearTheme.Background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Nothing is playing.\nTap a song or episode to start.",
            color = WearTheme.OnSurfaceDim,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption1,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun LocalNowPlaying(state: WearPlayerHolder.State) {
    val item = state.item ?: return
    val accent = if (item.isPodcast) WearTheme.Orange else WearTheme.Cyan
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearTheme.Background)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = WearTheme.OnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.subtitle,
            color = WearTheme.OnSurfaceDim,
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(Modifier.height(10.dp))

        val dur = state.durationMs.coerceAtLeast(1)
        val progress = (state.positionMs.toFloat() / dur).coerceIn(0f, 1f)
        GlowingProgressBar(progress, accent)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(state.positionMs), color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2)
            Text(formatMs(state.durationMs), color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { WearPlayerHolder.seekBy(-10_000) },
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = WearTheme.OnSurface) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { WearPlayerHolder.togglePlayPause() },
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = WearTheme.OnSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { WearPlayerHolder.seekBy(30_000) },
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.Forward30, contentDescription = "+30s", tint = WearTheme.OnSurface) }
        }
    }
}

@Composable
private fun RemoteNowPlaying(state: NowPlayingState, phone: PhoneCommandClient) {
    val accent = if (state.isPodcast || state.isAudiobook) WearTheme.Orange else WearTheme.Cyan
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearTheme.Background)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Playing on phone",
            color = WearTheme.Cyan,
            style = MaterialTheme.typography.caption2,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            state.title,
            color = WearTheme.OnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            state.artist,
            color = WearTheme.OnSurfaceDim,
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { phone.previous() },
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = WearTheme.OnSurface) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { phone.playPause() },
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = WearTheme.OnSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { phone.next() },
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = WearTheme.OnSurface) }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun GlowingProgressBar(progress: Float, accent: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(WearTheme.Surface, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
        // Glow
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(8.dp)
                .background(accent.copy(alpha = 0.25f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        )
        // Fill
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(3.dp)
                .background(accent, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
    }
}
