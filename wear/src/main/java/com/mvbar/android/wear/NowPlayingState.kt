package com.mvbar.android.wear

import com.mvbar.android.shared.WearProtocol

data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isPodcast: Boolean = false,
    val isAudiobook: Boolean = false,
    val favorite: Boolean = false,
    val artworkUrl: String? = null,
    /** Wall-clock millis of the last position update, used to interpolate. */
    val timestamp: Long = 0L,
) {
    val isEmpty: Boolean get() = title.isEmpty() && artist.isEmpty()
}

internal fun com.google.android.gms.wearable.DataMap.toNowPlayingState() = NowPlayingState(
    title = getString(WearProtocol.KEY_TITLE).orEmpty(),
    artist = getString(WearProtocol.KEY_ARTIST).orEmpty(),
    album = getString(WearProtocol.KEY_ALBUM).orEmpty(),
    durationMs = getLong(WearProtocol.KEY_DURATION_MS, 0L),
    positionMs = getLong(WearProtocol.KEY_POSITION_MS, 0L),
    isPlaying = getBoolean(WearProtocol.KEY_IS_PLAYING, false),
    isPodcast = getBoolean(WearProtocol.KEY_IS_PODCAST, false),
    isAudiobook = getBoolean(WearProtocol.KEY_IS_AUDIOBOOK, false),
    favorite = getBoolean(WearProtocol.KEY_FAVORITE, false),
    artworkUrl = getString(WearProtocol.KEY_ARTWORK),
    timestamp = getLong(WearProtocol.KEY_TIMESTAMP, 0L),
)
