package com.mvbar.android.ui

import android.content.Intent
import android.app.Instrumentation
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mvbar.android.MainActivity
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.screens.favorites.FavoritesScreen
import com.mvbar.android.ui.theme.MvbarTheme
import kotlin.math.abs

/** Real touch events against an isolated list: no playback, API calls, or saved-order changes. */
internal fun Instrumentation.verifyFavoritesDrag(): Bundle {
    val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
    val tracks = mutableStateOf((1..30).map { Track(id = it, title = "Drag probe $it", artist = "Fixture") })
    var saves = 0
    var refreshes = 0
    try {
        runOnMainSync {
            activity.setContent { MvbarTheme {
                FavoritesScreen(tracks.value, null, { _, _ -> }, {}, { refreshes++ }, { id, before ->
                    val moved = tracks.value.first { it.id == id }
                    val next = tracks.value.filterNot { it.id == id }.toMutableList()
                    next.add(if (before == null) next.size else next.indexOfFirst { it.id == before }, moved)
                    tracks.value = next
                    saves++
                })
            } }
        }
        SystemClock.sleep(1200)
        fun bounds(label: String): Rect {
            fun find(node: AccessibilityNodeInfo?): Rect? {
                if (node == null) return null
                if (node.contentDescription?.toString() == label) return Rect().also { node.getBoundsInScreen(it) }
                for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                return null
            }
            for (attempt in 0..15) {
                find(uiAutomation.rootInActiveWindow)?.let { return it }
                SystemClock.sleep(100)
            }
            fun labels(node: AccessibilityNodeInfo?): String = if (node == null) "null" else "[${node.text}|${node.contentDescription}]" + (0 until node.childCount).joinToString { labels(node.getChild(it)) }
            error("Missing $label: ${labels(uiAutomation.rootInActiveWindow)}")
        }
        val first = bounds("Reorder Drag probe 1")
        val second = bounds("Reorder Drag probe 2")
        val rowHeight = second.centerY() - first.centerY()
        fun drag(destination: Float) {
            val start = bounds("Reorder Drag probe 1")
            val down = SystemClock.uptimeMillis()
            fun event(action: Int, y: Float) {
                val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, start.centerX().toFloat(), y, 0)
                e.source = InputDevice.SOURCE_TOUCHSCREEN
                check(uiAutomation.injectInputEvent(e, true))
                e.recycle()
            }
            event(MotionEvent.ACTION_DOWN, start.exactCenterY())
            SystemClock.sleep(650)
            for (step in 1..24) {
                val y = start.exactCenterY() + (destination - start.exactCenterY()) * step / 24
                event(MotionEvent.ACTION_MOVE, y)
                SystemClock.sleep(45)
                val actual = bounds("Reorder Drag probe 1").exactCenterY()
                check(abs(actual - y) < 24f) { "Dragged row jumped: finger=$y row=$actual step=$step" }
            }
            event(MotionEvent.ACTION_UP, destination)
            SystemClock.sleep(500)
        }
        drag(first.exactCenterY() + rowHeight * 2)
        check(tracks.value.take(3).map { it.id } == listOf(2, 3, 1)) { "Unexpected order ${tracks.value.take(5).map { it.id }}" }
        check(abs(bounds("Reorder Drag probe 2").centerY() - first.centerY()) < 8) { "First-row anchor scrolled the viewport" }
        drag(first.exactCenterY())
        check(tracks.value.take(3).map { it.id } == listOf(1, 2, 3)) { "Could not drag back to first" }
        check(saves == 2) { "Expected one save per drag, got $saves" }
        check(refreshes == 1) { "Dragging triggered pull-to-refresh" }
        return Bundle().apply { putString("result", "First track down two rows and back: stable finger position, viewport, saved order, no refresh") }
    } finally { runOnMainSync { activity.finish() } }
}
