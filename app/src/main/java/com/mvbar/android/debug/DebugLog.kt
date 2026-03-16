package com.mvbar.android.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

object DebugLog {
    private const val MAX_ENTRIES = 500
    private val entries = ConcurrentLinkedDeque<LogEntry>()

    @Volatile
    var enabled: Boolean = false

    /** Server URL for log uploads (e.g. "http://10.10.100.5:9999") */
    @Volatile
    var uploadServerUrl: String = ""

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            return "[$time] $level/$tag: $message"
        }
    }

    fun log(level: String, tag: String, message: String) {
        if (!enabled) return
        entries.addLast(LogEntry(level = level, tag = tag, message = message))
        while (entries.size > MAX_ENTRIES) entries.pollFirst()
    }

    fun i(tag: String, message: String) = log("I", tag, message)
    fun d(tag: String, message: String) = log("D", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)
    fun e(tag: String, message: String, throwable: Throwable) {
        log("E", tag, "$message\n${throwable.stackTraceToString()}")
    }

    fun getEntries(): List<LogEntry> = entries.toList()

    fun getLogText(): String {
        val header = buildString {
            appendLine("=== mvbar Android Debug Log ===")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App: 1.0.0")
            appendLine("Entries: ${entries.size}")
            appendLine("================================")
            appendLine()
        }
        return header + entries.joinToString("\n") { it.format() }
    }

    fun clear() = entries.clear()

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("mvbar debug log", getLogText()))
    }

    fun shareLog(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "mvbar Android Debug Log")
            putExtra(Intent.EXTRA_TEXT, getLogText())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Debug Log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Upload log to the configured server. Returns success message or throws. */
    suspend fun uploadLog(): String = withContext(Dispatchers.IO) {
        val serverUrl = uploadServerUrl.trimEnd('/')
        if (serverUrl.isBlank()) throw IllegalStateException("Upload server URL not set")

        val logText = getLogText()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"

        val url = URL("$serverUrl/upload")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.setRequestProperty("X-Device", device)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true

            conn.outputStream.use { it.write(logText.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val body = conn.inputStream.bufferedReader().readText()
            if (code == 200) {
                "Uploaded (${logText.length} bytes)"
            } else {
                throw Exception("Server returned $code: $body")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Install as global uncaught exception handler (wraps existing) */
    fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            enabled = true
            e("CRASH", "Uncaught exception on ${thread.name}", throwable)
            prev?.uncaughtException(thread, throwable)
        }
    }
}
