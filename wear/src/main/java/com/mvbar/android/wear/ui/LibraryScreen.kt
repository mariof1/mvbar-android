package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.mvbar.android.wear.NowPlayingRepository
import com.mvbar.android.wear.net.Episode
import com.mvbar.android.wear.net.Playlist
import com.mvbar.android.wear.net.Podcast
import com.mvbar.android.wear.net.Track
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun LibraryScreen(
    backend: Backend,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenSmartPlaylists: () -> Unit,
    onOpenPlaylist: (Int, String) -> Unit,
    onOpenTrackList: (String, suspend () -> List<Track>) -> Unit,
    onOpenPodcast: (Int) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    if (showSearch) {
        SearchScreen(backend, onBack = { showSearch = false }, onOpenNowPlaying = onOpenNowPlaying)
        return
    }

    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var reachable by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf(LibraryData()) }

    LaunchedEffect(refreshKey) {
        loading = true
        val result = runCatching {
            coroutineScope {
                val reach = async { backend.connectionOk() }
                val playlists = async { backend.playlists() }
                val podcasts = async { backend.podcasts() }
                val episodes = async { backend.newEpisodes() }
                val added = async { backend.recentTracks() }
                val history = async { backend.history() }
                val favorites = async { backend.favorites() }
                LibraryLoad(
                    reachable = reach.await(),
                    data = LibraryData(
                        playlists = playlists.await(),
                        podcasts = podcasts.await(),
                        newEpisodes = episodes.await(),
                        recentlyAdded = added.await(),
                        history = history.await(),
                        favorites = favorites.await()
                    )
                )
            }
        }
        result.onSuccess {
            reachable = it.reachable
            data = it.data
        }.onFailure {
            reachable = false
            data = LibraryData()
        }
        loading = false
    }

    WearList {
        item { NowPlayingHero(onOpen = onOpenNowPlaying) }
        item {
            CenteredActions {
                RoundIconAction("Voice search", Icons.Default.Mic, WearTheme.Cyan, { showSearch = true }, iconTint = Color.Black)
                Spacer(Modifier.size(12.dp))
                RoundIconAction("Refresh library", Icons.Default.Refresh, WearTheme.SurfaceRaised, { refreshKey++ })
                Spacer(Modifier.size(12.dp))
                RoundIconAction("Settings", Icons.Default.Settings, WearTheme.SurfaceRaised, onOpenSettings)
            }
        }

        if (loading) {
            item { LoadingChip("Syncing watch") }
        } else if (!reachable && data.isEmpty) {
            item { OfflineChip(onRetry = { refreshKey++ }) }
        }

        item { SectionLabel("Browse") }
        item { CategoryChip("Albums", "Browse your library", Icons.Default.Album, WearTheme.Cyan, onOpenAlbums) }
        item { CategoryChip("Smart playlists", "Server rules", Icons.Default.AutoAwesome, WearTheme.Pink, onOpenSmartPlaylists) }
        item {
            CategoryChip("Favorites", "${data.favorites.size} tracks", Icons.Default.Favorite, WearTheme.Pink) {
                onOpenTrackList("Favorites") { backend.favorites() }
            }
        }
        item {
            CategoryChip("History", "Recently played", Icons.Default.History, WearTheme.Cyan) {
                onOpenTrackList("History") { backend.history() }
            }
        }

        if (data.history.isNotEmpty()) {
            item { SectionLabel("Continue") }
            items(data.history.take(6)) { track ->
                TrackChip(backend, track) {
                    val queue = data.history.map { PlayableItem.Music(it) }
                    WearPlayerHolder.playQueue(backend.context, queue, data.history.indexOf(track).coerceAtLeast(0))
                    onOpenNowPlaying()
                }
            }
        }

        if (data.newEpisodes.isNotEmpty()) {
            item { SectionLabel("New episodes") }
            items(data.newEpisodes.take(6)) { episode ->
                EpisodeChip(backend, episode) {
                    WearPlayerHolder.play(backend.context, PlayableItem.PodcastEp(episode))
                    onOpenNowPlaying()
                }
            }
        }

        if (data.recentlyAdded.isNotEmpty()) {
            item { SectionLabel("Recently added") }
            items(data.recentlyAdded.take(6)) { track ->
                TrackChip(backend, track) {
                    val queue = data.recentlyAdded.map { PlayableItem.Music(it) }
                    WearPlayerHolder.playQueue(backend.context, queue, data.recentlyAdded.indexOf(track).coerceAtLeast(0))
                    onOpenNowPlaying()
                }
            }
        }

        if (data.podcasts.isNotEmpty()) {
            item { SectionLabel("Podcasts") }
            items(data.podcasts.take(12)) { podcast ->
                CompactPodcastChip(backend, podcast) { onOpenPodcast(podcast.id) }
            }
        }

        if (data.playlists.isNotEmpty()) {
            item { SectionLabel("Playlists") }
            items(data.playlists.take(20)) { playlist ->
                PlaylistChip(playlist) { onOpenPlaylist(playlist.id, playlist.name) }
            }
        }

        if (!loading && reachable && data.isEmpty) {
            item { EmptyChip("Nothing here yet", "Sync your library from the phone") }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private data class LibraryData(
    val playlists: List<Playlist> = emptyList(),
    val podcasts: List<Podcast> = emptyList(),
    val newEpisodes: List<Episode> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val history: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList()
) {
    val isEmpty: Boolean
        get() = playlists.isEmpty() &&
            podcasts.isEmpty() &&
            newEpisodes.isEmpty() &&
            recentlyAdded.isEmpty() &&
            history.isEmpty() &&
            favorites.isEmpty()
}

private data class LibraryLoad(val reachable: Boolean, val data: LibraryData)

@Composable
private fun NowPlayingHero(onOpen: () -> Unit) {
    val local by WearPlayerHolder.state.collectAsState()
    val remote by NowPlayingRepository.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val backend = remember { Backend.get(ctx.applicationContext) }

    val title: String
    val subtitle: String
    val artUrl: String?
    val accent: Color
    when {
        local.isActive -> {
            val item = local.item
            title = item?.title ?: "Watch playback"
            subtitle = "Playing on this watch"
            artUrl = when (item) {
                is PlayableItem.Music -> backend.artworkUrl(item.track.artPath)
                is PlayableItem.PodcastEp -> backend.artworkUrl(item.episode.imagePath ?: item.episode.podcastImagePath)
                null -> null
            }
            accent = if (item?.isPodcast == true) WearTheme.Orange else WearTheme.Cyan
        }
        !remote.isEmpty -> {
            title = remote.title
            subtitle = "Playing on your phone"
            artUrl = remote.artworkUrl
            accent = if (remote.isPodcast || remote.isAudiobook) WearTheme.Orange else WearTheme.Cyan
        }
        else -> {
            title = "mvbar"
            subtitle = "Ready to play on your watch"
            artUrl = null
            accent = WearTheme.Cyan
        }
    }

    Chip(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors(backgroundColor = accent.copy(alpha = 0.48f)),
        icon = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(WearTheme.Surface),
                contentAlignment = Alignment.Center
            ) {
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = WearTheme.OnSurface)
                }
            }
        },
        label = {
            Text(
                title,
                color = WearTheme.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption1,
                fontWeight = FontWeight.SemiBold
            )
        },
        secondaryLabel = {
            Text(
                subtitle,
                color = WearTheme.OnSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption2
            )
        }
    )
}

@Composable
private fun CategoryChip(
    label: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = { Icon(icon, contentDescription = null, tint = accent) },
        label = { Text(label, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(subtitle, color = WearTheme.OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}

@Composable
private fun PlaylistChip(playlist: Playlist, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = WearTheme.Cyan) },
        label = { Text(playlist.name, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text("${playlist.trackCount} tracks", color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2)
        }
    )
}

@Composable
private fun CompactPodcastChip(backend: Backend, podcast: Podcast, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = {
            val art = backend.artworkUrl(podcast.imagePath ?: podcast.imageUrl)
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Default.Podcasts, contentDescription = null, tint = WearTheme.Orange)
            }
        },
        label = { Text(podcast.title, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            val subtitle = if (podcast.unplayedCount > 0) "${podcast.unplayedCount} unplayed" else podcast.author.orEmpty()
            Text(subtitle, color = WearTheme.OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}
