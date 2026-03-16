package com.mvbar.android.ui.screens.nowplaying

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.player.PlayMode
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.theme.*

@Composable
fun NowPlayingScreen(
    state: PlayerState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val track = state.currentTrack ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Cyan900.copy(alpha = 0.4f), BackgroundDark, BackgroundDark)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = OnSurface, modifier = Modifier.size(32.dp))
                }
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurfaceDim
                )
                IconButton(onClick = { }) {
                    Icon(Icons.Filled.MoreVert, "More", tint = OnSurfaceDim)
                }
            }

            Spacer(Modifier.weight(0.5f))

            // Album art
            AsyncImage(
                model = ApiClient.trackArtUrl(track.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .shadow(24.dp, RoundedCornerShape(20.dp))
            )

            Spacer(Modifier.height(32.dp))

            // Track info
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                track.displayAlbum,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Progress bar
            val progress = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f
            Slider(
                value = progress,
                onValueChange = { onSeek((it * state.duration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Cyan500,
                    activeTrackColor = Cyan500,
                    inactiveTrackColor = WhiteOverlay15
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(state.position), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                Text(formatTime(state.duration), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
            }

            Spacer(Modifier.height(16.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCyclePlayMode) {
                    Icon(
                        when (state.playMode) {
                            PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                            PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        "Play Mode",
                        tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(36.dp))
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(72.dp)
                        .background(Cyan500, CircleShape)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(36.dp))
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Favorite",
                        tint = if (state.isFavorite) Pink500 else OnSurfaceDim,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
