package com.mvbar.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBrowserPageTest {
    @Test fun pagesDoNotRepeatAndLastPageCanBeShort() {
        val items = (0 until 23).toList()
        assertEquals((0 until 10).toList(), mediaBrowserPage(items, 0, 10))
        assertEquals((10 until 20).toList(), mediaBrowserPage(items, 1, 10))
        assertEquals(listOf(20, 21, 22), mediaBrowserPage(items, 2, 10))
        assertTrue(mediaBrowserPage(items, 3, 10).isEmpty())
    }

    @Test fun unboundedLegacyRequestRetainsAllItems() {
        assertEquals(listOf(1, 2, 3), mediaBrowserPage(listOf(1, 2, 3), 0, Int.MAX_VALUE))
    }

    @Test fun invalidAndOverflowingRequestsAreEmpty() {
        val items = listOf(1, 2)
        assertTrue(mediaBrowserPage(items, -1, 10).isEmpty())
        assertTrue(mediaBrowserPage(items, 0, 0).isEmpty())
        assertTrue(mediaBrowserPage(items, Int.MAX_VALUE, Int.MAX_VALUE).isEmpty())
    }
}
