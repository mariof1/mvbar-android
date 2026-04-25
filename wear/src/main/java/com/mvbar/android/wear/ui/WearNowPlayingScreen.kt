package com.mvbar.android.wear.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.mvbar.android.wear.NowPlayingRepository
import com.mvbar.android.wear.NowPlayingState
import com.mvbar.android.wear.PhoneCommandClient
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder
import kotlinx.coroutines.launch

@Composable
fun WearNowPlayingScreen(
    onOpenQueue: () -> Unit
) {
    val ctx = LocalContext.current
    val local by WearPlayerHolder.state.collectAsState()
    val remote by NowPlayingRepository.state.collectAsState()
    val phone = remember { PhoneCommandClient(ctx.applicationContext) }

    when {
        local.isActive -> LocalNowPlaying(local, onOpenQueue)
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
private fun LocalNowPlaying(state: WearPlayerHolder.State, onOpenQueue: () -> Unit) {
    val item = state.item ?: return
    val ctx = LocalContext.current
    val backend = remember { Backend.get(ctx.applicationContext) }
    val accent = if (item.isPodcast) WearTheme.Orange else WearTheme.Cyan

    val artUrl = remember(item) {
        when (item) {
            is PlayableItem.Music -> backend.artworkUrl(item.track.artPath)
            else -> null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        // Blurred background art
        if (artUrl != null && Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp)
                    .alpha(0.35f)
            )
        }
        // Dark scrim
        Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000)))

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Foreground album art
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                item.title,
                color = WearTheme.OnSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption1
            )
            Text(
                item.subtitle,
                color = WearTheme.OnSurfaceDim,
                style = MaterialTheme.typography.caption2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            val dur = state.durationMs.coerceAtLeast(1)
            val progress = (state.positionMs.toFloat() / dur).coerceIn(0f, 1f)
            GlowingProgressBar(progress, accent)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(state.positionMs), color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption3)
                Text(formatMs(state.durationMs), color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption3)
            }

            Spacer(Modifier.height(4.dp))

            // Transport row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { WearPlayerHolder.previous() },
                    enabled = state.hasPrevious,
                    colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = WearTheme.OnSurface) }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { WearPlayerHolder.togglePlayPause() },
                    colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = WearTheme.OnSurface
                    )
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { WearPlayerHolder.next() },
                    enabled = state.hasNext,
                    colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = WearTheme.OnSurface) }
            }

            Spacer(Modifier.height(6.dp))

            // Volume + favorite + queue row
            VolumeAndActionsRow(
                state = state,
                isMusic = item is PlayableItem.Music,
                onToggleFavorite = {
                    val music = item as? PlayableItem.Music ?: return@VolumeAndActionsRow
                    val newFav = !state.isFavorite
                    WearPlayerHolder.setFavoriteLocal(newFav)
                    kotlinx.coroutines.MainScope().launch {
                        backend.setFavorite(music.track.id, newFav)
                    }
                },
                onOpenQueue = onOpenQueue
            )
        }
    }
}

@Composable
private fun VolumeAndActionsRow(
    state: WearPlayerHolder.State,
    isMusic: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var vol by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    fun setVol(v: Int) {
        val nv = v.coerceIn(0, maxVol)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
        vol = nv
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = { setVol(vol - 1) },
            colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
            modifier = Modifier.size(28.dp)
        ) { Icon(Icons.Default.VolumeDown, contentDescription = "Volume down", tint = WearTheme.OnSurface, modifier = Modifier.size(14.dp)) }

        Text(
            "${(vol * 100 / maxVol.coerceAtLeast(1))}%",
            color = WearTheme.OnSurfaceDim,
            style = MaterialTheme.typography.caption3
        )

        Button(
            onClick = { setVol(vol + 1) },
            colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
            modifier = Modifier.size(28.dp)
        ) { Icon(Icons.Default.VolumeUp, contentDescription = "Volume up", tint = WearTheme.OnSurface, modifier = Modifier.size(14.dp)) }

        if (isMusic) {
            Button(
                onClick = onToggleFavorite,
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (state.isFavorite) WearTheme.Pink else WearTheme.OnSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Button(
            onClick = onOpenQueue,
            colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
            modifier = Modifier.size(28.dp)
        ) { Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = WearTheme.OnSurface, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun RemoteNowPlaying(state: NowPlayingState, phone: PhoneCommandClient) {
    val accent = if (state.isPodcast || state.isAudiobook) WearTheme.Orange else WearTheme.Cyan
    Column(
        modifier = Modifier.fillMaxSize().background(WearTheme.Background)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Playing on phone", color = WearTheme.Cyan, style = MaterialTheme.typography.caption2, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(state.title, color = WearTheme.OnSurface, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text(state.artist, color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { phone.previous() }, colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface), modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = WearTheme.OnSurface)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { phone.playPause() }, colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent), modifier = Modifier.size(56.dp)) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = WearTheme.OnSurface)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { phone.next() }, colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface), modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = WearTheme.OnSurface)
            }
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
private fun GlowingProgressBar(progress: Float, accent: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().height(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(WearTheme.Surface, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier.fillMaxWidth(progress).height(8.dp)
                .background(accent.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
        )
        Box(
            modifier = Modifier.fillMaxWidth(progress).height(3.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
    }
}
