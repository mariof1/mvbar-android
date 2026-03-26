package com.mvbar.android.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.Track
import com.mvbar.android.player.PlayMode
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.theme.*
import kotlinx.coroutines.launch

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
    onLoadLyrics: ((Int) -> Unit)? = null
) {
    val track = state.currentTrack ?: return
    var showLyrics by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Back gesture minimizes the player
    BackHandler(onBack = onBack)

    // Load lyrics when switching to lyrics view or track changes (skip for podcasts)
    LaunchedEffect(showLyrics, track.id) {
        if (showLyrics && !state.isPodcastMode) onLoadLyrics?.invoke(track.id)
    }

    // Swipe-down-to-dismiss state
    val density = LocalDensity.current
    val screenHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val expandThreshold = with(density) { 40.dp.toPx() } // 40dp for expand (snappier)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val dismissThreshold = screenHeightPx * 0.15f // 15% for dismiss
    
    val displayOffset = if (isDismissing) screenHeightPx else dragOffset.coerceAtLeast(0f)

    // Bottom sheet for queue
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 72.dp, // Increased from 52dp for better grab area
        sheetContainerColor = SurfaceContainerDark,
        sheetContentColor = OnSurface,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 16.dp,
        containerColor = Color.Transparent,
        sheetDragHandle = null,
        sheetContent = {
            // Inline queue content
            QueueSheetContent(
                queue = state.queue,
                currentIndex = state.queueIndex,
                isExpanded = scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded,
                onPlayItem = onPlayQueueItem,
                onRemoveItem = onRemoveFromQueue,
                onClearQueue = onClearQueue
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = displayOffset }
    ) {
        // Main player content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scaffoldState) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > dismissThreshold) {
                                isDismissing = true
                                onBack()
                            } else if (dragOffset < -expandThreshold) {
                                // Swipe up → expand queue
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // Track both directions: positive = down, negative = up
                            dragOffset += dragAmount
                        }
                    )
                }
                .background(BackgroundDark)
        ) {
            // Dynamic blurred background
            val artModel = state.artworkUrl
                ?: if (state.isPodcastMode) {
                    ApiClient.episodeArtUrl(-track.id)
                } else {
                    track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
                }
            
            AsyncImage(
                model = artModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.6f }
                    .blur(100.dp) // Safe blur (API 31+, ignored on others)
            )
            
            // Dark overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )

            // Content
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

            if (isLandscape) {
                // Landscape: queue on left (toggleable), art + controls on right
                var showQueue by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Queue panel (animated width)
                    AnimatedVisibility(
                        visible = showQueue,
                        enter = expandHorizontally(animationSpec = tween(300), expandFrom = Alignment.Start) + fadeIn(tween(200)),
                        exit = shrinkHorizontally(animationSpec = tween(250), shrinkTowards = Alignment.Start) + fadeOut(tween(150))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.42f)
                                .background(SurfaceContainerDark.copy(alpha = 0.95f))
                        ) {
                            // Queue header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnSurface)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${state.queue.size}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                                    if (state.queue.isNotEmpty()) {
                                        Spacer(Modifier.width(4.dp))
                                        TextButton(onClick = onClearQueue) {
                                            Text("Clear", color = Pink500, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 16.dp))

                            if (state.queue.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Queue is empty", color = OnSurfaceDim)
                                }
                            } else {
                                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                                    itemsIndexed(state.queue) { index, qTrack ->
                                        val isActive = index == state.queueIndex
                                        val isPast = index < state.queueIndex
                                        Box(modifier = Modifier.graphicsLayer { alpha = if (isPast) 0.5f else 1f }) {
                                            QueueItem(
                                                track = qTrack,
                                                isActive = isActive,
                                                onPlay = { onPlayQueueItem(index) },
                                                onRemove = { onRemoveFromQueue(index) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right side: art + controls (consolidated)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Top bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = OnSurface, modifier = Modifier.size(28.dp))
                            }
                            Row {
                                if (!state.isPodcastMode && !state.isAudiobookMode) {
                                    IconButton(onClick = { showLyrics = !showLyrics }) {
                                        Icon(Icons.Filled.MusicNote, "Lyrics", tint = if (showLyrics) Cyan500 else OnSurfaceDim)
                                    }
                                }
                                IconButton(onClick = { showQueue = !showQueue }) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                        tint = if (showQueue) Cyan500 else OnSurfaceDim)
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Art + info + controls in a compact row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact album art
                            Box(
                                modifier = Modifier
                                    .weight(0.45f)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showLyrics && !state.isPodcastMode && !state.isAudiobookMode) {
                                    com.mvbar.android.ui.components.LyricsView(
                                        lyrics = lyrics,
                                        isLoading = lyricsLoading,
                                        positionMs = state.position,
                                        modifier = Modifier
                                            .fillMaxHeight(0.8f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(SurfaceDark.copy(alpha = 0.5f))
                                    )
                                } else {
                                    val artModelLand = state.artworkUrl
                                        ?: if (state.isPodcastMode) ApiClient.episodeArtUrl(-track.id)
                                        else track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
                                    AsyncImage(
                                        model = artModelLand,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxHeight(0.8f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .shadow(16.dp, RoundedCornerShape(16.dp))
                                    )
                                }
                            }

                            // Info + seekbar + controls
                            Column(
                                modifier = Modifier
                                    .weight(0.55f)
                                    .padding(start = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(track.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                Text(track.displayArtist, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)

                                Spacer(Modifier.height(8.dp))

                                // Seekbar
                                var isDragging by remember { mutableStateOf(false) }
                                var dragProgress by remember { mutableFloatStateOf(0f) }
                                val currentProgress = if (isDragging) dragProgress
                                    else if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f

                                Slider(
                                    value = currentProgress,
                                    onValueChange = { isDragging = true; dragProgress = it },
                                    onValueChangeFinished = { isDragging = false; onSeek((dragProgress * state.duration).toLong()) },
                                    colors = SliderDefaults.colors(thumbColor = Cyan500, activeTrackColor = Cyan500, inactiveTrackColor = WhiteOverlay15),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val displayPosition = if (isDragging) (dragProgress * state.duration).toLong() else state.position
                                    Text(formatTime(displayPosition), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                                    Text(formatTime(state.duration), style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                                }

                                Spacer(Modifier.height(4.dp))

                                // Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (state.isPodcastMode || state.isAudiobookMode) {
                                        Spacer(Modifier.size(36.dp))
                                    } else {
                                        IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                when (state.playMode) {
                                                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                                                    PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                                                    else -> Icons.Filled.Repeat
                                                },
                                                "Play Mode",
                                                tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (state.isPodcastMode || state.isAudiobookMode) {
                                        IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                                            Text("-15", color = OnSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                                            Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = onTogglePlay,
                                        modifier = Modifier.size(56.dp).background(if (state.isPodcastMode) Orange500 else Cyan500, CircleShape)
                                    ) {
                                        Icon(
                                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            if (state.isPlaying) "Pause" else "Play",
                                            tint = Color.Black, modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    if (state.isPodcastMode || state.isAudiobookMode) {
                                        IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                                            Text("+15", color = OnSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                                            Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    if (state.isPodcastMode || state.isAudiobookMode) {
                                        Spacer(Modifier.size(36.dp))
                                    } else {
                                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                "Favorite",
                                                tint = if (state.isFavorite) Pink500 else OnSurfaceDim,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            } else {
            // Portrait layout (original)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = OnSurface, modifier = Modifier.size(32.dp))
                    }
                    Text(
                        when {
                            state.isAudiobookMode -> "Audiobook"
                            state.isPodcastMode -> "Podcast"
                            else -> "Now Playing"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurfaceDim
                    )
                    Row {
                        if (!state.isPodcastMode && !state.isAudiobookMode) {
                            IconButton(onClick = { showLyrics = !showLyrics }) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    "Lyrics",
                                    tint = if (showLyrics) Cyan500 else OnSurfaceDim
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                                    scaffoldState.bottomSheetState.partialExpand()
                                } else {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                "Queue",
                                tint = if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) Cyan500 else OnSurfaceDim
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(0.5f))

                // Album art or lyrics view
                if (showLyrics && !state.isPodcastMode && !state.isAudiobookMode) {
                    com.mvbar.android.ui.components.LyricsView(
                        lyrics = lyrics,
                        isLoading = lyricsLoading,
                        positionMs = state.position,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceDark.copy(alpha = 0.5f))
                    )
                } else {
                    val artModel = state.artworkUrl
                        ?: if (state.isPodcastMode) {
                            ApiClient.episodeArtUrl(-track.id)
                        } else {
                            track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
                        }
                    AsyncImage(
                        model = artModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .shadow(24.dp, RoundedCornerShape(20.dp))
                    )
                }

                Spacer(Modifier.height(32.dp))

                // Track info
                Text(
                    track.displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track.displayArtist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    track.displayAlbum,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // Progress bar
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }
                
                val currentProgress = if (isDragging) {
                    dragProgress
                } else {
                    if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f
                }

                Slider(
                    value = currentProgress,
                    onValueChange = { 
                        isDragging = true
                        dragProgress = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek((dragProgress * state.duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Cyan500,
                        activeTrackColor = Cyan500,
                        inactiveTrackColor = WhiteOverlay15
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayPosition = if (isDragging) {
                        (dragProgress * state.duration).toLong()
                    } else {
                        state.position
                    }
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
                    if (state.isPodcastMode || state.isAudiobookMode) {
                        Spacer(Modifier.size(48.dp))
                    } else {
                        IconButton(onClick = onCyclePlayMode) {
                            Icon(
                                when (state.playMode) {
                                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                                    PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                                    else -> Icons.Filled.Repeat
                                },
                                "Play Mode",
                                tint = if (state.playMode != PlayMode.NORMAL) Cyan500 else OnSurfaceDim,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (state.isPodcastMode || state.isAudiobookMode) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                            Text("-15", color = OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, "Previous", tint = OnSurface, modifier = Modifier.size(36.dp))
                        }
                    }

                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(72.dp)
                            .background(if (state.isPodcastMode) Orange500 else Cyan500, CircleShape)
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (state.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    if (state.isPodcastMode || state.isAudiobookMode) {
                        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                            Text("+15", color = OnSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipNext, "Next", tint = OnSurface, modifier = Modifier.size(36.dp))
                        }
                    }

                    if (state.isPodcastMode || state.isAudiobookMode) {
                        Spacer(Modifier.size(48.dp))
                    } else {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favorite",
                                tint = if (state.isFavorite) Pink500 else OnSurfaceDim,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
            }
            } // end portrait else
        }
    }
}

@Composable
private fun QueueSheetContent(
    queue: List<Track>,
    currentIndex: Int,
    isExpanded: Boolean,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    val nextTrack = if (currentIndex < queue.size - 1) queue[currentIndex + 1] else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.fillMaxHeight(0.7f) else Modifier)
            .animateContentSize()
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(OnSurfaceDim.copy(alpha = 0.4f))
            )
        }

        if (!isExpanded) {
            // Collapsed: Show "Up Next" preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* Let the sheet handle expansion */ }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Up Next",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cyan500,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        nextTrack?.displayTitle ?: "End of queue",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    "Open Queue",
                    tint = OnSurfaceDim
                )
            }
        } else {
            // Expanded: Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${queue.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    if (queue.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onClearQueue) {
                            Text("Clear", color = Pink500, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            
            HorizontalDivider(color = WhiteOverlay10, modifier = Modifier.padding(horizontal = 20.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Queue is empty", color = OnSurfaceDim)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(queue) { index, track ->
                        val isActive = index == currentIndex
                        // Only show passed tracks if recent (e.g., previous 1-2)? 
                        // For now keep showing all.
                        val isPast = index < currentIndex
                        
                        Box(modifier = Modifier.graphicsLayer { alpha = if (isPast) 0.5f else 1f }) {
                            QueueItem(
                                track = track,
                                isActive = isActive,
                                onPlay = { onPlayItem(index) },
                                onRemove = { onRemoveItem(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    track: Track,
    isActive: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val bgColor = if (isActive) Cyan500.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Cyan400)
            )
            Spacer(Modifier.width(10.dp))
        }

        val queueArtModel = if (track.id < 0) {
            ApiClient.episodeArtUrl(-track.id)
        } else {
            track.artPath?.let { ApiClient.artPathUrl(it) } ?: ApiClient.trackArtUrl(track.id)
        }
        AsyncImage(
            model = queueArtModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) Cyan400 else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            track.durationFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDim
        )

        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, "Remove", tint = OnSurfaceDim, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
