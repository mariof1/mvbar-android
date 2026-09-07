package com.mvbar.android.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import com.mvbar.android.MainActivity
import com.mvbar.android.R
import com.mvbar.android.connect.ConnectDevice
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.social.SocialRealtimeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A transport-only session: it never creates an audio player or requests audio focus. */
class RemotePlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private var displayedDeviceId: String? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "MVBar Connect playback", NotificationManager.IMPORTANCE_LOW)
        )
        session = MediaSession(this, "MVBar Connect")
        @Suppress("DEPRECATION")
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_FIXED, 1, 1) {})
        session.setSessionActivity(openApp())
        session.setCallback(remoteTransportCallback())
        scope.launch {
            combine(SocialRealtimeManager.connectDevices, SocialRealtimeManager.selectedConnectDeviceId) { devices, selected ->
                devices.firstOrNull { it.id == selected && it.id != ApiClient.getClientId() && it.state.track != null }
            }.collect { device ->
                if (device == null) {
                    session.isActive = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else publish(device)
            }
        }
    }

    // Android Auto browsing/voice search belongs to PlaybackService. This private
    // transport session does not advertise ACTION_PLAY_FROM_SEARCH or expose a
    // browser service; it only controls the selected device's existing queue.
    @android.annotation.SuppressLint("MissingOnPlayFromSearch")
    private fun remoteTransportCallback() = object : MediaSession.Callback() {
        override fun onPlay() = command("play")
        override fun onPause() = command("pause")
        override fun onSkipToNext() = command("next")
        override fun onSkipToPrevious() = command("previous")
        override fun onStop() = command("stop")
        override fun onSeekTo(pos: Long) {
            if (currentDevice()?.id != displayedDeviceId) return
            val duration = currentDevice()?.state?.durationMs ?: return
            if (duration <= 0) return
            SocialRealtimeManager.sendCommandToSelected("seek", buildJsonObject {
                put("positionMs", pos.coerceIn(0, duration))
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val device = currentDevice()
        if (device != null) {
            publish(device)
            if (intent?.getStringExtra("deviceId") == device.id) {
                when (intent.action) {
                    "play", "pause", "next", "previous" -> command(intent.action!!)
                }
            }
        } else {
            // Fulfil the foreground-start contract even if selection disappeared during startup.
            startForeground(ID, Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_android_auto_media_badge).setContentTitle("MVBar Connect").build())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun currentDevice() = SocialRealtimeManager.selectedConnectDevice()
        ?.takeIf { it.id != ApiClient.getClientId() && it.state.track != null }

    private fun command(action: String) {
        if (currentDevice()?.id == displayedDeviceId && displayedDeviceId != null) {
            SocialRealtimeManager.sendCommandToSelected(action)
        }
    }

    private fun openApp() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(device: ConnectDevice, command: String, icon: Int, label: String): Notification.Action {
        val intent = Intent(this, RemotePlaybackService::class.java).setAction(command)
            .setData(android.net.Uri.parse("mvbar-connect://control/${android.net.Uri.encode(device.id)}/$command"))
            .putExtra("deviceId", device.id)
        return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), label, PendingIntent.getService(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    }

    private fun publish(device: ConnectDevice) {
        val state = device.state
        val track = state.track ?: return
        displayedDeviceId = device.id
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, track.id.toString())
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title ?: "Unknown track")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs.coerceAtLeast(0)).build())
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
        if (state.durationMs > 0) actions = actions or PlaybackState.ACTION_SEEK_TO
        session.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setState(if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                state.positionMs.coerceAtLeast(0), if (state.isPlaying) 1f else 0f, SystemClock.elapsedRealtime()).build())
        session.isActive = true
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_android_auto_media_badge)
            .setContentTitle(track.title ?: "Unknown track")
            .setContentText(track.artist)
            .setSubText("Playing on ${device.name}")
            .setContentIntent(openApp()).setOnlyAlertOnce(true).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(device, "previous", android.R.drawable.ic_media_previous, "Previous"))
            .addAction(action(device, if (state.isPlaying) "pause" else "play",
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Pause" else "Play"))
            .addAction(action(device, "next", android.R.drawable.ic_media_next, "Next"))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
        startForeground(ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mvbar_connect_playback"
        private const val ID = 2002
    }
}
