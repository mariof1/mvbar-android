package com.mvbar.android.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
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

/**
 * Unified library — no Pods/Music tab split. Single ScalingLazyColumn:
 * [Now Playing hero] [Search · Settings buttons] [Recents] [Library categories]
 * [Latest episodes] [Subscribed podcasts] [Playlists]. Browse anything from
 * a single screen.
 */
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

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var podcasts by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var newEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var recents by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(Unit) {
        playlists = backend.playlists()
        podcasts = backend.podcasts()
        newEpisodes = backend.newEpisodes()
        recents = backend.recentTracks()
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(WearTheme.Background)
    ) {
        item { NowPlayingHero(onOpen = onOpenNowPlaying) }
        item {
            QuickActions(
                onSearch = { showSearch = true },
                onSettings = onOpenSettings
            )
        }

        item { SectionLabel("Browse") }
        item {
            CategoryChip("Albums", Icons.Default.Album, WearTheme.Cyan, onOpenAlbums)
        }
        item {
            CategoryChip("Smart playlists", Icons.Default.AutoAwesome, WearTheme.Pink, onOpenSmartPlaylists)
        }
        item {
            CategoryChip("Favorites", Icons.Default.Favorite, WearTheme.Pink) {
                onOpenTrackList("Favorites") { backend.favorites() }
            }
        }
        item {
            CategoryChip("History", Icons.Default.History, WearTheme.Cyan) {
                onOpenTrackList("History") { backend.history() }
            }
        }

        if (recents.isNotEmpty()) {
            item { SectionLabel("Recently played") }
            items(recents.take(8)) { t ->
                TrackChip(backend, t, onClick = {
                    val list = recents.map { PlayableItem.Music(it) }
                    val idx = recents.indexOf(t).coerceAtLeast(0)
                    WearPlayerHolder.playQueue(backend.context, list, idx)
                    onOpenNowPlaying()
                })
            }
        }

        if (newEpisodes.isNotEmpty()) {
            item { SectionLabel("New episodes") }
            items(newEpisodes.take(8)) { ep ->
                EpisodeChip(backend, ep, onClick = {
                    WearPlayerHolder.play(backend.context, PlayableItem.PodcastEp(ep))
                    onOpenNowPlaying()
                })
            }
        }

        if (podcasts.isNotEmpty()) {
            item { SectionLabel("Podcasts") }
            items(podcasts) { p ->
                CompactPodcastChip(backend, p, onClick = { onOpenPodcast(p.id) })
            }
        }

        if (playlists.isNotEmpty()) {
            item { SectionLabel("Playlists") }
            items(playlists) { pl ->
                Chip(
                    onClick = { onOpenPlaylist(pl.id, pl.name) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = WearTheme.Cyan) },
                    label = { Text(pl.name, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text("${pl.trackCount} tracks", color = WearTheme.OnSurfaceDim, style = MaterialTheme.typography.caption2) }
                )
            }
        }
    }
}

@Composable
private fun NowPlayingHero(onOpen: () -> Unit) {
    val local by WearPlayerHolder.state.collectAsState()
    val remote by NowPlayingRepository.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val backend = remember { Backend.get(ctx.applicationContext) }

    val title: String?
    val subtitle: String
    val artUrl: String?
    val accent: Color
    when {
        local.isActive -> {
            val item = local.item
            title = item?.title
            subtitle = item?.subtitle.orEmpty()
            artUrl = (item as? PlayableItem.Music)?.track?.artPath?.let { backend.artworkUrl(it) }
            accent = if (item?.isPodcast == true) WearTheme.Orange else WearTheme.Cyan
        }
        !remote.isEmpty -> {
            title = remote.title
            subtitle = remote.artist
            artUrl = null
            accent = if (remote.isPodcast || remote.isAudiobook) WearTheme.Orange else WearTheme.Cyan
        }
        else -> { title = null; subtitle = ""; artUrl = null; accent = WearTheme.Cyan }
    }
    if (title == null) return

    Chip(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors(backgroundColor = accent.copy(alpha = 0.55f)),
        icon = {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
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
            Text(title, color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption1, fontWeight = FontWeight.SemiBold)
        },
        secondaryLabel = if (subtitle.isNotBlank()) {
            { Text(subtitle, color = WearTheme.OnSurface.copy(alpha = 0.85f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.caption2) }
        } else null
    )
}

@Composable
private fun QuickActions(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconAction("Search", Icons.Default.Mic, WearTheme.Cyan, onSearch)
        Spacer(Modifier.width(20.dp))
        IconAction("Settings", Icons.Default.Settings, WearTheme.Surface, onSettings, iconTint = WearTheme.OnSurface)
    }
}

@Composable
private fun IconAction(label: String, icon: ImageVector, bg: Color, onClick: () -> Unit, iconTint: Color = WearTheme.OnSurface) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.primaryButtonColors(backgroundColor = bg),
        modifier = Modifier.size(44.dp).clip(CircleShape)
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = WearTheme.OnSurfaceDim,
        style = MaterialTheme.typography.caption2,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun CategoryChip(label: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = { Icon(icon, contentDescription = null, tint = accent) },
        label = { Text(label, color = WearTheme.OnSurface) }
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
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Icon(Icons.Default.Podcasts, contentDescription = null, tint = WearTheme.Orange)
            }
        },
        label = { Text(podcast.title.orEmpty(), color = WearTheme.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}
