package com.mvbar.android.player

import com.mvbar.android.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class AudiobookProgressIdentityTest {
    private fun state(id: Int) = PlayerState(currentTrack = Track(id = id), isAudiobookMode = true)

    @Test fun knownAudiobookChapterCanSaveProgress() {
        assertEquals(7, audiobookProgressChapterId(2, listOf(7), state(-200007)))
    }

    @Test fun podcastCannotSaveAudiobookProgressEvenWithCollidingId() {
        assertNull(audiobookProgressChapterId(2, listOf(7), state(-200007).copy(isPodcastModeOverride = true)))
        assertNull(audiobookProgressChapterId(2, listOf(7), state(-200007).copy(isAudiobookMode = false)))
    }

    @Test fun otherPlaybackAndUnknownChaptersAreRejected() {
        assertNull(audiobookProgressChapterId(2, listOf(7), state(-200008)))
        assertNull(audiobookProgressChapterId(2, listOf(7), state(-300007)))
        assertNull(audiobookProgressChapterId(2, listOf(7), state(7)))
        assertNull(audiobookProgressChapterId(2, listOf(7), PlayerState()))
    }
}
