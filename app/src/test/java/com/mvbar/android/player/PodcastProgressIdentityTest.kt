package com.mvbar.android.player

import com.mvbar.android.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class PodcastProgressIdentityTest {
    private val episode = PlayerState(currentTrack = Track(id = -200007), isPlaying = true, position = 15000)

    @Test fun matchingPlayingEpisodeCanSave() {
        assertTrue(canSavePodcastProgress(200007, episode))
    }

    @Test fun audiobookWithTheSameNumericIdCannotSavePodcastProgress() {
        assertFalse(canSavePodcastProgress(200007, episode.copy(isAudiobookMode = true)))
    }

    @Test fun unrelatedOrInactivePlaybackCannotSave() {
        assertFalse(canSavePodcastProgress(200008, episode))
        assertFalse(canSavePodcastProgress(200007, episode.copy(isPlaying = false)))
        assertFalse(canSavePodcastProgress(200007, episode.copy(position = 0)))
        assertFalse(canSavePodcastProgress(0, episode))
        assertFalse(canSavePodcastProgress(200007, PlayerState()))
    }
}
