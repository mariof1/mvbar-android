package com.mvbar.android.ui

import android.app.Application
import android.app.Instrumentation
import android.os.Bundle
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.viewmodel.PodcastViewModel
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the real Retrofit/ViewModel path against an isolated delayed HTTP server. */
internal fun Instrumentation.verifyPodcastPreviewCancellation(): Bundle {
    val originalUrl = ApiClient.getBaseUrl()
    val originalToken = ApiClient.getToken()
    val server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
    val firstAccepted = CountDownLatch(1)
    val serverThread = Thread {
        try {
            while (!server.isClosed) {
                val socket = server.accept()
                Thread { respondToPreview(socket, firstAccepted) }.apply { isDaemon = true }.start()
            }
        } catch (_: Exception) {
            // Closing the server ends the accept loop.
        }
    }.apply { isDaemon = true; start() }
    var viewModel: PodcastViewModel? = null
    try {
        runOnMainSync {
            viewModel = PodcastViewModel(targetContext.applicationContext as Application)
        }
        // Application session restoration also configures ApiClient during startup.
        // Let it settle before swapping in this process-local fixture endpoint.
        Thread.sleep(2_000)
        ApiClient.configure("http://127.0.0.1:${server.localPort}", null)
        runOnMainSync { viewModel!!.previewPodcast("https://fixture/first") }
        check(firstAccepted.await(10, TimeUnit.SECONDS)) { "The delayed preview request did not reach the fixture server" }
        runOnMainSync { viewModel!!.previewPodcast("https://fixture/second") }
        waitForPreview("Second", viewModel!!)
        Thread.sleep(1_200)
        check(viewModel!!.preview.value?.title == "Second") { "A cancelled preview replaced the current result" }

        runOnMainSync { viewModel!!.previewPodcast("https://fixture/first") }
        Thread.sleep(100)
        runOnMainSync { viewModel!!.clearPreview() }
        Thread.sleep(1_200)
        check(viewModel!!.preview.value == null) { "A closed preview dialog received a late result" }
        check(viewModel!!.previewError.value == null && !viewModel!!.previewLoading.value) {
            "Clearing preview left stale error/loading state"
        }
        return Bundle().apply {
            putString("result", "Latest preview won; closing cancelled delayed work without stale result, error, or loading state")
        }
    } finally {
        viewModel?.let { instance -> runOnMainSync { instance.clearPreview() } }
        ApiClient.configure(originalUrl, originalToken)
        server.close()
        serverThread.join(1_000)
    }
}

private fun waitForPreview(title: String, viewModel: PodcastViewModel) {
    repeat(100) {
        if (viewModel.preview.value?.title == title && !viewModel.previewLoading.value) return
        Thread.sleep(50)
    }
    error("Preview '$title' did not complete: preview=${viewModel.preview.value} error=${viewModel.previewError.value}")
}

private fun respondToPreview(socket: Socket, firstAccepted: CountDownLatch) {
    socket.use {
        it.soTimeout = 5_000
        val reader = it.getInputStream().bufferedReader()
        val request = reader.readLine().orEmpty()
        while (!reader.readLine().isNullOrEmpty()) Unit
        val delayed = request.contains("first")
        if (delayed) {
            firstAccepted.countDown()
            Thread.sleep(800)
        }
        val title = if (delayed) "First" else "Second"
        val body = """{"ok":true,"preview":{"title":"$title"}}"""
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
        try {
            it.getOutputStream().write(response.toByteArray())
            it.getOutputStream().flush()
        } catch (_: Exception) {
            // Expected when Retrofit cancellation closes the first socket.
        }
    }
}
