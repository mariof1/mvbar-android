package com.mvbar.android.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.Track
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.components.*
import com.mvbar.android.ui.screens.browse.BrowseScreen
import com.mvbar.android.ui.screens.detail.AlbumDetailScreen
import com.mvbar.android.ui.screens.detail.ArtistDetailScreen
import com.mvbar.android.ui.screens.detail.GenreDetailScreen
import com.mvbar.android.ui.screens.detail.PlaylistDetailScreen
import com.mvbar.android.ui.screens.detail.SmartPlaylistDetailScreen
import com.mvbar.android.ui.screens.favorites.FavoritesScreen
import com.mvbar.android.ui.screens.history.HistoryScreen
import com.mvbar.android.ui.screens.home.HomeScreen
import com.mvbar.android.ui.screens.library.LibraryScreen
import com.mvbar.android.ui.screens.nowplaying.NowPlayingScreen
import com.mvbar.android.ui.screens.search.SearchScreen
import com.mvbar.android.ui.screens.settings.SettingsScreen
import com.mvbar.android.ui.screens.smartplaylist.CreateSmartPlaylistScreen
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.BrowseViewModel
import com.mvbar.android.viewmodel.MainViewModel

enum class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BROWSE("browse", "Browse", Icons.Filled.GridView, Icons.Outlined.GridView),
    LIBRARY("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    FAVORITES("favorites", "Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainVm: MainViewModel,
    playerState: PlayerState,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val browseVm: BrowseViewModel = viewModel()
    var showSearch by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var contextTrack by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val currentRoute by navController.currentBackStackEntryAsState()
    val currentTab = currentRoute?.destination?.route

    val homeState by mainVm.homeState.collectAsState()
    val favorites by mainVm.favorites.collectAsState()
    val favoritesLoading by mainVm.favoritesLoading.collectAsState()
    val favoritesError by mainVm.favoritesError.collectAsState()
    val history by mainVm.history.collectAsState()
    val historyLoading by mainVm.historyLoading.collectAsState()
    val historyError by mainVm.historyError.collectAsState()
    val playlists by mainVm.playlists.collectAsState()
    val smartPlaylists by mainVm.smartPlaylists.collectAsState()
    val searchResults by mainVm.searchResults.collectAsState()
    val browseState by browseVm.state.collectAsState()
    val artistTracks by browseVm.artistTracks.collectAsState()
    val artistAlbums by browseVm.artistAlbums.collectAsState()
    val artistAppearsOn by browseVm.artistAppearsOn.collectAsState()
    val albumTracks by browseVm.albumTracks.collectAsState()
    val selectedArtist by browseVm.selectedArtist.collectAsState()
    val selectedAlbum by browseVm.selectedAlbum.collectAsState()
    val genreTracks by browseVm.genreTracks.collectAsState()
    val genreLoading by browseVm.genreLoading.collectAsState()
    val lyrics by mainVm.lyrics.collectAsState()
    val lyricsLoading by mainVm.lyricsLoading.collectAsState()
    val playlistTracks by mainVm.playlistTracks.collectAsState()
    val playlistLoading by mainVm.playlistLoading.collectAsState()
    val selectedPlaylist by mainVm.selectedPlaylist.collectAsState()
    val smartPlaylistDetail by mainVm.smartPlaylistDetail.collectAsState()
    val smartPlaylistLoading by mainVm.smartPlaylistLoading.collectAsState()

    val currentTrackId = playerState.currentTrack?.id

    // Full-screen Now Playing (slide up/down)
    AnimatedVisibility(
        visible = showNowPlaying && playerState.currentTrack != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(350)
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(200))
    ) {
        NowPlayingScreen(
            state = playerState,
            lyrics = lyrics,
            lyricsLoading = lyricsLoading,
            onBack = { showNowPlaying = false },
            onTogglePlay = { mainVm.playerManager.togglePlay() },
            onNext = { mainVm.playerManager.next() },
            onPrevious = { mainVm.playerManager.previous() },
            onSeek = { mainVm.playerManager.seekTo(it) },
            onCyclePlayMode = { mainVm.playerManager.cyclePlayMode() },
            onToggleFavorite = {
                playerState.currentTrack?.let { mainVm.toggleFavorite(it.id) }
            },
            onPlayQueueItem = { mainVm.playerManager.playQueueIndex(it) },
            onRemoveFromQueue = { mainVm.playerManager.removeFromQueue(it) },
            onClearQueue = { mainVm.playerManager.clearQueue() },
            onLoadLyrics = { mainVm.loadLyrics(it) }
        )
    }


    // Search overlay
    if (showSearch) {
        SearchScreen(
            results = searchResults,
            currentTrackId = currentTrackId,
            onSearch = { mainVm.search(it) },
            onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
            onClose = { showSearch = false; mainVm.clearSearch() }
        )
        return
    }

    // Add to playlist dialog
    showAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylist = null },
            onSelect = { playlistId ->
                mainVm.addToPlaylist(playlistId, track)
                ToastManager.show("Added to playlist", ToastIcon.PLAYLIST)
            }
        )
    }

    // Track context menu bottom sheet
    contextTrack?.let { track ->
        TrackBottomSheet(
            track = track,
            onDismiss = { contextTrack = null },
            onPlayNext = {
                mainVm.playerManager.playNext(track)
                ToastManager.show("\"${track.displayTitle}\" will play next", ToastIcon.QUEUE)
            },
            onAddToQueue = {
                mainVm.addToQueue(track)
                ToastManager.show("Added to queue", ToastIcon.QUEUE)
            },
            onToggleFavorite = { mainVm.toggleFavorite(track.id) },
            onAddToPlaylist = {
                contextTrack = null
                showAddToPlaylist = track
            },
            onGoToAlbum = track.album?.let { albumName ->
                {
                    contextTrack = null
                    try { navController.navigate("album?name=${Uri.encode(albumName)}") }
                    catch (_: Exception) {}
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, "Search", tint = OnSurfaceDim)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = BackgroundDark
                    )
                )
            },
            bottomBar = {
                Column {
                    // Mini player
                    if (playerState.currentTrack != null) {
                        MiniPlayerBar(
                            state = playerState,
                            onTogglePlay = { mainVm.playerManager.togglePlay() },
                            onNext = { mainVm.playerManager.next() },
                            onTap = { showNowPlaying = true }
                        )
                    }

                    // Bottom navigation
                    NavigationBar(
                        containerColor = SurfaceDark.copy(alpha = 0.95f),
                        contentColor = OnSurface,
                        tonalElevation = 0.dp
                    ) {
                        BottomTab.entries.forEach { tab ->
                            val selected = currentTab == tab.route ||
                                (currentTab?.startsWith("artist/") == true && tab == BottomTab.BROWSE) ||
                                (currentTab?.startsWith("album") == true && tab == BottomTab.BROWSE) ||
                                (currentTab?.startsWith("genre/") == true && tab == BottomTab.BROWSE) ||
                                (currentTab == "history" && tab == BottomTab.LIBRARY) ||
                                (currentTab?.startsWith("playlist/") == true && tab == BottomTab.LIBRARY) ||
                                (currentTab?.startsWith("smart-playlist") == true && tab == BottomTab.LIBRARY)

                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentTab != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Cyan500,
                                    selectedTextColor = Cyan500,
                                    unselectedIconColor = OnSurfaceDim,
                                    unselectedTextColor = OnSurfaceDim,
                                    indicatorColor = Cyan500.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 4 } },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 4 } },
                popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 4 } }
            ) {
                composable("home") {
                    HomeScreen(
                        state = homeState,
                        currentTrackId = currentTrackId,
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onAlbumClick = { name ->
                            DebugLog.i("Nav", "Home album click: '$name'")
                            try {
                                navController.navigate("album?name=${Uri.encode(name)}")
                            } catch (e: Exception) {
                                DebugLog.e("Nav", "Album navigate failed", e)
                            }
                        },
                        onRefresh = { mainVm.loadHome() },
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable("browse") {
                    BrowseScreen(
                        state = browseState,
                        onTabChange = { browseVm.setTab(it) },
                        onArtistClick = { artist ->
                            browseVm.loadArtistDetail(artist)
                            navController.navigate("artist/${artist.id}")
                        },
                        onAlbumClick = { album ->
                            DebugLog.i("Nav", "Browse album click: '${album.displayName}'")
                            try {
                                navController.navigate("album?name=${Uri.encode(album.displayName)}")
                            } catch (e: Exception) {
                                DebugLog.e("Nav", "Album navigate failed", e)
                            }
                        },
                        onGenreClick = { genre ->
                            browseVm.loadGenreTracks(genre.name)
                            try {
                                navController.navigate("genre/${Uri.encode(genre.name)}")
                            } catch (e: Exception) {
                                DebugLog.e("Nav", "Genre navigate failed", e)
                            }
                        },
                        onRefresh = { browseVm.loadAll() },
                        onLoadMoreArtists = { browseVm.loadMoreArtists() },
                        onLoadMoreAlbums = { browseVm.loadMoreAlbums() },
                        onLoadMoreGenres = { browseVm.loadMoreGenres() }
                    )
                }

                composable("artist/{id}") {
                    ArtistDetailScreen(
                        artist = selectedArtist,
                        tracks = artistTracks,
                        albums = artistAlbums,
                        appearsOn = artistAppearsOn,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (artistTracks.isNotEmpty()) mainVm.playTrack(artistTracks.first(), artistTracks) },
                        onAlbumClick = { albumName ->
                            try { navController.navigate("album?name=${Uri.encode(albumName)}") }
                            catch (_: Exception) {}
                        },
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable(
                    route = "album?name={name}",
                    arguments = listOf(navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { entry ->
                    val name = entry.arguments?.getString("name") ?: ""
                    DebugLog.i("Nav", "Album screen opened: '$name'")
                    LaunchedEffect(name) {
                        if (name.isNotEmpty()) browseVm.loadAlbumTracks(name)
                    }
                    AlbumDetailScreen(
                        album = selectedAlbum,
                        albumName = name,
                        tracks = albumTracks,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (albumTracks.isNotEmpty()) mainVm.playTrack(albumTracks.first(), albumTracks) },
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable(
                    route = "genre/{name}",
                    arguments = listOf(navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { entry ->
                    val name = entry.arguments?.getString("name") ?: ""
                    GenreDetailScreen(
                        genreName = name,
                        tracks = genreTracks,
                        isLoading = genreLoading,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (genreTracks.isNotEmpty()) mainVm.playTrack(genreTracks.first(), genreTracks) },
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable("library") {
                    LibraryScreen(
                        playlists = playlists,
                        smartPlaylists = smartPlaylists,
                        onPlaylistClick = { playlistId ->
                            playlists.find { it.id == playlistId }?.let { playlist ->
                                mainVm.loadPlaylistDetail(playlist)
                                navController.navigate("playlist/${playlistId}")
                            }
                        },
                        onSmartPlaylistClick = { id ->
                            mainVm.loadSmartPlaylistDetail(id)
                            navController.navigate("smart-playlist/${id}")
                        },
                        onCreatePlaylist = { mainVm.createPlaylist(it) },
                        onCreateSmartPlaylist = {
                            navController.navigate("create-smart-playlist")
                        },
                        onRefresh = {
                            mainVm.loadPlaylists()
                            mainVm.loadSmartPlaylists()
                        },
                        onHistoryClick = { navController.navigate("history") }
                    )
                }

                composable("playlist/{id}") {
                    PlaylistDetailScreen(
                        playlist = selectedPlaylist,
                        tracks = playlistTracks,
                        isLoading = playlistLoading,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = {
                            if (playlistTracks.isNotEmpty()) mainVm.playTrack(playlistTracks.first(), playlistTracks)
                        },
                        onRemoveTrack = { trackId ->
                            selectedPlaylist?.let { mainVm.removeFromPlaylist(it.id, trackId) }
                        },
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable("smart-playlist/{id}") {
                    SmartPlaylistDetailScreen(
                        detail = smartPlaylistDetail,
                        isLoading = smartPlaylistLoading,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = {
                            smartPlaylistDetail?.tracks?.let { tracks ->
                                if (tracks.isNotEmpty()) mainVm.playTrack(tracks.first(), tracks)
                            }
                        },
                        onDelete = {
                            smartPlaylistDetail?.let {
                                mainVm.deleteSmartPlaylist(it.id)
                                navController.popBackStack()
                            }
                        },
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable("create-smart-playlist") {
                    CreateSmartPlaylistScreen(
                        genres = browseState.genres,
                        onBack = { navController.popBackStack() },
                        onCreate = { name, sort, filters ->
                            mainVm.createSmartPlaylist(name, sort, filters)
                        }
                    )
                }

                composable("history") {
                    HistoryScreen(
                        history = history,
                        currentTrackId = currentTrackId,
                        onPlayTrack = { mainVm.playTrack(it) },
                        onRefresh = { mainVm.loadHistory() },
                        onBack = { navController.popBackStack() },
                        isLoading = historyLoading,
                        error = historyError,
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable("favorites") {
                    FavoritesScreen(
                        favorites = favorites,
                        currentTrackId = currentTrackId,
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onToggleFavorite = { mainVm.toggleFavorite(it) },
                        onRefresh = { mainVm.loadFavorites() },
                        isLoading = favoritesLoading,
                        error = favoritesError,
                        onTrackLongPress = { contextTrack = it }
                    )
                }

                composable("settings") {
                    SettingsScreen(onLogout = onLogout)
                }
            }
        }

        // Toast overlay — above everything
        MvbarToastHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (playerState.currentTrack != null) 180.dp else 100.dp)
        )
    }
}
