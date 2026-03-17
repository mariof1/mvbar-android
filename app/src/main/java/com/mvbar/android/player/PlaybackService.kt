package com.mvbar.android.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mvbar.android.MainActivity
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.debug.DebugLog
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize audio cache
        AudioCacheManager.init(this)

        // OkHttp client with auth header for streaming
        val okClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                ApiClient.getToken()?.let {
                    builder.addHeader("Authorization", "Bearer $it")
                }
                chain.proceed(builder.build())
            }
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okClient)

        // Wrap with cache: reads from disk cache first, streams & caches on miss
        val dataSourceFactory = AudioCacheManager.createCacheDataSourceFactory(upstreamFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Enable gapless playback (crossfade-free seamless transitions)
        player.pauseAtEndOfMediaItems = false

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DebugLog.e("Player", "Playback error: ${error.errorCodeName}", error)
            }
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(AuthBitmapLoader())
            .build()

        DebugLog.i("Player", "PlaybackService created with cache + gapless playback")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
