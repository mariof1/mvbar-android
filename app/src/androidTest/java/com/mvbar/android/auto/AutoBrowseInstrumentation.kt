package com.mvbar.android.auto

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.os.Bundle
import com.mvbar.android.ui.verifyLoginVersion
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only probe of legacy browse and Media3 search interfaces used by Android Auto. */
class AutoBrowseInstrumentation : Instrumentation() {
    private var searchOnly = false
    private var loginVersionOnly = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        searchOnly = arguments?.getString("scope") == "search"
        loginVersionOnly = arguments?.getString("scope") == "login-version"
        start()
    }

    override fun onStart() {
        if (loginVersionOnly) {
            try {
                finish(Activity.RESULT_OK, verifyLoginVersion())
            } catch (error: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("error", error.toString()) })
            }
            return
        }
        val report = JSONArray()
        var browser: MediaBrowser? = null
        try {
            val connected = CountDownLatch(1)
            runOnMainSync {
                browser = MediaBrowser(targetContext,
                    ComponentName(targetContext, "com.mvbar.android.player.PlaybackService"),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() { connected.countDown() }
                        override fun onConnectionFailed() { connected.countDown() }
                    }, null)
                browser!!.connect()
            }
            check(connected.await(30, TimeUnit.SECONDS)) { "Browser connection timed out" }
            val client = browser!!
            check(client.isConnected) { "Browser connection rejected" }

            fun children(id: String, page: Int = 0, pageSize: Int = 10): List<MediaBrowser.MediaItem> {
                val loaded = CountDownLatch(1)
                var result: List<MediaBrowser.MediaItem>? = null
                val callback = object : MediaBrowser.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>, options: Bundle) {
                        result = children.toList()
                        loaded.countDown()
                    }
                    override fun onError(parentId: String, options: Bundle) { loaded.countDown() }
                }
                runOnMainSync {
                    client.subscribe(id, Bundle().apply {
                        putInt(MediaBrowser.EXTRA_PAGE, page)
                        putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize)
                    }, callback)
                }
                check(loaded.await(45, TimeUnit.SECONDS)) { "Timed out browsing $id" }
                runOnMainSync { client.unsubscribe(id, callback) }
                return checkNotNull(result) { "Browse failed: $id" }
            }

            val roots = children(client.root, pageSize = 100)
            check(roots.isNotEmpty()) { "Empty root" }
            report.put(JSONObject().put("root", client.root).put("folders", JSONArray(roots.map { it.mediaId })))
            for (folder in roots.filter { it.isBrowsable && !searchOnly }) {
                val items = children(folder.mediaId!!)
                check(items.size <= 10) { "Page size ignored for ${folder.mediaId}: ${items.size}" }
                report.put(JSONObject().put("folder", folder.mediaId).put("count", items.size)
                    .put("titles", JSONArray(items.take(3).map { it.description.title.toString() })))
                check(items.all { !it.mediaId.isNullOrBlank() && !it.description.title.isNullOrBlank() }) {
                    "Missing item metadata in ${folder.mediaId}"
                }
                if (items.isNotEmpty()) {
                    val second = children(folder.mediaId!!, page = 1)
                    check(second.size <= 10) { "Second page size ignored for ${folder.mediaId}" }
                    if (folder.mediaId in listOf("[albums]", "[artists]")) {
                        check(items.map { it.mediaId }.intersect(second.map { it.mediaId }.toSet()).isEmpty()) {
                            "Repeated page for ${folder.mediaId}"
                        }
                    }
                    report.put(JSONObject().put("pagination", folder.mediaId)
                        .put("firstCount", items.size).put("secondCount", second.size)
                        .put("overlap", items.map { it.mediaId }.intersect(second.map { it.mediaId }.toSet()).size))
                }
            }
            var modernFuture: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaBrowser>? = null
            val searchReady = CountDownLatch(1)
            var announcedCount = -1
            runOnMainSync {
                modernFuture = androidx.media3.session.MediaBrowser.Builder(targetContext,
                    androidx.media3.session.SessionToken(targetContext,
                        ComponentName(targetContext, "com.mvbar.android.player.PlaybackService")))
                    .setListener(object : androidx.media3.session.MediaBrowser.Listener {
                        override fun onSearchResultChanged(
                            browser: androidx.media3.session.MediaBrowser,
                            query: String,
                            itemCount: Int,
                            params: androidx.media3.session.MediaLibraryService.LibraryParams?
                        ) {
                            if (query == "love") {
                                announcedCount = itemCount
                                searchReady.countDown()
                            }
                        }
                    }).buildAsync()
            }
            val modern = modernFuture!!.get(30, TimeUnit.SECONDS)
            try {
                runOnMainSync { modern.search("love", null) }
                check(searchReady.await(45, TimeUnit.SECONDS)) { "Search notification timed out" }
                check(announcedCount > 5) { "Search fixture needs more than five results" }
                fun searchPage(page: Int, pageSize: Int = 5): List<androidx.media3.common.MediaItem> {
                    var pending: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.LibraryResult<com.google.common.collect.ImmutableList<androidx.media3.common.MediaItem>>>? = null
                    runOnMainSync { pending = modern.getSearchResult("love", page, pageSize, null) }
                    return checkNotNull(pending!!.get(30, TimeUnit.SECONDS).value) { "Search failed" }
                }
                val first = searchPage(0)
                val second = searchPage(1)
                val overlap = first.map { it.mediaId }.intersect(second.map { it.mediaId }.toSet()).size
                report.put(JSONObject().put("search", "love").put("announcedCount", announcedCount).put("firstCount", first.size)
                    .put("secondCount", second.size).put("overlap", overlap))
                check(first.size <= 5 && second.size <= 5 && overlap == 0) { "Search pagination ignored" }
                val pageCount = (announcedCount + 4) / 5
                val pages = listOf(first, second) + (2 until pageCount).map { searchPage(it) }
                val ids = pages.flatten().map { it.mediaId }
                check(pages.all { it.size <= 5 }) { "Later search page exceeds requested size" }
                check(ids.size == announcedCount && ids.distinct().size == announcedCount) {
                    "Search pages do not match announced result count"
                }
                check(searchPage(pageCount).isEmpty()) { "Search page beyond the end is not empty" }
                check(searchPage(1, 7).map { it.mediaId } == ids.drop(7).take(7)) {
                    "Search ordering changes with page size"
                }
                report.put(JSONObject().put("searchPages", JSONArray(pages.map { it.size }))
                    .put("uniqueResults", ids.distinct().size).put("mixedPageSizesConsistent", true))
            } finally {
                runOnMainSync { modern.release() }
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("report", report.toString()) })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("error", error.toString())
                putString("report", report.toString())
            })
        } finally {
            runOnMainSync { browser?.disconnect() }
        }
    }
}
