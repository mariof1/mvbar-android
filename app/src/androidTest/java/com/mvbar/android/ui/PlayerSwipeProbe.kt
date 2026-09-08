package com.mvbar.android.ui

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mvbar.android.MainActivity
import com.mvbar.android.data.model.Track
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.screens.nowplaying.NowPlayingScreen
import com.mvbar.android.ui.theme.MvbarTheme
import kotlin.math.abs

/** Uses the actual player with isolated callbacks: never changes the user's playback or queue. */
internal fun Instrumentation.verifyPlayerSwipe(): Bundle {
    val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
    val tracks = (1..30).map { Track(id = it, title = "Swipe fixture $it", artist = "Fixture") }
    val state = mutableStateOf(PlayerState(currentTrack = tracks.first(), queue = tracks, queueIndex = 0))
    var expanded = false
    var dismissed = false
    var blur = 0f
    var seeks = 0
    try {
        runOnMainSync { activity.setContent { MvbarTheme {
            NowPlayingScreen(state.value, onBack = { dismissed = true }, onTogglePlay = {},
                onNext = {}, onPrevious = {}, onSeek = { seeks++ }, onCyclePlayMode = {}, onToggleFavorite = {},
                onQueueOpenChanged = { expanded = it }, onBackdropBlurChanged = { blur = it },
                onPlayQueueItem = { state.value = state.value.copy(currentTrack = tracks[it], queueIndex = it) })
        } } }
        waitForIdleSync()
        repeat(40) {
            if (uiAutomation.rootInActiveWindow == null) SystemClock.sleep(250)
        }
        SystemClock.sleep(500)
        fun bounds(label: String): Rect {
            fun find(node: AccessibilityNodeInfo?): Rect? {
                if (node == null) return null
                node.refresh()
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) return Rect().also { node.getBoundsInScreen(it) }
                for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                return null
            }
            return find(uiAutomation.rootInActiveWindow) ?: error("Missing $label")
        }
        fun awaitState(message: String, predicate: () -> Boolean) {
            repeat(30) {
                var ready = false
                runOnMainSync { ready = predicate() }
                if (ready) return
                SystemClock.sleep(100)
            }
            error(message)
        }
        val density = targetContext.resources.displayMetrics.density
        val screen = Rect().also { uiAutomation.rootInActiveWindow.getBoundsInScreen(it) }
        fun drag(startY: Float, deltasDp: List<Float>, held: ((Int) -> Unit)? = null) {
            val down = SystemClock.uptimeMillis()
            val x = screen.exactCenterX()
            fun event(action: Int, y: Float) {
                val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                e.source = InputDevice.SOURCE_TOUCHSCREEN
                check(uiAutomation.injectInputEvent(e, true)); e.recycle()
            }
            event(MotionEvent.ACTION_DOWN, startY)
            var previous = 0f
            deltasDp.forEachIndexed { index, delta ->
                for (step in 1..12) {
                    event(MotionEvent.ACTION_MOVE, startY + (previous + (delta - previous) * step / 12f) * density)
                    SystemClock.sleep(16)
                }
                SystemClock.sleep(80)
                held?.invoke(index)
                previous = delta
            }
            event(MotionEvent.ACTION_UP, startY + previous * density)
            SystemClock.sleep(450)
        }
        val title = bounds("Swipe fixture 1")
        val start = title.exactCenterY()
        drag(start, listOf(-180f, -30f)) { step ->
            val moved = (start - bounds("Swipe fixture 1").exactCenterY()) / density
            check(abs(moved - if (step == 0) 180f else 30f) < 20) { "Held reversal jumped: $moved" }
            check(!expanded)
        }
        check(!expanded && !dismissed)
        drag(start, listOf(-350f))
        awaitState("Queue did not expand") { expanded && !dismissed }
        // Header remains available while the list scrolls independently below it.
        val header = bounds("Queue")
        check(header.top < screen.height() * 0.4f) { "Queue does not occupy three quarters: $header" }
        val rowBefore = bounds("Swipe fixture 6").top
        drag(screen.height() * 0.8f, listOf(-250f))
        check(expanded && !dismissed)
        check(bounds("Swipe fixture 6").top < rowBefore - 100 * density) { "Queue did not scroll independently" }
        val selected = bounds("Swipe fixture 6")
        val tapTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(tapTime, SystemClock.uptimeMillis(), action, selected.exactCenterX(), selected.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        awaitState("Queue selection changed the surface or did not select the track") { state.value.currentTrack == tracks[5] && expanded && !dismissed }

        drag(header.exactCenterY(), listOf(350f))
        awaitState("Down from queue should only restore player") { !expanded && !dismissed }
        drag(start, listOf(80f, 20f)) { step ->
            check(blur > if (step == 0) 30f else 37f) { "Backdrop cleared too quickly: $blur" }
        }
        awaitState("Cancelled pull did not restore blur") { !dismissed && abs(blur - 40f) < 0.1f }
        check(seeks == 0) { "Surface drag changed playback position" }
        val seekbar = bounds("Playback position")
        val seekTime = SystemClock.uptimeMillis()
        for ((action, x) in listOf(MotionEvent.ACTION_DOWN to seekbar.left + 30f, MotionEvent.ACTION_MOVE to seekbar.exactCenterX(), MotionEvent.ACTION_UP to seekbar.exactCenterX())) {
            val event = MotionEvent.obtain(seekTime, SystemClock.uptimeMillis(), action, x, seekbar.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(80)
        }
        awaitState("Seek gesture was intercepted by the surface") { seeks == 1 && !expanded && !dismissed }
        drag(start, listOf(115f))
        awaitState("Short downward swipe did not dismiss") { dismissed }
        check(seeks == 1) { "Closing drag changed playback position" }
        return Bundle().apply { putString("result", "Held reversal, nearest snap, 75% queue, nested scrolling, queue selection, independent seeking, two-step dismissal, progressive blur and short closing swipe passed") }
    } catch (error: Throwable) {
        uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(targetContext.getExternalFilesDir(null), "player-swipe-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        throw error
    } finally { runOnMainSync { activity.finish() } }
}
