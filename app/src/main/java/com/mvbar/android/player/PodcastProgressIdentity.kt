package com.mvbar.android.player

internal fun canSavePodcastProgress(episodeId: Int, state: PlayerState): Boolean =
    episodeId > 0 && state.isPodcastMode && state.isPlaying && state.position > 0 &&
        state.currentTrack?.id == -episodeId
