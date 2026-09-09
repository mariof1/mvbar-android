package com.mvbar.android.auto

import android.app.Instrumentation
import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.player.PlaybackService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Verifies that a failed server request completes Android Auto search with zero results. */
internal fun Instrumentation.verifyAutoSearchError(): Bundle {
    val originalUrl = ApiClient.getBaseUrl()
    val originalToken = ApiClient.getToken()
    var browser: MediaBrowser? = null
    try {
        ApiClient.configure("http://127.0.0.1:1", originalToken)
        val notified = CountDownLatch(1)
        var count = -1
        val future = MediaBrowser.Builder(
            targetContext,
            SessionToken(targetContext, ComponentName(targetContext, PlaybackService::class.java))
        ).setListener(object : MediaBrowser.Listener {
            override fun onSearchResultChanged(
                browser: MediaBrowser,
                query: String,
                itemCount: Int,
                params: MediaLibraryService.LibraryParams?
            ) {
                if (query == "unreachable") {
                    count = itemCount
                    notified.countDown()
                }
            }
        }).buildAsync()
        browser = future.get(30, TimeUnit.SECONDS)
        runOnMainSync { browser.search("unreachable", null) }
        check(notified.await(10, TimeUnit.SECONDS)) {
            "Failed Android Auto search never notified its controller"
        }
        check(count == 0) { "Failed search announced $count results instead of zero" }
        return Bundle().apply {
            putBoolean("failureNotified", true)
            putInt("announcedCount", count)
        }
    } finally {
        runOnMainSync { browser?.release() }
        ApiClient.configure(originalUrl, originalToken)
    }
}
