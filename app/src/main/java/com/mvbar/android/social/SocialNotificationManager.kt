package com.mvbar.android.social

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mvbar.android.MainActivity
import com.mvbar.android.R
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.SocialSummary
import com.mvbar.android.data.model.TrackShare

object SocialNotificationManager {
    const val CHANNEL_ID = "mvbar_social"
    const val EXTRA_OPEN_SOCIAL = "com.mvbar.android.extra.OPEN_SOCIAL"
    const val EXTRA_OPEN_PLAYLISTS = "com.mvbar.android.extra.OPEN_PLAYLISTS"

    private const val PREFS_NAME = "mvbar_social_notifications"
    private const val MAX_SAVED_IDS = 250

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Friends and sharing",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Friend requests, accepted requests, and songs or playlists shared with you"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyFriendRequest(
        context: Context,
        relationshipId: Int,
        email: String,
        remember: Boolean = true
    ) {
        val key = "request:$relationshipId"
        if (remember && !rememberOnce(context, "events", key)) return
        post(
            context,
            key,
            "New friend request",
            "${email.ifBlank { "Someone" }} sent you a friend request."
        )
    }

    fun notifyFriendAccepted(
        context: Context,
        relationshipId: Int,
        email: String,
        remember: Boolean = true
    ) {
        val key = "accepted:$relationshipId"
        if (remember && !rememberOnce(context, "events", key)) return
        post(
            context,
            key,
            "Friend request accepted",
            "${email.ifBlank { "Someone" }} accepted your friend request."
        )
    }

    fun notifyTrackShared(
        context: Context,
        shareId: Int,
        senderEmail: String,
        trackTitle: String?,
        remember: Boolean = true
    ) {
        val key = "share:$shareId"
        if (remember && !rememberOnce(context, "events", key)) return
        post(
            context,
            key,
            "Song shared with you",
            "${senderEmail.ifBlank { "A friend" }} shared “${trackTitle ?: "a song"}” with you."
        )
    }

    fun notifyPlaylistShared(
        context: Context,
        playlistId: Int,
        senderEmail: String,
        playlistName: String,
        sharedAt: String? = null,
        remember: Boolean = true
    ) {
        val key = "playlist:$playlistId:${sharedAt.orEmpty()}"
        if (remember && !rememberOnce(context, "events", key)) return
        post(
            context,
            key,
            "Playlist shared with you",
            "${senderEmail.ifBlank { "A friend" }} shared “${playlistName.ifBlank { "a playlist" }}” with you.",
            openPlaylists = true
        )
    }

    /**
     * Compares the server snapshot with the last background check. Existing unread
     * social items are surfaced on first setup; existing playlist memberships form a
     * baseline so an app upgrade cannot generate a burst of old playlist notifications.
     */
    fun processSnapshot(
        context: Context,
        summary: SocialSummary,
        shares: List<TrackShare>,
        playlists: List<Playlist>
    ) {
        val prefs = prefs(context)
        val initializedKey = scopedKey("snapshot_initialized")
        val initialized = prefs.getBoolean(initializedKey, false)
        val playlistsInitializedKey = scopedKey("playlists_snapshot_initialized")
        val playlistsInitialized = prefs.getBoolean(playlistsInitializedKey, false)
        val previousOutgoing = readSet(context, "outgoing")
        val previousFriends = readSet(context, "friends")
        val previousSharedPlaylists = readSet(context, "shared_playlists")
        val sharedPlaylists = playlists.filterNot { it.isOwner }

        summary.incoming.forEach { request ->
            notifyFriendRequest(context, request.relationshipId, request.user.email)
        }
        shares.asSequence()
            .filter { it.readAt == null }
            .take(5)
            .forEach { share ->
                notifyTrackShared(context, share.id, share.sender.email, share.track.title)
            }

        if (initialized) {
            summary.friends
                .filter { "${it.relationshipId}" !in previousFriends && "${it.relationshipId}" in previousOutgoing }
                .forEach { friend ->
                    notifyFriendAccepted(context, friend.relationshipId, friend.user.email)
                }
        }

        if (playlistsInitialized) {
            sharedPlaylists.asSequence()
                .filter { playlistSnapshotKey(it) !in previousSharedPlaylists }
                .take(5)
                .forEach { playlist ->
                    notifyPlaylistShared(
                        context,
                        playlist.id,
                        playlist.owner?.email.orEmpty(),
                        playlist.name,
                        playlist.sharedAt
                    )
                }
        }

        writeSet(context, "friends", summary.friends.map { "${it.relationshipId}" }.toSet())
        writeSet(context, "outgoing", summary.outgoing.map { "${it.relationshipId}" }.toSet())
        writeSet(context, "shared_playlists", sharedPlaylists.map(::playlistSnapshotKey).toSet())
        if (!initialized) prefs.edit().putBoolean(initializedKey, true).apply()
        if (!playlistsInitialized) prefs.edit().putBoolean(playlistsInitializedKey, true).apply()
    }

    fun clearSessionState(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun post(
        context: Context,
        tag: String,
        title: String,
        body: String,
        openPlaylists: Boolean = false
    ) {
        if (!notificationsAllowed(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(if (openPlaylists) EXTRA_OPEN_PLAYLISTS else EXTRA_OPEN_SOCIAL, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_android_auto_media_badge)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, tag.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the notification call.
        }
    }

    private fun rememberOnce(context: Context, name: String, value: String): Boolean {
        val values = readSet(context, name).toMutableSet()
        if (!values.add(value)) return false
        writeSet(context, name, values)
        return true
    }

    private fun readSet(context: Context, name: String): Set<String> =
        prefs(context).getStringSet(scopedKey(name), emptySet())?.toSet().orEmpty()

    private fun writeSet(context: Context, name: String, values: Set<String>) {
        prefs(context).edit()
            .putStringSet(scopedKey(name), values.toList().takeLast(MAX_SAVED_IDS).toSet())
            .apply()
    }

    private fun scopedKey(name: String): String =
        "${ApiClient.getBaseUrl().hashCode()}:$name"

    private fun playlistSnapshotKey(playlist: Playlist): String =
        "${playlist.id}:${playlist.sharedAt.orEmpty()}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
