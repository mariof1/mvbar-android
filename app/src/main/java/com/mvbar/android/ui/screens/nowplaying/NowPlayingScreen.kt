package com.mvbar.android.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.Episode
import com.mvbar.android.data.model.SmartPlaylist
import com.mvbar.android.data.model.Track
import com.mvbar.android.player.PlayMode
import com.mvbar.android.player.PlayerState
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.ui.components.ArtworkImage
import com.mvbar.android.ui.components.CastRouteButton
import com.mvbar.android.ui.components.GlowingProgressLine
import com.mvbar.android.ui.components.GlowingSeekbar
import com.mvbar.android.ui.components.LyricsView
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    lyrics: List<com.mvbar.android.data.model.LyricLine> = emptyList(),
    lyricsLoading: Boolean = false,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayQueueItem: (Int) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onLoadLyrics: ((Int) -> Unit)? = null,
    // Queue panel tabs data
    playlists: List<Playlist> = emptyList(),
    smartPlaylists: List<SmartPlaylist> = emptyList(),
    favorites: List<Track> = emptyList(),
    podcastContinueListening: List<Episode> = emptyList(),
    playlistTracks: List<Track> = emptyList(),
    playlistTracksLoading: Boolean = false,
    smartPlaylistTracks: List<Track> = emptyList(),
    smartPlaylistTracksLoading: Boolean = false,
    onLoadPlaylistTracks: (Int) -> Unit = {},
    onLoadSmartPlaylistTracks: (Int) -> Unit = {},
    onPlayTrackWithQueue: (Track, List<Track>) -> Unit = { _, _ -> },
    // All tracks tab
    allTracks: List<Track> = emptyList(),
    allTracksLoading: Boolean = false,
    hasMoreAllTracks: Boolean = false,
    onLoadAllTracks: () -> Unit = {},
    onLoadMoreAllTracks: () -> Unit = {},
    onShuffleAllTracks: (Track?) -> Unit = {},
    onPlayPodcastEpisode: (Episode) -> Unit = {},
    initialQueueOpen: Boolean = false,
    onQueueOpenChanged: (Boolean) -> Unit = {},
    onSearch: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null
) {
    val track = state.currentTrack ?: return
    var showLyrics by remember { mutableStateOf(false) }

    // Keep screen on while lyrics are visible
    val view = LocalView.current
    DisposableEffect(showLyrics) {
        if (showLyrics) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Back gesture minimizes the player
    BackHandler(onBack = onBack)

    // Load lyrics when switching to lyrics view or track changes (skip for podcasts)
    LaunchedEffect(showLyrics, track.id) {
        if (showLyrics && !state.isPodcastMode) onLoadLyrics?.invoke(track.id)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Swipe-down-to-dismiss state
    val density = LocalDensity.current
    val screenHeightPx = with(density) {
        configuration.screenHeightDp.dp.toPx()
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDraggingToDismiss by remember { mutableStateOf(false) }
    val dismissOffset = remember { Animatable(0f) }
    val dismissThreshold = screenHeightPx * 0.15f
    val flingDismissThreshold = with(density) { 900.dp.toPx() }
    val displayOffset =
        if (isDraggingToDismiss) dragOffset.coerceAtLeast(0f) else dismissOffset.value.coerceAtLeast(0f)
    val swipeToDismissModifier = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            dragOffset = (dragOffset + delta).coerceIn(0f, screenHeightPx)
        },
        startDragImmediately = dismissOffset.isRunning,
        onDragStarted = {
            dismissOffset.stop()
            dragOffset = dismissOffset.value.coerceAtLeast(0f)
            isDraggingToDismiss = true
        },
        onDragStopped = { velocity ->
            val releaseOffset = dragOffset.coerceAtLeast(0f)
            val shouldDismiss = releaseOffset > dismissThreshold || velocity > flingDismissThreshold

            dismissOffset.snapTo(releaseOffset)
            isDraggingToDismiss = false

            if (shouldDismiss) {
                dismissOffset.animateTo(
                    screenHeightPx,
                    animationSpec = tween(180, easing = FastOutLinearInEasing)
                )
                onBack()
            } else {
                dismissOffset.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            dragOffset = 0f
        }
    )

    // Background art model (shared)
    val artModel = state.artworkUrl
        ?: if (state.isPodcastMode) ApiClient.episodeArtUrl(-track.id)
        else track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)

    // Queue panel visibility — shared, persisted via callback
    var showQueue by remember { mutableStateOf(initialQueueOpen) }
    LaunchedEffect(showQueue) { onQueueOpenChanged(showQueue) }

    if (isLandscape) {
        // ===== LANDSCAPE: standalone layout (no bottom sheet) =====

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = displayOffset }
                .then(swipeToDismissModifier)
                .background(BackgroundDark)
        ) {
            // Blurred background
            AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f }.blur(100.dp))
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val totalWidth = maxWidth
                val queueTargetWidth = if (showQueue) totalWidth * 0.42f else 0.dp
                val queueWidth by animateDpAsState(
                    targetValue = queueTargetWidth,
                    animationSpec = tween(200),
                    label = "queueWidth"
                )

                Row(modifier = Modifier.fillMaxSize()) {
                // Queue panel — always composed, width animated
                Box(
                    modifier = Modifier
                        .width(queueWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                        .background(SurfaceContainerDark.copy(alpha = 0.95f))
                ) {
                    QueuePanelContent(
                        state = state,
                        playlists = playlists,
                        smartPlaylists = smartPlaylists,
                        favorites = favorites,
                        podcastContinueListening = podcastContinueListening,
                        playlistTracks = playlistTracks,
                        playlistTracksLoading = playlistTracksLoading,
                        smartPlaylistTracks = smartPlaylistTracks,
                        smartPlaylistTracksLoading = smartPlaylistTracksLoading,
                        allTracks = allTracks,
                        allTracksLoading = allTracksLoading,
                        hasMoreAllTracks = hasMoreAllTracks,
                        onLoadAllTracks = onLoadAllTracks,
                        onLoadMoreAllTracks = onLoadMoreAllTracks,
                        onShuffleAllTracks = onShuffleAllTracks,
                        onPlayPodcastEpisode = onPlayPodcastEpisode,
                        onPlayQueueItem = onPlayQueueItem,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        onLoadPlaylistTracks = onLoadPlaylistTracks,
                        onLoadSmartPlaylistTracks = onLoadSmartPlaylistTracks,
                        onPlayTrackWithQueue = onPlayTrackWithQueue,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(totalWidth * 0.42f)
                    )
                }

                // Right side: art + controls
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (showQueue) {
                        // Queue-open layout: artwork as full background, larger controls overlay
                        AsyncImage(
                            model = artModel, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f }
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Top bar
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = OnSurface, modifier = Modifier.size(32.dp))
                                }
                                Row {
                                    if (!state.isPodcastMode && !state.isAudiobookMode) {
                                        CastRouteButton(isCasting = state.isCasting)
                                    }
                                    IconButton(onClick = onSearch) {
                                        Icon(Icons.Filled.Search, "Search", tint = OnSurfaceDim, modifier = Modifier.size(28.dp))
                                    }
                                    if (!state.isPodcastMode && !state.isAudiobookMode) {
                                        onAddToPlaylist?.let {
                                            IconButton(onClick = it) {
                                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = OnSurfaceDim, modifier = Modifier.size(28.dp))
                                            }
                                        }
                                        IconButton(onClick = { showLyrics = !showLyrics }) {
                                            Icon(Icons.Filled.Lyrics, "Lyrics", tint = if (showLyrics) Cyan500 else OnSurfaceDim, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    IconButton(onClick = { showQueue = !showQueue }, modifier = Modifier.size(52.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = Cyan500, modifier = Modifier.size(34.dp))
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showLyrics) {
                                    EmbeddedLyricsPanel(
                                        lyrics = lyrics,
                                        isLoading = lyricsLoading,
                                        positionMs = state.position,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(track.displayTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(4.dp))
                                        Text(track.displayArtist, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Seekbar
                            var isDragging by remember { mutableStateOf(false) }
                            var dragProgress by remember { mutableFloatStateOf(0f) }
                            val currentProgress = if (isDragging) dragProgress else if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f

                            GlowingSeekbar(
                                progress = currentProgress,
                                onProgressChange = { isDragging = true; dragProgress = it },
                                onSeekFinished = { isDragging = false; onSeek((dragProgress * state.duration).toLong()) },
                                accent = if (state.isPodcastMode || state.isAudiobookMode) Orange500 else Cyan500,
                                accentHighlight = if (state.isPodcastMode || state.isAudiobookMode) Orange400 else Cyan400,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val displayPosition = if (isDragging) (dragProgress * state.duration).toLong() else state.position
                                Text(formatTime(displayPosition), style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                                Text(formatTime(state.duration), style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                            }

                            Spacer(Modifier.height(8.dp))

                            // Media buttons — larger
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(48.dp))
                                else IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(48.dp)) {
                                    Icon(when (state.playMode) { PlayMode.SHUFFLE -> Icons.Filled.Shuffle; PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne; else -> Icons.Filled.Repeat },
                                        "Play Mode", tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim, modifier = Modifier.size(28.dp))
                                }
                                if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Text("-15", color = OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                else IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(36.dp)) }
                                IconButton(onClick = onTogglePlay, modifier = Modifier.size(72.dp).background(if (state.isPodcastMode) Orange500 else Cyan500, CircleShape)) {
                                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(44.dp))
                                }
                                if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Text("+15", color = OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                else IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(36.dp)) }
                                if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(48.dp))
                                else IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                                    Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                                        tint = if (state.isFavorite) Pink500 else OnSurfaceDim, modifier = Modifier.size(28.dp))
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                        }
                    } else {
                        // Queue-closed layout: standard side-by-side art + controls
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Top bar
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = OnSurface, modifier = Modifier.size(28.dp))
                                }
                                Row {
                                    if (!state.isPodcastMode && !state.isAudiobookMode) {
                                        CastRouteButton(isCasting = state.isCasting)
                                    }
                                    IconButton(onClick = onSearch) {
                                        Icon(Icons.Filled.Search, "Search", tint = OnSurfaceDim)
                                    }
                                    if (!state.isPodcastMode && !state.isAudiobookMode) {
                                        onAddToPlaylist?.let {
                                            IconButton(onClick = it) {
                                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = OnSurfaceDim)
                                            }
                                        }
                                        IconButton(onClick = { showLyrics = !showLyrics }) {
                                            Icon(Icons.Filled.Lyrics, "Lyrics", tint = if (showLyrics) Cyan500 else OnSurfaceDim)
                                        }
                                    }
                                    IconButton(onClick = { showQueue = !showQueue }, modifier = Modifier.size(52.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = if (showQueue) Cyan500 else OnSurfaceDim, modifier = Modifier.size(34.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            // Art + info + controls
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(0.45f).padding(8.dp), contentAlignment = Alignment.Center) {
                                    if (showLyrics) {
                                        EmbeddedLyricsPanel(
                                            lyrics = lyrics,
                                            isLoading = lyricsLoading,
                                            positionMs = state.position,
                                            modifier = Modifier
                                                .fillMaxHeight(0.8f)
                                                .aspectRatio(1f)
                                                .shadow(16.dp, RoundedCornerShape(16.dp))
                                        )
                                    } else {
                                        AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxHeight(0.8f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).shadow(16.dp, RoundedCornerShape(16.dp)))
                                    }
                                }

                                Column(modifier = Modifier.weight(0.55f).padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(track.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    Text(track.displayArtist, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)

                                    Spacer(Modifier.height(8.dp))

                                    var isDragging by remember { mutableStateOf(false) }
                                    var dragProgress by remember { mutableFloatStateOf(0f) }
                                    val currentProgress = if (isDragging) dragProgress else if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f

                                    GlowingSeekbar(
                                progress = currentProgress,
                                onProgressChange = { isDragging = true; dragProgress = it },
                                onSeekFinished = { isDragging = false; onSeek((dragProgress * state.duration).toLong()) },
                                accent = if (state.isPodcastMode || state.isAudiobookMode) Orange500 else Cyan500,
                                accentHighlight = if (state.isPodcastMode || state.isAudiobookMode) Orange400 else Cyan400,
                                modifier = Modifier.fillMaxWidth()
                            )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        val displayPosition = if (isDragging) (dragProgress * state.duration).toLong() else state.position
                                        Text(formatTime(displayPosition), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                                        Text(formatTime(state.duration), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(36.dp))
                                        else IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(36.dp)) {
                                            Icon(when (state.playMode) { PlayMode.SHUFFLE -> Icons.Filled.Shuffle; PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne; else -> Icons.Filled.Repeat },
                                                "Play Mode", tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim, modifier = Modifier.size(20.dp))
                                        }
                                        if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) { Text("-15", color = OnSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                                        else IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(28.dp)) }
                                        IconButton(onClick = onTogglePlay, modifier = Modifier.size(56.dp).background(if (state.isPodcastMode) Orange500 else Cyan500, CircleShape)) {
                                            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                                        }
                                        if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) { Text("+15", color = OnSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                                        else IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(28.dp)) }
                                        if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(36.dp))
                                        else IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                                            Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                                                tint = if (state.isFavorite) Pink500 else OnSurfaceDim, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                } // Row
            } // BoxWithConstraints

        }
    } else {
        // ===== PORTRAIT: standalone layout with toggleable queue =====

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = displayOffset }
                .then(swipeToDismissModifier)
                .background(BackgroundDark)
        ) {
            // Blurred background
            AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f }.blur(100.dp))
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (showQueue) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top actions
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        if (!state.isPodcastMode && !state.isAudiobookMode) {
                            CastRouteButton(isCasting = state.isCasting)
                        }
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, "Search", tint = OnSurfaceDim)
                        }
                        if (!state.isPodcastMode && !state.isAudiobookMode) {
                            onAddToPlaylist?.let {
                                IconButton(onClick = it) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = OnSurfaceDim)
                                }
                            }
                            IconButton(onClick = { showLyrics = !showLyrics }) {
                                Icon(Icons.Filled.Lyrics, "Lyrics", tint = if (showLyrics) Cyan500 else OnSurfaceDim)
                            }
                        }
                        IconButton(onClick = { showQueue = !showQueue }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                tint = if (showQueue) Cyan500 else OnSurfaceDim,
                                modifier = Modifier.size(34.dp))
                        }
                    }
                }

                if (showQueue) {
                    // ---- COMPACT MODE: art+info row, seekbar, controls, then queue ----
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.displayTitle, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.displayArtist, style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    // Seekbar
                    var isDragging by remember { mutableStateOf(false) }
                    var dragProgress by remember { mutableFloatStateOf(0f) }
                    val currentProgress = if (isDragging) dragProgress
                        else if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f

                    GlowingSeekbar(
                                progress = currentProgress,
                                onProgressChange = { isDragging = true; dragProgress = it },
                                onSeekFinished = { isDragging = false; onSeek((dragProgress * state.duration).toLong()) },
                                accent = if (state.isPodcastMode || state.isAudiobookMode) Orange500 else Cyan500,
                                accentHighlight = if (state.isPodcastMode || state.isAudiobookMode) Orange400 else Cyan400,
                                modifier = Modifier.fillMaxWidth()
                            )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val displayPosition = if (isDragging) (dragProgress * state.duration).toLong() else state.position
                        Text(formatTime(displayPosition), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                        Text(formatTime(state.duration), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                    }

                    // Compact controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(36.dp))
                        else IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(36.dp)) {
                            Icon(when (state.playMode) { PlayMode.SHUFFLE -> Icons.Filled.Shuffle; PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne; else -> Icons.Filled.Repeat },
                                "Play Mode", tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim, modifier = Modifier.size(20.dp))
                        }
                        if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) { Text("-15", color = OnSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                        else IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(28.dp)) }
                        IconButton(onClick = onTogglePlay, modifier = Modifier.size(56.dp).background(if (state.isPodcastMode) Orange500 else Cyan500, CircleShape)) {
                            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                        if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) { Text("+15", color = OnSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                        else IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(28.dp)) }
                        if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(36.dp))
                        else IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                            Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                                tint = if (state.isFavorite) Pink500 else OnSurfaceDim, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    QueuePanelContent(
                        state = state,
                        playlists = playlists,
                        smartPlaylists = smartPlaylists,
                        favorites = favorites,
                        podcastContinueListening = podcastContinueListening,
                        playlistTracks = playlistTracks,
                        playlistTracksLoading = playlistTracksLoading,
                        smartPlaylistTracks = smartPlaylistTracks,
                        smartPlaylistTracksLoading = smartPlaylistTracksLoading,
                        allTracks = allTracks,
                        allTracksLoading = allTracksLoading,
                        hasMoreAllTracks = hasMoreAllTracks,
                        onLoadAllTracks = onLoadAllTracks,
                        onLoadMoreAllTracks = onLoadMoreAllTracks,
                        onShuffleAllTracks = onShuffleAllTracks,
                        onPlayPodcastEpisode = onPlayPodcastEpisode,
                        onPlayQueueItem = onPlayQueueItem,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        onLoadPlaylistTracks = onLoadPlaylistTracks,
                        onLoadSmartPlaylistTracks = onLoadSmartPlaylistTracks,
                        onPlayTrackWithQueue = onPlayTrackWithQueue,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // ---- FULL MODE: normal portrait layout ----
                    Spacer(Modifier.weight(0.5f))

                    AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp)).shadow(24.dp, RoundedCornerShape(20.dp)))

                    Spacer(Modifier.height(32.dp))

                    Text(track.displayTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(track.displayArtist, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(track.displayAlbum, style = MaterialTheme.typography.bodySmall, color = OnSurfaceSubtle,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)

                    Spacer(Modifier.height(24.dp))

                    // Seekbar
                    var isDragging by remember { mutableStateOf(false) }
                    var dragProgress by remember { mutableFloatStateOf(0f) }
                    val currentProgress = if (isDragging) dragProgress
                        else if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f

                    GlowingSeekbar(
                                progress = currentProgress,
                                onProgressChange = { isDragging = true; dragProgress = it },
                                onSeekFinished = { isDragging = false; onSeek((dragProgress * state.duration).toLong()) },
                                accent = if (state.isPodcastMode || state.isAudiobookMode) Orange500 else Cyan500,
                                accentHighlight = if (state.isPodcastMode || state.isAudiobookMode) Orange400 else Cyan400,
                                modifier = Modifier.fillMaxWidth()
                            )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val displayPosition = if (isDragging) (dragProgress * state.duration).toLong() else state.position
                        Text(formatTime(displayPosition), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                        Text(formatTime(state.duration), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(48.dp))
                        else IconButton(onClick = onCyclePlayMode) {
                            Icon(when (state.playMode) { PlayMode.SHUFFLE -> Icons.Filled.Shuffle; PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne; else -> Icons.Filled.Repeat },
                                "Play Mode", tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim, modifier = Modifier.size(24.dp))
                        }
                        if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Text("-15", color = OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        else IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(36.dp)) }
                        IconButton(onClick = onTogglePlay, modifier = Modifier.size(72.dp).background(if (state.isPodcastMode) Orange500 else Cyan500, CircleShape)) {
                            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(40.dp))
                        }
                        if (state.isPodcastMode || state.isAudiobookMode) IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Text("+15", color = OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        else IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(36.dp)) }
                        if (state.isPodcastMode || state.isAudiobookMode) Spacer(Modifier.size(48.dp))
                        else IconButton(onClick = onToggleFavorite) {
                            Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                                tint = if (state.isFavorite) Pink500 else OnSurfaceDim, modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(Modifier.weight(1f))
                }
            }

            // Full-screen lyrics overlay
            if (showLyrics && !state.isPodcastMode && !state.isAudiobookMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .clickable { showLyrics = false }
                ) {
                    com.mvbar.android.ui.components.LyricsView(
                        lyrics = lyrics, isLoading = lyricsLoading, positionMs = state.position,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbeddedLyricsPanel(
    lyrics: List<com.mvbar.android.data.model.LyricLine>,
    isLoading: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.38f))
    ) {
        LyricsView(
            lyrics = lyrics,
            isLoading = isLoading,
            positionMs = positionMs,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            lineSpacing = 8.dp,
            syncedTopSpacer = 12.dp,
            unsyncedTopSpacer = 0.dp,
            bottomSpacer = 72.dp
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
