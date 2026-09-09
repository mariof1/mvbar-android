package com.mvbar.android.auto

import android.app.Instrumentation
import android.content.ComponentName
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.player.PlaybackService
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Clears a temporary queue, checks persistence, then restores the user's paused queue. */
internal fun Instrumentation.verifyPlaybackSnapshotClear(): Bundle {
    val browser = MediaBrowser.Builder(
        targetContext,
        SessionToken(targetContext, ComponentName(targetContext, PlaybackService::class.java))
    ).buildAsync().get(30, TimeUnit.SECONDS)
    var originalItems = emptyList<MediaItem>()
    var originalIndex = 0
    var originalPosition = 0L
    var originalShuffle = false
    var originalRepeat = Player.REPEAT_MODE_OFF
    try {
        val savedBefore = runBlocking { AaPreferences.getSavedPlaybackState(targetContext) }
        if (savedBefore != null) {
            for (attempt in 0 until 120) {
                var ready = false
                runOnMainSync { ready = browser.mediaItemCount > 0 && browser.playbackState == Player.STATE_READY }
                if (ready) break
                Thread.sleep(250)
            }
        }
        runOnMainSync {
            check(!browser.playWhenReady) { "Refusing to replace an actively playing queue" }
            originalItems = (0 until browser.mediaItemCount).map(browser::getMediaItemAt)
            originalIndex = browser.currentMediaItemIndex.coerceAtLeast(0)
            originalPosition = browser.currentPosition.coerceAtLeast(0L)
            originalShuffle = browser.shuffleModeEnabled
            originalRepeat = browser.repeatMode
        }
        check(originalItems.isNotEmpty()) { "Need a paused queue to verify restoration" }

        val temporary = originalItems[originalItems.lastIndex]
        runOnMainSync {
            browser.shuffleModeEnabled = false
            browser.setMediaItem(temporary)
            browser.prepare()
            browser.pause()
        }
        waitForReady(browser, temporary.mediaId, 0L)
        runOnMainSync { browser.clearMediaItems(); browser.stop() }

        var cleared = false
        for (attempt in 0 until 40) {
            Thread.sleep(250)
            cleared = runBlocking { AaPreferences.getSavedPlaybackState(targetContext) == null }
            if (cleared) break
        }
        check(cleared) { "Cleared queue left a stale playback snapshot" }
        return Bundle().apply { putBoolean("snapshotCleared", true) }
    } finally {
        if (originalItems.isNotEmpty()) {
            runOnMainSync {
                browser.shuffleModeEnabled = false
                browser.setMediaItems(originalItems, originalIndex.coerceAtMost(originalItems.lastIndex), originalPosition)
                browser.prepare()
                browser.pause()
                browser.repeatMode = originalRepeat
                browser.shuffleModeEnabled = originalShuffle
            }
            waitForReady(browser, originalItems[originalIndex.coerceAtMost(originalItems.lastIndex)].mediaId, originalPosition)
            // Allow the asynchronous DataStore snapshot to complete before the
            // instrumentation process exits and the service is reclaimed.
            var restored = false
            for (attempt in 0 until 40) {
                val saved = runBlocking { AaPreferences.getSavedPlaybackState(targetContext) }
                if (saved?.entries?.size == originalItems.size &&
                    saved.entries.getOrNull(saved.index)?.mediaId == originalItems[originalIndex].mediaId &&
                    abs(saved.positionMs - originalPosition) < 1_000L) {
                    restored = true
                    break
                }
                Thread.sleep(250)
            }
            check(restored) { "Restored queue was not persisted" }
        }
        runOnMainSync { browser.release() }
    }
}

private fun Instrumentation.waitForReady(browser: MediaBrowser, mediaId: String, position: Long) {
    var ready = false
    for (attempt in 0 until 120) {
        Thread.sleep(250)
        runOnMainSync {
            ready = browser.playbackState == Player.STATE_READY &&
                browser.currentMediaItem?.mediaId == mediaId &&
                abs(browser.currentPosition - position) < 1_000L
        }
        if (ready) return
    }
    error("Player did not restore $mediaId at $position ms")
}
