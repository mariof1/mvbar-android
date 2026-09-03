package com.mvbar.android.tv.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.mvbar.android.tv.MainActivity
import com.mvbar.android.tv.R
import com.mvbar.android.tv.data.Episode
import com.mvbar.android.tv.data.RecommendationBucket
import com.mvbar.android.tv.data.Track
import com.mvbar.android.tv.data.TvSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class TvHomePublisher(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("mvbar_tv_home", Context.MODE_PRIVATE)

    // These builder methods are the documented public TvProvider authoring API,
    // but their shared base builder is incorrectly annotated as library-only.
    @SuppressLint("RestrictedApi")
    suspend fun publish(
        recommendations: List<RecommendationBucket>,
        recentlyAdded: List<Track>,
        episodes: List<Episode>
    ) = withContext(Dispatchers.IO) {
        publishMutex.withLock {
            runCatching {
                val helper = PreviewChannelHelper(appContext)
                val channelId = ensureChannel(helper)
                val sessionStore = TvSessionStore(appContext)
                val session = sessionStore.load()
                val clientId = sessionStore.clientId
                val programs = buildHomePrograms(
                    recommendations,
                    recentlyAdded,
                    episodes,
                    session?.serverUrl
                )
                val posterUris = programs.associate { program ->
                    program.id to (
                        cachePoster(program.posterUrl, session?.token, clientId)
                            ?: defaultPosterUri()
                        )
                }
                prunePosterCache()
                val signature = programs.joinToString("|") {
                    "$POSTER_FORMAT:${it.id}:${it.title}:${it.description}:${it.posterUrl}"
                }.hashCode().toString()
                if (preferences.getString(CONTENT_SIGNATURE, null) == signature) {
                    return@runCatching
                }
                appContext.contentResolver.delete(
                    TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                    null,
                    null
                )

                programs.forEachIndexed { index, program ->
                    helper.publishPreviewProgram(
                        PreviewProgram.Builder()
                            .setChannelId(channelId)
                            .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                            .setTitle(program.title)
                            .setDescription(program.description)
                            .setPosterArtUri(posterUris.getValue(program.id))
                            .setIntentUri(Uri.parse(program.intentUri))
                            .setInternalProviderId(program.id)
                            .setWeight(programs.size - index)
                            .build()
                    )
                }
                preferences.edit().putString(CONTENT_SIGNATURE, signature).apply()
            }.onFailure { error ->
                Log.e("MvbarTvHome", "Could not publish TV home content", error)
            }
        }
    }

    private fun ensureChannel(helper: PreviewChannelHelper): Long {
        val savedId = preferences.getLong(CHANNEL_ID, -1L)
        if (savedId > 0 && helper.getPreviewChannel(savedId) != null) return savedId

        val channel = PreviewChannel.Builder()
            .setDisplayName("MVBar – Continue & Discover")
            .setDescription("Continue listening, recently added music, and personal recommendations")
            .setInternalProviderId("mvbar-home")
            .setAppLinkIntent(Intent(appContext, MainActivity::class.java).setData(deepLink("home")))
            .setLogo(BitmapFactory.decodeResource(appContext.resources, R.drawable.mvbar_tv_launcher))
            .build()
        return helper.publishDefaultChannel(channel).also { channelId ->
            preferences.edit().putLong(CHANNEL_ID, channelId).apply()
        }
    }

    private fun defaultPosterUri(): Uri = Uri.parse(
        "android.resource://${appContext.packageName}/${R.drawable.mvbar_tv_launcher}"
    )

    private fun cachePoster(url: String?, token: String?, clientId: String): Uri? {
        if (url.isNullOrBlank()) return null
        val directory = File(appContext.cacheDir, "tv_home_art").apply { mkdirs() }
        val file = File(directory, "${url.sha256()}.jpg")
        if (!file.isFile || file.length() == 0L) {
            val request = Request.Builder()
                .url(url)
                .header("X-MVBar-Client", "android-tv")
                .header("X-MVBar-Client-Id", clientId)
                .apply {
                    token?.let {
                        header("Authorization", "Bearer $it")
                        header("Cookie", "mvbar_token=$it")
                    }
                }
                .build()
            val downloaded = imageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                if (body.contentLength() > MAX_POSTER_BYTES) return@use false
                val temporary = File(directory, "${file.name}.part")
                body.byteStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                if (temporary.length() == 0L || !temporary.renameTo(file)) {
                    temporary.delete()
                    false
                } else {
                    true
                }
            }
            if (!downloaded) return null
        }
        file.setLastModified(System.currentTimeMillis())
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.home-art",
            file
        )
        grantLauncherReadAccess(uri)
        return uri
    }

    private fun prunePosterCache() {
        File(appContext.cacheDir, "tv_home_art")
            .listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.endsWith(".part") }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_CACHED_POSTERS)
            .forEach(File::delete)
    }

    private fun grantLauncherReadAccess(uri: Uri) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        appContext.packageManager.queryIntentActivities(homeIntent, 0).forEach { activity ->
            appContext.grantUriPermission(
                activity.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "default_channel_id"
        const val CONTENT_SIGNATURE = "content_signature"
        const val POSTER_FORMAT = 4
        const val MAX_POSTER_BYTES = 12L * 1024L * 1024L
        const val MAX_CACHED_POSTERS = 100
        val publishMutex = Mutex()
        val imageClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

internal data class TvHomeProgram(
    val id: String,
    val title: String,
    val description: String,
    val intentUri: String,
    val posterUrl: String? = null
)

internal fun buildHomePrograms(
    recommendations: List<RecommendationBucket>,
    recentlyAdded: List<Track>,
    episodes: List<Episode>,
    serverUrl: String? = null
): List<TvHomeProgram> {
    val normalizedServer = serverUrl?.trimEnd('/')
    val continuing = episodes.asSequence()
        .filter { it.positionMs > 0L && !it.played }
        .take(5)
        .map { episode ->
            TvHomeProgram(
                "episode:${episode.id}",
                episode.title,
                "Continue ${episode.podcastTitle ?: "podcast"}",
                deepLinkString("episode", episode.id.toString()),
                normalizedServer?.let { episodePosterUrl(it, episode) }
            )
        }
    val recent = recentlyAdded.asSequence().take(8).map { track ->
        TvHomeProgram(
            "track:${track.id}",
            track.displayTitle,
            "Recently added • ${track.displayArtist}",
            deepLinkString("track", track.id.toString()),
            normalizedServer?.let { trackPosterUrl(it, track) }
        )
    }
    val buckets = recommendations.asSequence().filter { it.tracks.isNotEmpty() }.take(7).map { bucket ->
        TvHomeProgram(
            "bucket:${bucket.key}",
            bucket.name,
            bucket.subtitle ?: "Personal recommendation mix",
            deepLinkString("bucket", bucket.key),
            normalizedServer?.let { server -> bucket.tracks.firstOrNull()?.let { trackPosterUrl(server, it) } }
        )
    }
    return (continuing + recent + buckets).take(20).toList()
}

private fun trackPosterUrl(serverUrl: String, track: Track): String =
    "$serverUrl/api/library/tracks/${track.id}/art"

private fun episodePosterUrl(serverUrl: String, episode: Episode): String =
    episode.imagePath?.let { "$serverUrl/api/podcast-art/${Uri.encode(it, "/")}" }
        ?: episode.podcastImagePath?.let { "$serverUrl/api/podcast-art/${Uri.encode(it, "/")}" }
        ?: "$serverUrl/api/podcasts/episodes/${episode.id}/art"

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }

internal fun deepLink(type: String, value: String? = null): Uri = Uri.Builder()
    .scheme("mvbar-tv")
    .authority(type)
    .apply { value?.let(::appendPath) }
    .build()

internal fun deepLinkString(type: String, value: String? = null): String = buildString {
    append("mvbar-tv://")
    append(type)
    value?.let {
        append('/')
        append(java.net.URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20"))
    }
}
