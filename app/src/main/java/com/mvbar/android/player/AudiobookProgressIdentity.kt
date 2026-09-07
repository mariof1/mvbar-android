package com.mvbar.android.player

internal fun audiobookProgressChapterId(
    audiobookId: Int,
    chapterIds: Collection<Int>,
    state: PlayerState
): Int? {
    if (!state.isAudiobookMode || state.isPodcastMode) return null
    val trackId = state.currentTrack?.id ?: return null
    if (trackId >= 0) return null
    val chapterId = -trackId.toLong() - audiobookId.toLong() * 100000L
    return chapterId.takeIf { it in 1..Int.MAX_VALUE.toLong() }
        ?.toInt()?.takeIf { it in chapterIds }
}
