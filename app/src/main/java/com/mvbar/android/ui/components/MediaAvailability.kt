package com.mvbar.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.theme.Cyan500
import com.mvbar.android.ui.theme.Orange400

enum class MediaAvailability(val isPlayable: Boolean) {
    Online(true),
    Cached(true),
    Unavailable(false)
}

@Composable
fun trackAvailability(trackId: Int): MediaAvailability {
    val isOnline = LocalIsOnline.current
    val cacheRevision by AudioCacheManager.cacheRevision.collectAsState()
    return remember(trackId, isOnline, cacheRevision) {
        val isCached = if (trackId > 0) {
            AudioCacheManager.isTrackCached(trackId)
        } else {
            AudioCacheManager.isEpisodeCached(-trackId)
        }
        mediaAvailability(isOnline = isOnline, isCached = isCached)
    }
}

@Composable
fun episodeAvailability(episodeId: Int): MediaAvailability {
    val isOnline = LocalIsOnline.current
    val cacheRevision by AudioCacheManager.cacheRevision.collectAsState()
    return remember(episodeId, isOnline, cacheRevision) {
        mediaAvailability(
            isOnline = isOnline,
            isCached = AudioCacheManager.isEpisodeCached(episodeId)
        )
    }
}

@Composable
fun chapterAvailability(audiobookId: Int, chapterId: Int): MediaAvailability {
    val isOnline = LocalIsOnline.current
    val cacheRevision by AudioCacheManager.cacheRevision.collectAsState()
    return remember(audiobookId, chapterId, isOnline, cacheRevision) {
        mediaAvailability(
            isOnline = isOnline,
            isCached = AudioCacheManager.isChapterCached(audiobookId, chapterId)
        )
    }
}

private fun mediaAvailability(isOnline: Boolean, isCached: Boolean): MediaAvailability =
    when {
        isCached -> MediaAvailability.Cached
        isOnline -> MediaAvailability.Online
        else -> MediaAvailability.Unavailable
    }

@Composable
fun AvailabilityBadge(
    availability: MediaAvailability,
    modifier: Modifier = Modifier,
    cachedLabel: String = "Cached",
    unavailableLabel: String = "Offline"
) {
    if (availability == MediaAvailability.Online) return

    val isCached = availability == MediaAvailability.Cached
    Text(
        text = if (isCached) cachedLabel else unavailableLabel,
        modifier = modifier
            .background(
                color = if (isCached) Cyan500.copy(alpha = 0.16f) else Orange400.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50)
            )
            .padding(PaddingValues(horizontal = 7.dp, vertical = 2.dp)),
        color = if (isCached) Cyan500 else Orange400,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}
