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
import com.mvbar.android.data.model.SocialSummary
import com.mvbar.android.data.model.TrackShare

object SocialNotificationManager {
    const val CHANNEL_ID = "mvbar_social"
    const val EXTRA_OPEN_SOCIAL = "com.mvbar.android.extra.OPEN_SOCIAL"

    private const val PREFS_NAME = "mvbar_social_notifications"
    private const val MAX_SAVED_IDS = 250

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Friends and sharing",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Friend requests, accepted requests, and songs shared with you"
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

    /**
     * Compares the server snapshot with the last background check. Existing unread
     * items are intentionally surfaced on first setup; old friendships are only a baseline.
     */
    fun processSnapshot(context: Context, summary: SocialSummary, shares: List<TrackShare>) {
        val prefs = prefs(context)
        val initializedKey = scopedKey("snapshot_initialized")
        val initialized = prefs.getBoolean(initializedKey, false)
        val previousOutgoing = readSet(context, "outgoing")
        val previousFriends = readSet(context, "friends")

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

        writeSet(context, "friends", summary.friends.map { "${it.relationshipId}" }.toSet())
        writeSet(context, "outgoing", summary.outgoing.map { "${it.relationshipId}" }.toSet())
        if (!initialized) prefs.edit().putBoolean(initializedKey, true).apply()
    }

    fun clearSessionState(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun post(context: Context, tag: String, title: String, body: String) {
        if (!notificationsAllowed(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_SOCIAL, true)
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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
