package com.mvbar.android.ui.screens.detail

import com.mvbar.android.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailScreenTest {
    @Test
    fun `multi-disc albums are split and ordered by original metadata`() {
        val tracks = listOf(
            Track(id = 22, title = "Disc two, track two", discNumber = 2, trackNumber = 2),
            Track(id = 12, title = "Disc one, track two", discNumber = 1, trackNumber = 2),
            Track(id = 21, title = "Disc two, track one", discNumber = 2, trackNumber = 1),
            Track(id = 11, title = "Disc one, track one", discNumber = 1, trackNumber = 1)
        )

        val sections = splitAlbumTracksByDisc(tracks)

        assertEquals(listOf(1, 2), sections.map { it.discNumber })
        assertEquals(listOf(11, 12), sections[0].tracks.map { it.id })
        assertEquals(listOf(21, 22), sections[1].tracks.map { it.id })
        assertEquals("1", albumTrackNumberLabel(sections[1].tracks[0], fallbackIndex = 0))
        assertEquals("2", albumTrackNumberLabel(sections[1].tracks[1], fallbackIndex = 1))
    }

    @Test
    fun `missing disc and track metadata retains a stable fallback order`() {
        val tracks = listOf(
            Track(id = 1, title = "First"),
            Track(id = 2, title = "Second")
        )

        val sections = splitAlbumTracksByDisc(tracks)

        assertEquals(1, sections.single().discNumber)
        assertEquals(listOf(1, 2), sections.single().tracks.map { it.id })
        assertEquals("1", albumTrackNumberLabel(sections.single().tracks[0], fallbackIndex = 0))
        assertEquals("2", albumTrackNumberLabel(sections.single().tracks[1], fallbackIndex = 1))
    }
}
