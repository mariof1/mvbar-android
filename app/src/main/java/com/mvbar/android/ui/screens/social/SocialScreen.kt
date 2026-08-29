package com.mvbar.android.ui.screens.social

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.SocialRelationship
import com.mvbar.android.data.model.SocialSearchUser
import com.mvbar.android.data.model.Track
import com.mvbar.android.data.model.TrackShare
import com.mvbar.android.social.SocialNotificationManager
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.SocialUiState

private enum class SocialTab { SHARES, FRIENDS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    state: SocialUiState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onAcceptRequest: (Int) -> Unit,
    onRemoveRequest: (Int) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onMarkRead: (Int) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteShare: (Int) -> Unit,
    onPlay: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    onDismissError: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var tab by remember { mutableStateOf(SocialTab.SHARES) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAllowed by remember {
        mutableStateOf(SocialNotificationManager.notificationsAllowed(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = SocialNotificationManager.notificationsAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize()) {
        if (!notificationsAllowed) {
            NotificationBanner {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }
        }

        state.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismissError) { Icon(Icons.Filled.Close, "Dismiss") }
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = BackgroundDark) {
            Tab(
                selected = tab == SocialTab.SHARES,
                onClick = { tab = SocialTab.SHARES },
                text = { TabLabel("Shared", state.summary.unreadShares) },
                icon = { Icon(Icons.Filled.Share, null) }
            )
            Tab(
                selected = tab == SocialTab.FRIENDS,
                onClick = { tab = SocialTab.FRIENDS },
                text = { TabLabel("Friends", state.summary.incoming.size) },
                icon = { Icon(Icons.Filled.People, null) }
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = Cyan500,
                    modifier = Modifier.align(Alignment.Center)
                )
                tab == SocialTab.SHARES -> SharesList(
                    shares = state.shares,
                    unread = state.summary.unreadShares,
                    busyKeys = state.busyKeys,
                    onMarkRead = onMarkRead,
                    onMarkAllRead = onMarkAllRead,
                    onDelete = onDeleteShare,
                    onPlay = onPlay,
                    onQueue = onQueue,
                    bottomPadding = bottomPadding
                )
                else -> FriendsList(
                    state = state,
                    onSearch = onSearch,
                    onSendRequest = onSendRequest,
                    onAcceptRequest = onAcceptRequest,
                    onRemoveRequest = onRemoveRequest,
                    onRemoveFriend = onRemoveFriend,
                    bottomPadding = bottomPadding
                )
            }
        }
    }
}

@Composable
private fun NotificationBanner(onSettings: () -> Unit) {
    Surface(color = Cyan500.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.NotificationsOff, null, tint = Cyan400)
            Spacer(Modifier.width(10.dp))
            Text(
                "Turn on notifications for new requests and shared songs.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSettings) { Text("Settings") }
        }
    }
}

@Composable
private fun TabLabel(label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(CircleShape).background(Pink500).padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (count > 99) "99+" else "$count", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SharesList(
    shares: List<TrackShare>,
    unread: Int,
    busyKeys: Set<String>,
    onMarkRead: (Int) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (Int) -> Unit,
    onPlay: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    if (shares.isEmpty()) {
        EmptySocial(Icons.Filled.Share, "No songs shared yet", "Songs your friends send you will appear here.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding + 120.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shared with you", style = MaterialTheme.typography.titleMedium, color = OnSurface, modifier = Modifier.weight(1f))
                if (unread > 0) {
                    TextButton(onClick = onMarkAllRead, enabled = "shares:all" !in busyKeys) {
                        Text("Mark all read")
                    }
                }
            }
        }
        items(shares, key = { it.id }) { share ->
            ShareCard(
                share = share,
                busy = "share:${share.id}" in busyKeys,
                onMarkRead = { onMarkRead(share.id) },
                onDelete = { onDelete(share.id) },
                onPlay = {
                    if (share.readAt == null) onMarkRead(share.id)
                    onPlay(share.track.toTrack())
                },
                onQueue = { onQueue(share.track.toTrack()) }
            )
        }
    }
}

@Composable
private fun ShareCard(
    share: TrackShare,
    busy: Boolean,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (share.readAt == null) Cyan500.copy(alpha = 0.10f) else SurfaceContainerDark
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(
                    model = share.track.artPath?.let(ApiClient::artPathUrl)
                        ?: ApiClient.trackArtUrl(share.track.id),
                    contentDescription = null,
                    placeholderIcon = Icons.Filled.MusicNote,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        share.track.title ?: "Untitled",
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        share.track.artist ?: "Unknown artist",
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("From ${share.sender.email}", color = Cyan400, style = MaterialTheme.typography.labelMedium)
                }
                if (share.readAt == null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Pink500))
                }
            }
            share.message?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", color = OnSurfaceDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, null); Text("Play") }
                TextButton(onClick = onQueue) { Icon(Icons.Filled.QueueMusic, null); Text("Queue") }
                if (share.readAt == null) {
                    IconButton(onClick = onMarkRead, enabled = !busy) {
                        Icon(Icons.Filled.MarkEmailRead, "Mark read", tint = OnSurfaceDim)
                    }
                }
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Filled.DeleteOutline, "Delete share", tint = OnSurfaceDim)
                }
            }
        }
    }
}

@Composable
private fun FriendsList(
    state: SocialUiState,
    onSearch: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onAcceptRequest: (Int) -> Unit,
    onRemoveRequest: (Int) -> Unit,
    onRemoveFriend: (String) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding + 120.dp)) {
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                label = { Text("Find people by email") },
                leadingIcon = { Icon(Icons.Filled.PersonSearch, null) },
                trailingIcon = {
                    if (state.isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
        }

        if (state.searchQuery.trim().length >= 2) {
            SectionHeader("Search results")
            if (!state.isSearching && state.searchResults.isEmpty()) {
                item { Text("No users found", color = OnSurfaceDim, modifier = Modifier.padding(12.dp)) }
            }
            items(state.searchResults, key = { "search:${it.id}" }) { user ->
                SearchUserRow(
                    user = user,
                    busy = "user:${user.id}" in state.busyKeys ||
                        (user.relationshipId?.let { "request:$it" in state.busyKeys } == true),
                    onSend = { onSendRequest(user.id) },
                    onAccept = { user.relationshipId?.let(onAcceptRequest) },
                    onRemoveRequest = { user.relationshipId?.let(onRemoveRequest) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        if (state.summary.incoming.isNotEmpty()) {
            SectionHeader("Friend requests", state.summary.incoming.size)
            items(state.summary.incoming, key = { "incoming:${it.relationshipId}" }) { relationship ->
                RelationshipRow(
                    relationship,
                    busy = "request:${relationship.relationshipId}" in state.busyKeys,
                    primaryLabel = "Accept",
                    onPrimary = { onAcceptRequest(relationship.relationshipId) },
                    secondaryLabel = "Decline",
                    onSecondary = { onRemoveRequest(relationship.relationshipId) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        if (state.summary.outgoing.isNotEmpty()) {
            SectionHeader("Sent requests")
            items(state.summary.outgoing, key = { "outgoing:${it.relationshipId}" }) { relationship ->
                RelationshipRow(
                    relationship,
                    busy = "request:${relationship.relationshipId}" in state.busyKeys,
                    primaryLabel = "Cancel",
                    onPrimary = { onRemoveRequest(relationship.relationshipId) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        SectionHeader("Friends", state.summary.friends.size)
        if (state.summary.friends.isEmpty()) {
            item { Text("Find someone above to send your first request.", color = OnSurfaceDim, modifier = Modifier.padding(12.dp)) }
        }
        items(state.summary.friends, key = { "friend:${it.user.id}" }) { relationship ->
            RelationshipRow(
                relationship,
                busy = "user:${relationship.user.id}" in state.busyKeys,
                primaryLabel = "Remove",
                onPrimary = { onRemoveFriend(relationship.user.id) },
                destructive = true
            )
        }
    }
}

private fun LazyListScope.SectionHeader(title: String, count: Int? = null) {
    item {
        Text(
            if (count == null) title else "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun RelationshipRow(
    relationship: SocialRelationship,
    busy: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    UserRow(email = relationship.user.email, avatarPath = relationship.user.avatarPath) {
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary, enabled = !busy) { Text(secondaryLabel) }
        }
        TextButton(onClick = onPrimary, enabled = !busy) {
            Text(primaryLabel, color = if (destructive) MaterialTheme.colorScheme.error else Cyan400)
        }
    }
}

@Composable
private fun SearchUserRow(
    user: SocialSearchUser,
    busy: Boolean,
    onSend: () -> Unit,
    onAccept: () -> Unit,
    onRemoveRequest: () -> Unit
) {
    UserRow(email = user.email, avatarPath = user.avatarPath) {
        when (user.relationship) {
            "friend" -> Text("Friends", color = Cyan400, style = MaterialTheme.typography.labelLarge)
            "outgoing" -> TextButton(onClick = onRemoveRequest, enabled = !busy) { Text("Cancel") }
            "incoming" -> Button(onClick = onAccept, enabled = !busy) { Text("Accept") }
            else -> Button(onClick = onSend, enabled = !busy) {
                Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
            }
        }
    }
}

@Composable
private fun UserRow(
    email: String,
    avatarPath: String?,
    actions: @Composable RowScope.() -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SocialAvatar(email, avatarPath)
        Spacer(Modifier.width(12.dp))
        Text(email, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
internal fun SocialAvatar(email: String, avatarPath: String?, modifier: Modifier = Modifier) {
    val avatarModifier = modifier.size(40.dp).clip(CircleShape)
    if (!avatarPath.isNullOrBlank()) {
        AsyncImage(
            model = ApiClient.avatarUrl(avatarPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier
        )
    } else {
        Box(avatarModifier.background(Cyan500.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(email.firstOrNull()?.uppercase() ?: "?", color = Cyan400, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptySocial(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, tint = OnSurfaceSubtle, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDim)
        }
    }
}
