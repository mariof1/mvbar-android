package com.mvbar.android.wear.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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

/**
 * Now Playing — visual-first. Big blurred art fills the screen; centered
 * artwork + title + artist on top of a scrim. Single primary play/pause,
 * prev/next at the sides, secondary actions (favorite · queue · volume)
 * in a compact row at the bottom. Volume opens the system volume sheet
 * which already has rotary support.
 */
@Composable
fun WearNowPlayingScreen(onOpenQueue: () -> Unit) {
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
            "Nothing is playing",
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
            is PlayableItem.PodcastEp -> backend.artworkUrl(
                item.episode.imagePath
                    ?: item.episode.podcastImagePath
                    ?: item.episode.imageUrl
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        // Blurred background
        if (artUrl != null && Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(36.dp).alpha(0.45f)
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)))

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: artwork + title/artist
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    item.title,
                    color = WearTheme.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.caption1,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.subtitle,
                    color = WearTheme.OnSurfaceDim,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Middle: progress + transport
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val dur = state.durationMs.coerceAtLeast(1)
                val progress = (state.positionMs.toFloat() / dur).coerceIn(0f, 1f)
                GlowingProgressBar(progress, accent)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(state.positionMs), color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption3)
                    Text(formatMs(state.durationMs), color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption3)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { WearPlayerHolder.previous() },
                        enabled = state.hasPrevious,
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0x40FFFFFF)),
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    ) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = WearTheme.OnSurface) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { WearPlayerHolder.togglePlayPause() },
                        colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent),
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = WearTheme.OnSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { WearPlayerHolder.next() },
                        enabled = state.hasNext,
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0x40FFFFFF)),
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    ) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = WearTheme.OnSurface) }
                }
            }

            // Bottom: secondary actions
            SecondaryActionsRow(
                state = state,
                isMusic = item is PlayableItem.Music,
                onToggleFavorite = {
                    val music = item as? PlayableItem.Music ?: return@SecondaryActionsRow
                    val newFav = !state.isFavorite
                    WearPlayerHolder.setFavoriteLocal(newFav)
                    kotlinx.coroutines.MainScope().launch { backend.setFavorite(music.track.id, newFav) }
                },
                onOpenQueue = onOpenQueue
            )
        }
    }
}

@Composable
private fun SecondaryActionsRow(
    state: WearPlayerHolder.State,
    isMusic: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMusic) {
            SmallCircleAction(
                icon = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                tint = if (state.isFavorite) WearTheme.Pink else WearTheme.OnSurface,
                description = "Favorite",
                onClick = onToggleFavorite
            )
        }
        SmallCircleAction(
            icon = Icons.Default.VolumeUp,
            tint = WearTheme.OnSurface,
            description = "Volume",
            onClick = {
                // Bring up the system volume slider — supports rotary on Wear OS.
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        )
        SmallCircleAction(
            icon = Icons.Default.QueueMusic,
            tint = WearTheme.OnSurface,
            description = "Queue",
            onClick = onOpenQueue
        )
    }
}

@Composable
private fun SmallCircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    description: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0x40FFFFFF)),
        modifier = Modifier.size(32.dp).clip(CircleShape)
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RemoteNowPlaying(state: NowPlayingState, phone: PhoneCommandClient) {
    val accent = if (state.isPodcast || state.isAudiobook) WearTheme.Orange else WearTheme.Cyan
    Column(
        modifier = Modifier.fillMaxSize().background(WearTheme.Background)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Playing on phone", color = accent, style = MaterialTheme.typography.caption2, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(state.title, color = WearTheme.OnSurface, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text(state.artist, color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { phone.previous() }, colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface), modifier = Modifier.size(40.dp).clip(CircleShape)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = WearTheme.OnSurface)
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = { phone.playPause() }, colors = ButtonDefaults.primaryButtonColors(backgroundColor = accent), modifier = Modifier.size(56.dp).clip(CircleShape)) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = WearTheme.OnSurface)
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = { phone.next() }, colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface), modifier = Modifier.size(40.dp).clip(CircleShape)) {
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
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)
            .background(Color(0x33FFFFFF), RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.fillMaxWidth(progress).height(8.dp)
            .background(accent.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
        Box(modifier = Modifier.fillMaxWidth(progress).height(3.dp)
            .background(accent, RoundedCornerShape(2.dp)))
    }
}
