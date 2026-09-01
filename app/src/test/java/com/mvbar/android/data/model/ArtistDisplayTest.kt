package com.mvbar.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDisplayTest {
    @Test
    fun `track credits are complete ordered and preferred`() {
        val track = Track(
            artist = "Legacy Artist",
            displayArtistName = "API Artist",
            artists = listOf(
                ArtistCredit(id = 2, name = "Artist B"),
                ArtistCredit(id = 1, name = "Artist A")
            )
        )

        assertEquals("Artist B • Artist A", track.displayArtist)
    }

    @Test
    fun `commas ampersands and bare slashes stay inside artist names`() {
        assertEquals(
            "Earth, Wind & Fire • AC/DC",
            formatArtistDisplay("Earth, Wind & Fire; AC/DC")
        )
    }

    @Test
    fun `album surfaces prefer the complete album artist credit`() {
        val album = Album(
            artist = "Track Artist",
            displayArtist = "Album Artist A; Album Artist B"
        )

        assertEquals("Album Artist A • Album Artist B", album.artistDisplay)
    }
}

