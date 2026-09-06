package com.mvbar.android.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SearchContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun readsServerSearchResponseWithAudiobooksAndSongs() {
        val result = json.decodeFromString<SearchResults>("""{"ok":true,"hits":[{"id":7,"title":"Song"}],"audiobooks":[{"id":42,"title":"1984","author":"George Orwell","has_cover":true}]}""")
        assertEquals(7, result.hits.single().id)
        assertEquals("George Orwell", result.audiobooks.single().author)
        assertEquals(42, result.audiobooks.single().id)
    }
    @Test fun olderServerResponsesRemainReadable() {
        val result = json.decodeFromString<SearchResults>("""{"ok":true,"hits":[]}""")
        assertTrue(result.audiobooks.isEmpty())
        assertFalse(result.indexing)
    }
    @Test fun onlyActiveScanningAndIndexingShowPartialResults() {
        assertTrue(json.decodeFromString<ScanProgress>("""{"ok":true,"status":"indexing","filesProcessed":10}""").active)
        assertTrue(ScanProgress("scanning").active)
        assertFalse(ScanProgress("idle").active)
        assertFalse(ScanProgress("unknown").active)
    }
}
