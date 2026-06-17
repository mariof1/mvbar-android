package com.mvbar.android.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mvbar.android.BuildConfig
import com.mvbar.android.debug.DebugLog
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppUpdateInfo(
    val version: String,
    val releaseName: String,
    val changelog: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

data class AppUpdateCheck(
    val currentVersion: String,
    val latest: AppUpdateInfo?,
    val updateAvailable: Boolean
)

sealed class UpdateInstallResult {
    data object Started : UpdateInstallResult()
    data object NeedsInstallPermission : UpdateInstallResult()
}

object AppUpdateManager {
    private const val RELEASE_API_URL = "https://api.github.com/repos/mariof1/mvbar-android/releases/latest"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdates(): AppUpdateCheck = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "mvbar-android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("GitHub update check failed: ${response.code}")
            }

            val release = json.decodeFromString<GitHubRelease>(body)
            val asset = release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    asset.name.contains("mvbar-android", ignoreCase = true) &&
                    !asset.name.contains("wear", ignoreCase = true)
            } ?: throw IOException("Latest GitHub release has no phone APK")

            val latestVersion = release.tagName.trim().removePrefix("v")
            val latest = AppUpdateInfo(
                version = latestVersion,
                releaseName = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                changelog = release.body?.trim().takeUnless { it.isNullOrBlank() } ?: "No changelog was provided.",
                assetName = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                sizeBytes = asset.size
            )

            AppUpdateCheck(
                currentVersion = BuildConfig.VERSION_NAME,
                latest = latest,
                updateAvailable = isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)
            )
        }
    }

    suspend fun downloadUpdate(context: Context, update: AppUpdateInfo): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { file ->
                if (file.extension.equals("apk", ignoreCase = true)) {
                    file.delete()
                }
            }
        }
        val target = File(updatesDir, safeApkName(update.assetName, update.version))
        val request = Request.Builder()
            .url(update.downloadUrl)
            .header("Accept", APK_MIME_TYPE)
            .header("User-Agent", "mvbar-android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APK download failed: ${response.code}")
            }
            val body = response.body ?: throw IOException("APK download returned an empty response")
            target.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }

        if (target.length() <= 0L) {
            target.delete()
            throw IOException("Downloaded APK was empty")
        }

        DebugLog.i("Update", "Downloaded ${update.assetName} to ${target.absolutePath}")
        target
    }

    fun startInstall(context: Context, apkFile: File): UpdateInstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return UpdateInstallResult.NeedsInstallPermission
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
            return UpdateInstallResult.Started
        } catch (e: ActivityNotFoundException) {
            throw IOException("No APK installer is available on this device", e)
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun safeApkName(assetName: String, version: String): String {
        val candidate = assetName.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "mvbar-android-$version.apk"
        return candidate.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val max = maxOf(latestParts.size, currentParts.size)

        for (index in 0 until max) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) {
                return latestPart > currentPart
            }
        }

        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .substringBefore("-")
            .split(".")
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)
