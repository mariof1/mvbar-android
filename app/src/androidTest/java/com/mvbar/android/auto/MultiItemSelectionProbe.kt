package com.mvbar.android.auto

import android.app.Instrumentation
import android.content.ComponentName
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.mvbar.android.player.PlaybackService
import java.util.concurrent.TimeUnit

/** Checks that resolving a list preserves the controller's requested start index. */
internal fun Instrumentation.verifyMultiItemSelection(): Bundle {
    var browser: MediaBrowser? = null
    var originalItems = emptyList<MediaItem>()
    var originalIndex = 0
    var originalPosition = 0L
    var originalShuffle = false
    var originalRepeat = Player.REPEAT_MODE_OFF
    try {
        browser = MediaBrowser.Builder(
            targetContext,
            SessionToken(targetContext, ComponentName(targetContext, PlaybackService::class.java))
        ).buildAsync().get(30, TimeUnit.SECONDS)
        runOnMainSync {
            check(!browser.playWhenReady) { "Refusing to replace an actively playing queue" }
            originalItems = (0 until browser.mediaItemCount).map(browser::getMediaItemAt)
            originalIndex = browser.currentMediaItemIndex.coerceAtLeast(0)
            originalPosition = browser.currentPosition.coerceAtLeast(0L)
            originalShuffle = browser.shuffleModeEnabled
            originalRepeat = browser.repeatMode
        }

        var favoritesFuture: com.google.common.util.concurrent.ListenableFuture<
            androidx.media3.session.LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>
        >? = null
        runOnMainSync { favoritesFuture = browser.getChildren("[favorites]", 0, 100, null) }
        val favorites = favoritesFuture!!.get(45, TimeUnit.SECONDS).value.orEmpty()
            .filter { it.mediaId.toIntOrNull() != null }
        check(favorites.size >= 3) { "Need at least three favourites for the selection probe" }
        val selectedIndex = 2
        val expectedId = favorites[selectedIndex].mediaId
        runOnMainSync {
            browser.shuffleModeEnabled = false
            browser.setMediaItems(favorites, selectedIndex, 0L)
            browser.prepare()
            browser.pause()
        }
        var actualId: String? = null
        var playbackState = Player.STATE_IDLE
        for (attempt in 0 until 40) {
            Thread.sleep(250)
            runOnMainSync {
                actualId = browser.currentMediaItem?.mediaId
                playbackState = browser.playbackState
            }
            if (actualId != null && playbackState != Player.STATE_BUFFERING) break
        }
        check(actualId == expectedId) {
            "Requested favourite $expectedId at $selectedIndex but selected $actualId"
        }
        return Bundle().apply {
            putInt("itemCount", favorites.size)
            putInt("selectedIndex", selectedIndex)
            putString("expectedId", expectedId)
            putString("actualId", actualId)
        }
    } finally {
        browser?.let { controller ->
            runOnMainSync {
                controller.shuffleModeEnabled = false
                if (originalItems.isEmpty()) {
                    controller.clearMediaItems()
                    controller.stop()
                } else {
                    controller.setMediaItems(originalItems, originalIndex.coerceAtMost(originalItems.lastIndex), originalPosition)
                    controller.prepare()
                    controller.pause()
                }
                controller.repeatMode = originalRepeat
                controller.shuffleModeEnabled = originalShuffle
                controller.release()
            }
        }
    }
}
