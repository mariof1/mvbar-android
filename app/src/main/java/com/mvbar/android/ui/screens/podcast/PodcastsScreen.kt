package com.mvbar.android.ui.screens.podcast

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Episode
import com.mvbar.android.data.model.Podcast
import com.mvbar.android.ui.components.AvailabilityBadge
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.components.episodeAvailability
import com.mvbar.android.ui.theme.*

private enum class PodcastHomeView { CONTINUE, SUBSCRIPTIONS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastsScreen(
    podcasts: List<Podcast>,
    continueListening: List<Episode>,
    isLoading: Boolean,
    onPodcastClick: (Podcast) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onMarkPlayed: (Int, Boolean) -> Unit,
    onSubscribeClick: () -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    var currentView by rememberSaveable { mutableStateOf(PodcastHomeView.CONTINUE) }

    BackHandler(enabled = currentView == PodcastHomeView.SUBSCRIPTIONS) {
        currentView = PodcastHomeView.CONTINUE
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PodcastSwitcher(
            currentView = currentView,
            continueCount = continueListening.size,
            subscriptionCount = podcasts.size,
            onViewChange = { currentView = it },
            onSubscribeClick = onSubscribeClick
        )

        if (isLoading && podcasts.isEmpty() && continueListening.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange500)
            }
            return@Column
        }

        when (currentView) {
            PodcastHomeView.CONTINUE -> ContinueListeningContent(
                episodes = continueListening,
                onEpisodePlay = onEpisodePlay,
                onMarkPlayed = onMarkPlayed,
                onSubscriptionsClick = { currentView = PodcastHomeView.SUBSCRIPTIONS }
            )
            PodcastHomeView.SUBSCRIPTIONS -> SubscriptionsContent(
                podcasts = podcasts,
                onPodcastClick = onPodcastClick,
                onSubscribeClick = onSubscribeClick
            )
        }
    }
}

@Composable
private fun PodcastSwitcher(
    currentView: PodcastHomeView,
    continueCount: Int,
    subscriptionCount: Int,
    onViewChange: (PodcastHomeView) -> Unit,
    onSubscribeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PodcastTab(
                selected = currentView == PodcastHomeView.CONTINUE,
                label = "Continue",
                count = continueCount,
                modifier = Modifier.weight(1f),
                onClick = { onViewChange(PodcastHomeView.CONTINUE) }
            )
            PodcastTab(
                selected = currentView == PodcastHomeView.SUBSCRIPTIONS,
                label = "Shows",
                count = subscriptionCount,
                modifier = Modifier.weight(1f),
                onClick = { onViewChange(PodcastHomeView.SUBSCRIPTIONS) }
            )
            FilledTonalIconButton(
                onClick = onSubscribeClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Cyan600,
                    contentColor = OnSurface
                )
            ) {
                Icon(Icons.Filled.Add, "Subscribe", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PodcastTab(
    selected: Boolean,
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) OnSurface else SurfaceElevated,
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                color = if (selected) BackgroundDark else OnSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    count.coerceAtMost(999).toString(),
                    color = if (selected) BackgroundDark.copy(alpha = 0.65f) else Cyan400,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ContinueListeningContent(
    episodes: List<Episode>,
    onEpisodePlay: (Episode) -> Unit,
    onMarkPlayed: (Int, Boolean) -> Unit,
    onSubscriptionsClick: () -> Unit
) {
    if (episodes.isEmpty()) {
        EmptyPodcastState(
            title = "Nothing in progress",
            body = "New and unfinished episodes will appear here.",
            actionLabel = "Browse shows",
            onAction = onSubscriptionsClick
        )
        return
    }

    val featured = episodes.first()
    val rest = episodes.drop(1)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ResumeEpisodeCard(
                episode = featured,
                onPlay = { onEpisodePlay(featured) },
                onMarkPlayed = { onMarkPlayed(featured.id, !featured.played) }
            )
        }
        if (rest.isNotEmpty()) {
            item {
                Text(
                    "Up next",
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    color = OnSurfaceDim,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            items(rest, key = { it.id }) { episode ->
                EpisodeListItem(
                    episode = episode,
                    showPodcastTitle = true,
                    showDescription = false,
                    onPlay = { onEpisodePlay(episode) },
                    onMarkPlayed = { onMarkPlayed(episode.id, !episode.played) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResumeEpisodeCard(
    episode: Episode,
    onPlay: () -> Unit,
    onMarkPlayed: () -> Unit
) {
    var showDetails by remember(episode.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val availability = episodeAvailability(episode.id)
    val isPlayable = availability.isPlayable
    val artUrl = episodeArtUrl(episode)
    val remaining = episodeRemainingText(episode)
    val description = remember(episode.description) { cleanPodcastDescription(episode.description) }

    if (showDetails) {
        PodcastFullTextDialog(
            label = "Episode details",
            title = episode.title,
            subtitle = episode.podcastTitle,
            meta = episodeMetaText(episode, includeProgress = true).takeIf { it.isNotBlank() },
            description = description,
            onDismiss = { showDetails = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isPlayable) 1f else 0.45f }
            .combinedClickable(
                onClick = { if (isPlayable) onPlay() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDetails = true
                }
            ),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(WhiteOverlay10, SurfaceDark, SurfaceDark)
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkImage(
                        model = artUrl,
                        contentDescription = null,
                        placeholderIcon = Icons.Filled.Podcasts,
                        iconSize = 32.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    PlayOverlay(modifier = Modifier.size(42.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    episode.podcastTitle?.let {
                        Text(
                            it,
                            color = Cyan400,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        episode.title,
                        color = OnSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        episodeMetaText(episode, includeProgress = false),
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            com.mvbar.android.ui.components.GlowingProgressLine(
                progress = episode.progressPercent / 100f,
                accent = Orange500,
                accentHighlight = Orange400,
                heightDp = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        remaining ?: "${episode.progressPercent}% played",
                        color = Orange400,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AvailabilityBadge(
                        availability = availability,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                IconButton(onClick = onMarkPlayed, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (episode.played) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (episode.played) "Mark unplayed" else "Mark played",
                        tint = if (episode.played) Orange500 else OnSurfaceSubtle,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsContent(
    podcasts: List<Podcast>,
    onPodcastClick: (Podcast) -> Unit,
    onSubscribeClick: () -> Unit
) {
    if (podcasts.isEmpty()) {
        EmptyPodcastState(
            title = "No shows yet",
            body = "Subscribed podcasts will appear here.",
            actionLabel = "Add show",
            onAction = onSubscribeClick
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(podcasts, key = { it.id }) { podcast ->
            PodcastGridItem(
                podcast = podcast,
                onClick = { onPodcastClick(podcast) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastGridItem(podcast: Podcast, onClick: () -> Unit) {
    var showDetails by remember(podcast.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val artUrl = podcast.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: podcast.imageUrl
        ?: ApiClient.podcastArtUrl(podcast.id)
    val description = remember(podcast.description) { cleanPodcastDescription(podcast.description) }
    val meta = buildList {
        if (podcast.unplayedCount > 0) add("${podcast.unplayedCount} unplayed")
        podcast.language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    }.joinToString(" - ").takeIf { it.isNotBlank() }

    if (showDetails) {
        PodcastFullTextDialog(
            label = "Podcast details",
            title = podcast.title,
            subtitle = podcast.author,
            meta = meta,
            description = description,
            onDismiss = { showDetails = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDetails = true
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceElevated)
        ) {
            ArtworkImage(
                model = artUrl,
                contentDescription = podcast.title,
                placeholderIcon = Icons.Filled.Podcasts,
                iconSize = 34.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (podcast.unplayedCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = Cyan600
                ) {
                    Text(
                        podcast.unplayedCount.coerceAtMost(999).toString(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        color = OnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            podcast.title,
            color = OnSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        podcast.author?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (podcast.unplayedCount > 0) {
            Text(
                "${podcast.unplayedCount} unplayed",
                color = Cyan400,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeListItem(
    episode: Episode,
    showPodcastTitle: Boolean = true,
    showDescription: Boolean = false,
    onPlay: () -> Unit,
    onMarkPlayed: () -> Unit
) {
    var showDetails by remember(episode.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val availability = episodeAvailability(episode.id)
    val isPlayable = availability.isPlayable
    val description = remember(episode.description) { cleanPodcastDescription(episode.description) }

    if (showDetails) {
        PodcastFullTextDialog(
            label = "Episode details",
            title = episode.title,
            subtitle = episode.podcastTitle,
            meta = episodeMetaText(episode, includeProgress = true).takeIf { it.isNotBlank() },
            description = description,
            onDismiss = { showDetails = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isPlayable) 1f else 0.42f }
            .combinedClickable(
                onClick = { if (isPlayable) onPlay() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDetails = true
                }
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (episode.played) SurfaceDark.copy(alpha = 0.65f) else BackgroundDark
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                ArtworkImage(
                    model = episodeArtUrl(episode),
                    contentDescription = null,
                    placeholderIcon = Icons.Filled.Podcasts,
                    iconSize = 26.dp,
                    modifier = Modifier.fillMaxSize()
                )
                PlayOverlay(modifier = Modifier.size(34.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.title,
                    color = if (episode.played) OnSurfaceDim else OnSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (showPodcastTitle && !episode.podcastTitle.isNullOrBlank()) {
                    Text(
                        episode.podcastTitle,
                        color = Cyan400,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        episodeMetaText(episode, includeProgress = true),
                        modifier = Modifier.weight(1f, fill = false),
                        color = OnSurfaceSubtle,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AvailabilityBadge(
                        availability = availability,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                if (showDescription && description.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        description,
                        color = OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (episode.positionMs > 0 && !episode.played && episode.durationMs != null) {
                    Spacer(Modifier.height(7.dp))
                    com.mvbar.android.ui.components.GlowingProgressLine(
                        progress = episode.progressPercent / 100f,
                        accent = Orange500,
                        accentHighlight = Orange400,
                        heightDp = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            IconButton(
                onClick = onMarkPlayed,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    if (episode.played) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (episode.played) "Mark unplayed" else "Mark played",
                    tint = if (episode.played) Orange500 else OnSurfaceSubtle,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
internal fun PodcastFullTextDialog(
    label: String,
    title: String,
    subtitle: String? = null,
    meta: String? = null,
    description: String? = null,
    onDismiss: () -> Unit
) {
    val cleanedDescription = description.orEmpty().trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = OnSurface,
        textContentColor = OnSurfaceDim,
        title = {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    title,
                    color = OnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        color = Cyan400,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                meta?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        color = OnSurfaceSubtle,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    cleanedDescription.ifBlank { "No description available." },
                    color = OnSurfaceDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Cyan400)
            }
        }
    )
}

@Composable
private fun PlayOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = BackgroundDark.copy(alpha = 0.68f)
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            "Play",
            modifier = Modifier.padding(6.dp),
            tint = OnSurface
        )
    }
}

@Composable
private fun EmptyPodcastState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceElevated
            ) {
                Icon(
                    Icons.Filled.Podcasts,
                    null,
                    tint = Cyan400,
                    modifier = Modifier
                        .size(62.dp)
                        .padding(16.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                color = OnSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                color = OnSurfaceDim,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                shape = RoundedCornerShape(50)
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun episodeArtUrl(episode: Episode): String =
    episode.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: episode.podcastImagePath?.let { ApiClient.podcastArtPathUrl(it) }
        ?: episode.imageUrl
        ?: episode.podcastImageUrl
        ?: ApiClient.episodeArtUrl(episode.id)

private fun episodeMetaText(episode: Episode, includeProgress: Boolean): String {
    val parts = mutableListOf<String>()
    if (episode.publishedFormatted.isNotEmpty()) parts += episode.publishedFormatted
    if (episode.durationFormatted.isNotEmpty()) parts += episode.durationFormatted
    if (includeProgress && episode.positionMs > 0 && !episode.played) {
        episodeRemainingText(episode)?.let { parts += it } ?: run {
            parts += "${episode.progressPercent}% played"
        }
    }
    if (episode.played) parts += "Played"
    return parts.joinToString(" - ")
}

private fun episodeRemainingText(episode: Episode): String? {
    val duration = episode.durationMs ?: return null
    if (duration <= 0L || episode.positionMs <= 0L) return null
    val remainingMs = (duration - episode.positionMs).coerceAtLeast(0L)
    val remainingMinutes = (remainingMs / 60_000L).coerceAtLeast(1L)
    return "$remainingMinutes min left"
}

internal fun cleanPodcastDescription(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}
