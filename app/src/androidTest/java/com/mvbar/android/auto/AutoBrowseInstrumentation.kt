package com.mvbar.android.auto

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only probe of the legacy browser interface used by Android Auto. */
class AutoBrowseInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
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
            for (folder in roots.filter { it.isBrowsable }) {
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
