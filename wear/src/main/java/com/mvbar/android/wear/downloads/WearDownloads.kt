package com.mvbar.android.wear.downloads

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import com.mvbar.android.wear.AuthTokenStore
import com.mvbar.android.wear.cache.MediaCache
import com.mvbar.android.wear.player.PlayableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.net.Uri

/**
 * Pulls a track/episode through CacheDataSource so subsequent playback
 * from the same URL is served from the on-device cache.
 *
 * Uses the same `MediaCache` as streaming, so manual downloads and
 * stream-while-listen recordings share the 2 GB budget under LRU.
 */
object WearDownloads {

    data class Status(val itemId: Int, val percent: Int, val done: Boolean = false, val error: String? = null)

    private val _active = MutableStateFlow<Map<Int, Status>>(emptyMap())
    val active: StateFlow<Map<Int, Status>> = _active.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun download(context: Context, item: PlayableItem) {
        val app = context.applicationContext
        val store = AuthTokenStore.get(app)
        val base = store.serverUrl ?: return
        val url = when (item) {
            is PlayableItem.Music -> "${base.trimEnd('/')}/api/library/tracks/${item.id}/stream"
            is PlayableItem.PodcastEp -> "${base.trimEnd('/')}/api/podcasts/episodes/${item.id}/stream"
        }

        val factory: DataSource.Factory = MediaCache.dataSourceFactory(app)
        val key = "${if (item.isPodcast) "ep" else "tr"}-${item.id}"
        scope.launch {
            update(item.id, Status(item.id, 0))
            try {
                val ds = factory.createDataSource() as androidx.media3.datasource.cache.CacheDataSource
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setKey(key)
                    .build()
                CacheWriter(
                    /* upstream = */ ds,
                    /* dataSpec = */ spec,
                    /* temporaryBuffer = */ null,
                    /* progressListener = */ androidx.media3.datasource.cache.CacheWriter.ProgressListener {
                        requestLength, bytesCached, _ ->
                        val pct = if (requestLength > 0) (bytesCached * 100 / requestLength).toInt() else 0
                        update(item.id, Status(item.id, pct))
                    }
                ).cache()
                update(item.id, Status(item.id, 100, done = true))
            } catch (e: Exception) {
                update(item.id, Status(item.id, 0, error = e.message ?: "Download failed"))
            }
        }
    }

    private fun update(id: Int, s: Status) {
        _active.value = _active.value.toMutableMap().also { it[id] = s }
    }
}
