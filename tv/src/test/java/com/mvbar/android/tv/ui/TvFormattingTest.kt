package com.mvbar.android.tv.ui

import androidx.media3.common.Player
import com.mvbar.android.tv.data.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TvFormattingTest {
    @Test
    fun formatsPlaybackTime() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("1:05", formatPlaybackTime(65_000L))
        assertEquals("1:01:01", formatPlaybackTime(3_661_000L))
    }

    @Test
    fun describesEveryRepeatMode() {
        assertEquals("Repeat off", repeatModeLabel(Player.REPEAT_MODE_OFF))
        assertEquals("Repeat all", repeatModeLabel(Player.REPEAT_MODE_ALL))
        assertEquals("Repeat one", repeatModeLabel(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun identifiesTvGridNavigationEdges() {
        assertEquals(true, isGridLeftEdge(0, 5))
        assertEquals(true, isGridLeftEdge(5, 5))
        assertEquals(false, isGridLeftEdge(1, 5))
        assertEquals(true, isGridTopRow(4, 5))
        assertEquals(false, isGridTopRow(5, 5))
    }

    @Test
    fun pluralizesTrackCount() {
        assertEquals("0 tracks", formatTrackCount(0))
        assertEquals("1 track", formatTrackCount(1))
        assertEquals("2 tracks", formatTrackCount(2))
    }

    @Test
    fun formatsDiscAndTrackNumbers() {
        assertEquals("2.4", formatTrackNumber(Track(discNumber = 2, trackNumber = 4)))
        assertEquals("7", formatTrackNumber(Track(trackNumber = 7)))
        assertEquals("•", formatTrackNumber(Track()))
    }
}
