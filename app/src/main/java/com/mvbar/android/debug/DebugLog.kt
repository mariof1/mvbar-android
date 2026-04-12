package com.mvbar.android.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

object DebugLog {
    private const val MAX_ENTRIES = 500
    private const val LOG_FILE_NAME = "debug_log.txt"
    private const val PREFS_NAME = "mvbar_debug"
    private const val KEY_ENABLED = "debug_enabled"
    private val entries = ConcurrentLinkedDeque<LogEntry>()

    @Volatile
    var enabled: Boolean = false

    /** File for persisting log entries across crashes */
    private var logFile: File? = null

    /** Call from Application.onCreate() to restore persisted settings and log entries */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)

        logFile = File(context.filesDir, LOG_FILE_NAME)

        // Restore persisted log entries from previous session / crash
        try {
            val file = logFile ?: return
            if (file.exists() && file.length() > 0) {
                val lines = file.readLines()
                for (line in lines) {
                    if (line.isBlank()) continue
                    // Parse: timestamp\tlevel\ttag\tmessage
                    val parts = line.split("\t", limit = 4)
                    if (parts.size == 4) {
                        val ts = parts[0].toLongOrNull() ?: continue
                        val msg = parts[3].replace("\\n", "\n").replace("\\t", "\t")
                        entries.addLast(LogEntry(timestamp = ts, level = parts[1], tag = parts[2], message = msg))
                    }
                }
                while (entries.size > MAX_ENTRIES) entries.pollFirst()
            }
        } catch (_: Exception) {
            // Don't crash on corrupt log file
        }
    }

    /** Persist current settings */
    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

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

        /** Tab-separated line for file persistence (message newlines escaped) */
        fun serialize(): String {
            val escaped = message.replace("\n", "\\n").replace("\t", "\\t")
            return "$timestamp\t$level\t$tag\t$escaped"
        }
    }

    fun log(level: String, tag: String, message: String) {
        if (!enabled) return
        val entry = LogEntry(level = level, tag = tag, message = message)
        entries.addLast(entry)
        while (entries.size > MAX_ENTRIES) entries.pollFirst()
        // Persist to file immediately so it survives crashes
        appendToFile(entry)
    }

    /** Append a single entry to the log file */
    private fun appendToFile(entry: LogEntry) {
        try {
            val file = logFile ?: return
            FileOutputStream(file, true).use { fos ->
                fos.write((entry.serialize() + "\n").toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // Silently ignore file write failures
        }
    }

    /** Rewrite the entire log file from current entries (after trim or clear) */
    private fun rewriteFile() {
        try {
            val file = logFile ?: return
            file.writeText(entries.joinToString("\n") { it.serialize() } + if (entries.isNotEmpty()) "\n" else "")
        } catch (_: Exception) {}
    }

    fun i(tag: String, message: String) = log("I", tag, message)
    fun d(tag: String, message: String) = log("D", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun w(tag: String, message: String, throwable: Throwable) {
        log("W", tag, "$message\n${throwable.stackTraceToString()}")
    }
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

    fun clear() {
        entries.clear()
        try { logFile?.delete() } catch (_: Exception) {}
    }

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

    /** Upload log to the main mvbar server. Returns success message or throws. */
    suspend fun uploadLog(): String = withContext(Dispatchers.IO) {
        val serverUrl = com.mvbar.android.data.api.ApiClient.getBaseUrl().trimEnd('/')
        if (serverUrl.isBlank() || serverUrl == "http://localhost") {
            throw IllegalStateException("Not connected to server")
        }

        val logText = getLogText()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val token = com.mvbar.android.data.api.ApiClient.getToken()

        val url = URL("$serverUrl/api/logs/upload")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.setRequestProperty("X-Device", device)
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Cookie", "mvbar_token=$token")
            }
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
            // Force-enable logging and capture the crash
            enabled = true
            e("CRASH", "Uncaught exception on ${thread.name}", throwable)
            // Rewrite file to ensure crash entry is persisted even if file was trimmed
            rewriteFile()
            prev?.uncaughtException(thread, throwable)
        }
    }
}
