package com.mvbar.android.ui

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mvbar.android.MainActivity
import com.mvbar.android.ui.screens.login.LoginScreen
import com.mvbar.android.ui.theme.MvbarTheme
import com.mvbar.android.viewmodel.AuthState
import java.io.File

/** Render the login form with inert callbacks, without logging out or changing stored credentials. */
internal fun Instrumentation.verifyLoginVersion(): Bundle {
    val version = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0)
        .versionName?.trim().orEmpty()
    val expected = if (version.isEmpty()) "Debug" else "Version $version"
    val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
    try {
        runOnMainSync {
            activity.setContent {
                MvbarTheme {
                    LoginScreen(AuthState(isLoading = false), onLogin = { _, _, _ -> })
                }
            }
        }
        waitForIdleSync()
        var labels = emptyList<String>()
        for (attempt in 0 until 40) {
            fun texts(node: AccessibilityNodeInfo?): List<String> {
                if (node == null) return emptyList()
                return listOfNotNull(node.text?.toString()) +
                    (0 until node.childCount).flatMap { texts(node.getChild(it)) }
            }
            labels = texts(uiAutomation.rootInActiveWindow)
                .filter { it.startsWith("Version ") || it == "Debug" }
            if (labels.isNotEmpty()) break
            SystemClock.sleep(250)
        }
        val screenshot = File(targetContext.getExternalFilesDir(null), "login-version-probe.png")
        uiAutomation.takeScreenshot()?.let { bitmap ->
            screenshot.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        check(expected in labels) { "Expected '$expected', rendered $labels" }
        return Bundle().apply {
            putString("versionLabel", expected)
            putString("screenshot", screenshot.absolutePath)
        }
    } finally {
        runOnMainSync { activity.finish() }
    }
}
