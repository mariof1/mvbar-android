package com.mvbar.android.auto

import android.app.Instrumentation
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.LibraryResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Appends a paused test queue, then removes it without replacing the user's queue. */
internal fun Instrumentation.verifyAutoQueueContext(browser: MediaBrowser, idOnly: Boolean): JSONObject {
    fun items(request: () -> ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>): List<MediaItem> {
        var future: ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>? = null
        runOnMainSync { future = request() }
        return checkNotNull(future!!.get(30, TimeUnit.SECONDS).value)
    }
    val results = items { browser.getSearchResult("love", 0, 100, null) }
    val song = results.first { it.mediaId.toIntOrNull()?.let { id -> id > 0 } == true && !it.mediaMetadata.albumTitle.isNullOrBlank() }
    val album = items { browser.getChildren("album:${song.mediaMetadata.albumTitle}", 0, 1000, null) }
        .filter { it.mediaId.toIntOrNull() != null }
    val index = album.indexOfFirst { it.mediaId == song.mediaId }
    check(index >= 0 && album.size > 1) { "Need a multi-track album containing the search song" }
    val expected = (album.drop(index) + album.take(index)).map { it.mediaId }
    // Explicit item metadata must survive even if a later search refresh changes the fallback.
    if (!idOnly) items { browser.getSearchResult("love", 0, 100, null) }
    var originalIds = emptyList<String>()
    var originalShuffle = false
    runOnMainSync {
        check(!browser.playWhenReady) { "Refusing to modify an actively playing queue" }
        originalIds = (0 until browser.mediaItemCount).map { browser.getMediaItemAt(it).mediaId }
        originalShuffle = browser.shuffleModeEnabled
    }
    var actual = emptyList<String>()
    try {
        val selected = if (idOnly) MediaItem.Builder().setMediaId(song.mediaId).build() else album[index]
        runOnMainSync { browser.addMediaItem(selected) }
        // A controller initially masks the request as one item; wait for the service expansion.
        for (attempt in 0 until 30) {
            Thread.sleep(500)
            runOnMainSync {
                actual = (originalIds.size until browser.mediaItemCount).map { browser.getMediaItemAt(it).mediaId }
            }
            if (actual.size > 1) break
        }
    } finally {
        runOnMainSync {
            check((0 until originalIds.size).map { browser.getMediaItemAt(it).mediaId } == originalIds) {
                "Original queue changed during probe"
            }
            browser.removeMediaItems(originalIds.size, browser.mediaItemCount)
            browser.shuffleModeEnabled = originalShuffle
        }
    }
    check(actual == expected) { "Wrong album queue: expected=$expected actual=$actual" }
    return JSONObject().put("idOnly", idOnly).put("song", song.mediaMetadata.title).put("album", song.mediaMetadata.albumTitle)
        .put("expected", JSONArray(expected)).put("actual", JSONArray(actual))
        .put("matches", actual == expected)
}
