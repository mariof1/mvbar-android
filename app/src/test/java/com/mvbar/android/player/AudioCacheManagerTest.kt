package com.mvbar.android.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCacheManagerTest {
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
