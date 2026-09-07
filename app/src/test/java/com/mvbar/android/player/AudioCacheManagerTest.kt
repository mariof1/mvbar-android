package com.mvbar.android.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioCacheManagerTest {
    @Test
    fun cachedMusicFromAnotherServerIsNotAvailableOffline() {
        assertEquals(42, cachedTrackIdForServer("https://a.test/api/library/tracks/42/stream", "https://a.test/"))
        assertNull(cachedTrackIdForServer("https://a.test/api/library/tracks/42/stream", "https://b.test/"))
        assertNull(cachedTrackIdForServer("http://a.test/api/library/tracks/42/stream", "https://a.test/"))
        assertNull(cachedTrackIdForServer("https://a.test:8080/api/library/tracks/42/stream", "https://a.test/"))
    }

    @Test
    fun cachedMusicRespectsServerBasePath() {
        val key = "https://a.test/music/api/library/tracks/42/stream"
        assertEquals(42, cachedTrackIdForServer(key, "https://a.test/music"))
        assertNull(cachedTrackIdForServer(key, "https://a.test/"))
        assertNull(cachedTrackIdForServer(key, "https://a.test/other/"))
    }

    @Test
    fun invalidAndNonMusicKeysAreNotTrackIds() {
        val base = "https://a.test/"
        assertNull(cachedTrackIdForServer("${base}api/podcasts/episodes/42/stream", base))
        assertNull(cachedTrackIdForServer("${base}api/library/tracks/-1/stream", base))
        assertNull(cachedTrackIdForServer("${base}api/library/tracks/0/stream", base))
        assertNull(cachedTrackIdForServer("${base}api/library/tracks/42/art", base))
        assertNull(cachedTrackIdForServer("${base}api/library/tracks/42/stream", ""))
    }

    @Test
    fun partialContentIsNotComplete() {
        assertFalse(isCompleteCacheEntry(contentLength = 10_000, cachedBytes = 2_000, rangeCached = false))
    }

    @Test
    fun unknownContentLengthIsNotComplete() {
        assertFalse(isCompleteCacheEntry(contentLength = null, cachedBytes = 2_000, rangeCached = true))
    }

    @Test
    fun completeContiguousContentIsComplete() {
        assertTrue(isCompleteCacheEntry(contentLength = 10_000, cachedBytes = 10_000, rangeCached = true))
    }

    @Test
    fun byteCountAloneDoesNotHideACacheGap() {
        assertFalse(isCompleteCacheEntry(contentLength = 10_000, cachedBytes = 10_000, rangeCached = false))
    }
}
