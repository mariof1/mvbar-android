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
import com.mvbar.android.data.model.Track
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.components.*
import com.mvbar.android.ui.screens.browse.BrowseScreen
import com.mvbar.android.ui.screens.detail.AlbumDetailScreen
import com.mvbar.android.ui.screens.detail.ArtistDetailScreen
import com.mvbar.android.ui.screens.detail.GenreDetailScreen
import com.mvbar.android.ui.screens.favorites.FavoritesScreen
import com.mvbar.android.ui.screens.history.HistoryScreen
import com.mvbar.android.ui.screens.home.HomeScreen
import com.mvbar.android.ui.screens.library.LibraryScreen
import com.mvbar.android.ui.screens.nowplaying.NowPlayingScreen
import com.mvbar.android.ui.screens.search.SearchScreen
import com.mvbar.android.ui.screens.settings.SettingsScreen
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

    val currentRoute by navController.currentBackStackEntryAsState()
    val currentTab = currentRoute?.destination?.route

    val homeState by mainVm.homeState.collectAsState()
    val favorites by mainVm.favorites.collectAsState()
    val history by mainVm.history.collectAsState()
    val playlists by mainVm.playlists.collectAsState()
    val searchResults by mainVm.searchResults.collectAsState()
    val browseState by browseVm.state.collectAsState()
    val artistTracks by browseVm.artistTracks.collectAsState()
    val albumTracks by browseVm.albumTracks.collectAsState()
    val selectedArtist by browseVm.selectedArtist.collectAsState()
    val selectedAlbum by browseVm.selectedAlbum.collectAsState()
    val genreTracks by browseVm.genreTracks.collectAsState()
    val genreLoading by browseVm.genreLoading.collectAsState()

    val currentTrackId = playerState.currentTrack?.id

    // Full-screen Now Playing
    if (showNowPlaying && playerState.currentTrack != null) {
        NowPlayingScreen(
            state = playerState,
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
            onClearQueue = { mainVm.playerManager.clearQueue() }
        )
        return
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
                                (currentTab == "history" && tab == BottomTab.LIBRARY)

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
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (artistTracks.isNotEmpty()) mainVm.playTrack(artistTracks.first(), artistTracks) },
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
                        onPlaylistClick = { /* TODO: navigate to playlist detail */ },
                        onRefresh = { mainVm.loadPlaylists() },
                        onHistoryClick = { navController.navigate("history") }
                    )
                }

                composable("history") {
                    HistoryScreen(
                        history = history,
                        currentTrackId = currentTrackId,
                        onPlayTrack = { mainVm.playTrack(it) },
                        onRefresh = { mainVm.loadHistory() },
                        onBack = { navController.popBackStack() },
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
