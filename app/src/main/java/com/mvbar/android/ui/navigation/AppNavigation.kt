package com.mvbar.android.ui.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.data.NetworkMonitor
import com.mvbar.android.data.model.Artist
import com.mvbar.android.data.model.Playlist
import com.mvbar.android.data.model.SmartPlaylistFilters
import com.mvbar.android.data.model.SuggestResponse
import com.mvbar.android.data.model.Track
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.PlayerState
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.components.*
import com.mvbar.android.ui.screens.browse.BrowseScreen
import com.mvbar.android.ui.screens.detail.AlbumDetailScreen
import com.mvbar.android.ui.screens.detail.ArtistDetailScreen
import com.mvbar.android.ui.screens.detail.GenreDetailScreen
import com.mvbar.android.ui.screens.detail.CountryDetailScreen
import com.mvbar.android.ui.screens.detail.LanguageDetailScreen
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
import com.mvbar.android.ui.screens.podcast.PodcastsScreen
import com.mvbar.android.ui.screens.podcast.PodcastDetailScreen
import com.mvbar.android.ui.screens.podcast.SubscribePodcastDialog
import com.mvbar.android.ui.screens.audiobooks.AudiobooksScreen
import com.mvbar.android.ui.screens.audiobooks.AudiobookDetailScreen
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.BrowseViewModel
import com.mvbar.android.viewmodel.MainViewModel
import com.mvbar.android.viewmodel.PodcastViewModel
import com.mvbar.android.viewmodel.AudiobookViewModel
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun parseSuggestArtists(resp: SuggestResponse): List<Pair<Int, String>> =
    resp.items.mapNotNull { elem ->
        try {
            val obj = elem.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            id to name
        } catch (_: Exception) { null }
    }

enum class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BROWSE("browse", "Browse", Icons.Filled.GridView, Icons.Outlined.GridView),
    LIBRARY("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    PODCASTS("podcasts", "Podcasts", Icons.Filled.Podcasts, Icons.Outlined.Podcasts),
    @Suppress("DEPRECATION")
    AUDIOBOOKS("audiobooks", "Books", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    FAVORITES("favorites", "Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)
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
    val podcastVm: PodcastViewModel = viewModel()
    val audiobookVm: AudiobookViewModel = viewModel()
    var showSearch by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var contextTrack by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var showSubscribeDialog by remember { mutableStateOf(false) }

    val currentRoute by navController.currentBackStackEntryAsState()
    val currentTab = currentRoute?.destination?.route

    val isOnline by NetworkMonitor.isOnline.collectAsState()

    val homeState by mainVm.homeState.collectAsState()
    val favorites by mainVm.favorites.collectAsState()
    val favoriteIds by mainVm.favoriteIds.collectAsState()
    val favoritesLoading by mainVm.favoritesLoading.collectAsState()
    val favoritesError by mainVm.favoritesError.collectAsState()
    val history by mainVm.history.collectAsState()
    val historyLoading by mainVm.historyLoading.collectAsState()
    val historyError by mainVm.historyError.collectAsState()
    val hasMoreHistory by mainVm.hasMoreHistory.collectAsState()
    val isLoadingMoreHistory by mainVm.isLoadingMoreHistory.collectAsState()
    val playlists by mainVm.playlists.collectAsState()
    val smartPlaylists by mainVm.smartPlaylists.collectAsState()
    val searchResults by mainVm.searchResults.collectAsState()
    val searchLoading by mainVm.searchLoading.collectAsState()
    val hasMoreSearch by mainVm.hasMoreSearch.collectAsState()
    val isLoadingMoreSearch by mainVm.isLoadingMoreSearch.collectAsState()
    val browseState by browseVm.state.collectAsState()
    val artistTracks by browseVm.artistTracks.collectAsState()
    val artistAlbums by browseVm.artistAlbums.collectAsState()
    val artistAppearsOn by browseVm.artistAppearsOn.collectAsState()
    val albumTracks by browseVm.albumTracks.collectAsState()
    val selectedArtist by browseVm.selectedArtist.collectAsState()
    val selectedAlbum by browseVm.selectedAlbum.collectAsState()
    val genreTracks by browseVm.genreTracks.collectAsState()
    val genreLoading by browseVm.genreLoading.collectAsState()
    val hasMoreGenreTracks by browseVm.hasMoreGenreTracks.collectAsState()
    val isLoadingMoreGenreTracks by browseVm.isLoadingMoreGenreTracks.collectAsState()
    val countryTracks by browseVm.countryTracks.collectAsState()
    val countryLoading by browseVm.countryLoading.collectAsState()
    val hasMoreCountryTracks by browseVm.hasMoreCountryTracks.collectAsState()
    val isLoadingMoreCountryTracks by browseVm.isLoadingMoreCountryTracks.collectAsState()
    val languageTracks by browseVm.languageTracks.collectAsState()
    val languageLoading by browseVm.languageLoading.collectAsState()
    val hasMoreLanguageTracks by browseVm.hasMoreLanguageTracks.collectAsState()
    val isLoadingMoreLanguageTracks by browseVm.isLoadingMoreLanguageTracks.collectAsState()
    val hasMoreArtistTracks by browseVm.hasMoreArtistTracks.collectAsState()
    val isLoadingMoreArtistTracks by browseVm.isLoadingMoreArtistTracks.collectAsState()
    val lyrics by mainVm.lyrics.collectAsState()
    val lyricsLoading by mainVm.lyricsLoading.collectAsState()
    val playlistTracks by mainVm.playlistTracks.collectAsState()
    val playlistLoading by mainVm.playlistLoading.collectAsState()
    val selectedPlaylist by mainVm.selectedPlaylist.collectAsState()
    val smartPlaylistDetail by mainVm.smartPlaylistDetail.collectAsState()
    val smartPlaylistLoading by mainVm.smartPlaylistLoading.collectAsState()
    val allTracks by mainVm.allTracks.collectAsState()
    val allTracksLoading by mainVm.allTracksLoading.collectAsState()
    val hasMoreAllTracks by mainVm.hasMoreAllTracks.collectAsState()

    // Podcast state
    val podcastsList by podcastVm.podcasts.collectAsState()
    val podcastContinueListening by podcastVm.continueListening.collectAsState()
    val podcastIsLoading by podcastVm.isLoading.collectAsState()
    val podcastSelectedPodcast by podcastVm.selectedPodcast.collectAsState()
    val podcastEpisodes by podcastVm.episodes.collectAsState()
    val podcastSearchResults by podcastVm.searchResults.collectAsState()
    val podcastSearchLoading by podcastVm.searchLoading.collectAsState()

    // Audiobook state
    val audiobooksList by audiobookVm.audiobooks.collectAsState()
    val audiobookIsLoading by audiobookVm.isLoading.collectAsState()
    val audiobookSelected by audiobookVm.selectedAudiobook.collectAsState()
    val audiobookChapters by audiobookVm.chapters.collectAsState()
    val audiobookProgress by audiobookVm.detailProgress.collectAsState()
    val audiobookPlayingChapter by audiobookVm.playingChapter.collectAsState()

    val currentTrackId = playerState.currentTrack?.id
    val context = LocalContext.current

    // Save playback state when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                mainVm.savePlaybackState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-resume: restore last session and open player
    LaunchedEffect(Unit) {
        val autoResume = AaPreferences.getAutoResume(context)
        if (!autoResume) return@LaunchedEffect
        // Poll until service restores the queue (up to 10s for slow headunits)
        var waited = 0L
        while (waited < 10_000) {
            val state = mainVm.playerManager.state.value
            if (state.queue.isNotEmpty()) break
            kotlinx.coroutines.delay(500)
            waited += 500
        }
        val state = mainVm.playerManager.state.value
        if (state.queue.isNotEmpty() && !state.isPlaying) {
            mainVm.playerManager.togglePlay()
        }
        if (state.queue.isNotEmpty()) {
            // Restore queue panel visibility BEFORE showing player
            mainVm.queuePanelOpen = AaPreferences.getQueuePanelOpen(context)
            showNowPlaying = true
        }
    }

    val rootTabs = remember { BottomTab.entries.map { it.route }.toSet() }
    val isAtRootTab = currentTab in rootTabs
    val activity = LocalContext.current as? Activity

    // Handle system back: navigate back through screens instead of closing app
    BackHandler(enabled = !showNowPlaying) {
        val popped = navController.popBackStack()
        if (!popped) {
            // At a root tab — if not home, go to home; otherwise minimize app
            if (currentTab != "home") {
                navController.navigate("home") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                activity?.moveTaskToBack(true)
            }
        }
    }

    // Search overlay
    if (showSearch) {
        SearchScreen(
            results = searchResults,
            isLoading = searchLoading,
            currentTrackId = currentTrackId,
            onSearch = { mainVm.search(it) },
            onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
            onArtistClick = { artist ->
                showSearch = false
                mainVm.clearSearch()
                navController.navigate("artist/${artist.id}")
            },
            onAlbumClick = { album ->
                showSearch = false
                mainVm.clearSearch()
                try { navController.navigate("album?name=${Uri.encode(album.album)}") }
                catch (_: Exception) {}
            },
            onPlaylistClick = { playlist ->
                showSearch = false
                mainVm.clearSearch()
                if (playlist.kind == "smart") {
                    mainVm.loadSmartPlaylistDetail(playlist.id)
                    navController.navigate("smart-playlist/${playlist.id}")
                } else {
                    playlists.find { it.id == playlist.id }?.let { p ->
                        mainVm.loadPlaylistDetail(p)
                        navController.navigate("playlist/${playlist.id}")
                    }
                }
            },
            onTrackLongPress = { contextTrack = it },
            favoriteIds = favoriteIds,
            onToggleFavorite = { mainVm.toggleFavorite(it) },
            onClose = { showSearch = false; mainVm.clearSearch() },
            hasMore = hasMoreSearch,
            isLoadingMore = isLoadingMoreSearch,
            onLoadMore = { mainVm.loadMoreSearchResults() }
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

    // Subscribe to podcast dialog
    if (showSubscribeDialog) {
        SubscribePodcastDialog(
            searchResults = podcastSearchResults,
            searchLoading = podcastSearchLoading,
            subscribedFeedUrls = podcastsList.map { it.feedUrl }.toSet(),
            onSearch = { podcastVm.searchPodcasts(it) },
            onSubscribe = { feedUrl ->
                podcastVm.subscribe(feedUrl)
            },
            onClose = {
                showSubscribeDialog = false
                podcastVm.clearSearch()
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

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useNavRail = screenWidthDp >= 600

    // Shared tab selection logic
    fun isTabSelected(tab: BottomTab): Boolean =
        currentTab == tab.route ||
            (currentTab?.startsWith("artist/") == true && tab == BottomTab.BROWSE) ||
            (currentTab?.startsWith("album") == true && tab == BottomTab.BROWSE) ||
            (currentTab?.startsWith("genre/") == true && tab == BottomTab.BROWSE) ||
            (currentTab?.startsWith("country/") == true && tab == BottomTab.BROWSE) ||
            (currentTab?.startsWith("language/") == true && tab == BottomTab.BROWSE) ||
            (currentTab == "history" && tab == BottomTab.LIBRARY) ||
            (currentTab?.startsWith("playlist/") == true && tab == BottomTab.LIBRARY) ||
            (currentTab?.startsWith("smart-playlist") == true && tab == BottomTab.LIBRARY) ||
            (currentTab?.startsWith("podcast/") == true && tab == BottomTab.PODCASTS) ||
            (currentTab?.startsWith("audiobook/") == true && tab == BottomTab.AUDIOBOOKS)

    fun onTabClick(tab: BottomTab) {
        if (currentTab != null && currentTab !in rootTabs) {
            navController.popBackStack()
        }
        if (currentTab != tab.route) {
            navController.navigate(tab.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    CompositionLocalProvider(LocalIsOnline provides isOnline) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                val screenTitle = when {
                    currentTab == "home" -> "For You"
                    currentTab == "browse" || currentTab?.startsWith("artist/") == true ||
                        currentTab?.startsWith("album") == true || currentTab?.startsWith("genre/") == true ||
                        currentTab?.startsWith("country/") == true || currentTab?.startsWith("language/") == true -> "Browse"
                    currentTab == "library" || currentTab == "history" ||
                        currentTab?.startsWith("playlist/") == true ||
                        currentTab?.startsWith("smart-playlist") == true -> "Library"
                    currentTab == "podcasts" || currentTab?.startsWith("podcast/") == true -> "Podcasts"
                    currentTab == "audiobooks" || currentTab?.startsWith("audiobook/") == true -> "Audiobooks"
                    currentTab == "favorites" -> "Favorites"
                    currentTab == "settings" -> "Settings"
                    currentTab == "cache-browser" -> "Cache"
                    else -> ""
                }
                TopAppBar(
                    title = {
                        Text(
                            screenTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, "Search", tint = OnSurfaceDim)
                        }
                        IconButton(onClick = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(Icons.Filled.Settings, "Settings", tint = OnSurfaceDim)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundDark
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    Column(Modifier.navigationBarsPadding()) {
                        // Mini player
                        AnimatedVisibility(
                            visible = playerState.currentTrack != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            MiniPlayerBar(
                                state = playerState,
                                onTogglePlay = { mainVm.playerManager.togglePlay() },
                                onNext = { mainVm.playerManager.next() },
                                onPrevious = { mainVm.playerManager.previous() },
                                onTap = { showNowPlaying = true }
                            )
                        }

                        // Bottom navigation
                        NavigationBar(
                            containerColor = SurfaceDark.copy(alpha = 0.95f),
                            contentColor = OnSurface,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            BottomTab.entries.forEach { tab ->
                                val selected = isTabSelected(tab)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { onTabClick(tab) },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = false,
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
            }
        ) { innerPadding ->
            Row(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
                // Navigation rail for tablets
                if (useNavRail) {
                    NavigationRail(
                        containerColor = SurfaceDark.copy(alpha = 0.95f),
                        contentColor = OnSurface
                    ) {
                        Spacer(Modifier.weight(1f))
                        BottomTab.entries.forEach { tab ->
                            val selected = isTabSelected(tab)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onTabClick(tab) },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = false,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Cyan500,
                                    selectedTextColor = Cyan500,
                                    unselectedIconColor = OnSurfaceDim,
                                    unselectedTextColor = OnSurfaceDim,
                                    indicatorColor = Cyan500.copy(alpha = 0.15f)
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                // Main content column (with mini player at bottom for tablet)
                Column(modifier = Modifier.weight(1f).navigationBarsPadding()) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.weight(1f),
                enterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 3 } +
                        scaleIn(tween(300), initialScale = 0.92f)
                },
                exitTransition = {
                    fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 0.95f)
                },
                popEnterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(tween(300)) { -it / 3 } +
                        scaleIn(tween(300), initialScale = 0.92f)
                },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 3 } +
                        scaleOut(tween(250), targetScale = 0.95f)
                }
            ) {
                composable("home") {
                    HomeScreen(
                        state = homeState,
                        currentTrackId = currentTrackId,
                        favoriteIds = favoriteIds,
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
                        onToggleFavorite = { mainVm.toggleFavorite(it) },
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
                            if (genre.name.isNotBlank()) {
                                browseVm.loadGenreTracks(genre.name)
                                try {
                                    navController.navigate("genre/${Uri.encode(genre.name)}")
                                } catch (e: Exception) {
                                    DebugLog.e("Nav", "Genre navigate failed", e)
                                }
                            }
                        },
                        onCountryClick = { country ->
                            if (country.name.isNotBlank()) {
                                browseVm.loadCountryTracks(country.name)
                                try {
                                    navController.navigate("country/${Uri.encode(country.name)}")
                                } catch (e: Exception) {
                                    DebugLog.e("Nav", "Country navigate failed", e)
                                }
                            }
                        },
                        onLanguageClick = { language ->
                            if (language.name.isNotBlank()) {
                                browseVm.loadLanguageTracks(language.name)
                                try {
                                    navController.navigate("language/${Uri.encode(language.name)}")
                                } catch (e: Exception) {
                                    DebugLog.e("Nav", "Language navigate failed", e)
                                }
                            }
                        },
                        onRefresh = { browseVm.loadAll() },
                        onLoadMoreArtists = { browseVm.loadMoreArtists() },
                        onLoadMoreAlbums = { browseVm.loadMoreAlbums() },
                        onLoadMoreGenres = { browseVm.loadMoreGenres() },
                        onLoadMoreCountries = { browseVm.loadMoreCountries() },
                        onLoadMoreLanguages = { browseVm.loadMoreLanguages() }
                    )
                }

                composable(
                    route = "artist/{id}",
                    arguments = listOf(navArgument("id") {
                        type = NavType.IntType
                        defaultValue = -1
                    })
                ) { entry ->
                    val id = entry.arguments?.getInt("id") ?: -1
                    LaunchedEffect(id) {
                        if (id > 0) {
                            browseVm.loadArtistDetail(Artist(id = id))
                        }
                    }
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
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) },
                        hasMoreTracks = hasMoreArtistTracks,
                        isLoadingMoreTracks = isLoadingMoreArtistTracks,
                        onLoadMoreTracks = { browseVm.loadMoreArtistTracks() }
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
                    LaunchedEffect(name) {
                        if (name.isNotEmpty()) {
                            DebugLog.i("Nav", "Album screen opened: '$name'")
                            browseVm.loadAlbumTracks(name)
                        }
                    }
                    AlbumDetailScreen(
                        album = selectedAlbum,
                        albumName = name,
                        tracks = albumTracks,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (albumTracks.isNotEmpty()) mainVm.playTrack(albumTracks.first(), albumTracks) },
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) }
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
                        hasMore = hasMoreGenreTracks,
                        isLoadingMore = isLoadingMoreGenreTracks,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (genreTracks.isNotEmpty()) mainVm.playTrack(genreTracks.first(), genreTracks) },
                        onLoadMore = { browseVm.loadMoreGenreTracks() },
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) }
                    )
                }

                composable(
                    route = "country/{name}",
                    arguments = listOf(navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { entry ->
                    val name = entry.arguments?.getString("name") ?: ""
                    CountryDetailScreen(
                        countryName = name,
                        tracks = countryTracks,
                        isLoading = countryLoading,
                        hasMore = hasMoreCountryTracks,
                        isLoadingMore = isLoadingMoreCountryTracks,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (countryTracks.isNotEmpty()) mainVm.playTrack(countryTracks.first(), countryTracks) },
                        onLoadMore = { browseVm.loadMoreCountryTracks() },
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) }
                    )
                }

                composable(
                    route = "language/{name}",
                    arguments = listOf(navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { entry ->
                    val name = entry.arguments?.getString("name") ?: ""
                    LanguageDetailScreen(
                        languageName = name,
                        tracks = languageTracks,
                        isLoading = languageLoading,
                        hasMore = hasMoreLanguageTracks,
                        isLoadingMore = isLoadingMoreLanguageTracks,
                        currentTrackId = currentTrackId,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { track, queue -> mainVm.playTrack(track, queue) },
                        onPlayAll = { if (languageTracks.isNotEmpty()) mainVm.playTrack(languageTracks.first(), languageTracks) },
                        onLoadMore = { browseVm.loadMoreLanguageTracks() },
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) }
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
                            mainVm.loadHistory()
                        },
                        history = history,
                        currentTrackId = currentTrackId,
                        onPlayTrack = { mainVm.playTrack(it) },
                        isHistoryLoading = historyLoading,
                        historyError = historyError,
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) },
                        hasMoreHistory = hasMoreHistory,
                        isLoadingMoreHistory = isLoadingMoreHistory,
                        onLoadMoreHistory = { mainVm.loadMoreHistory() }
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
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) }
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
                        onEdit = {
                            smartPlaylistDetail?.let {
                                navController.navigate("edit-smart-playlist/${it.id}")
                            }
                        },
                        onDelete = {
                            smartPlaylistDetail?.let {
                                mainVm.deleteSmartPlaylist(it.id)
                                navController.popBackStack()
                            }
                        },
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) }
                    )
                }

                composable("edit-smart-playlist/{id}") { entry ->
                    val spId = entry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                    val sp = smartPlaylistDetail

                    // Resolve artist IDs to names for pre-population
                    val artistNames = remember { mutableStateListOf<Pair<Int, String>>() }
                    LaunchedEffect(sp?.filters) {
                        val allIds = (sp?.filters?.include?.artists.orEmpty()) +
                            (sp?.filters?.exclude?.artists.orEmpty())
                        if (allIds.isNotEmpty()) {
                            try {
                                val resp = mainVm.resolveArtistIds(allIds)
                                artistNames.clear()
                                artistNames.addAll(parseSuggestArtists(resp))
                            } catch (_: Exception) {}
                        }
                    }

                    CreateSmartPlaylistScreen(
                        genres = browseState.genres,
                        onBack = { navController.popBackStack() },
                        onCreate = { _, _, _ -> },
                        onSuggest = { kind, query -> mainVm.suggest(kind, query) },
                        editId = spId,
                        initialName = sp?.name ?: "",
                        initialSort = sp?.sort ?: "random",
                        initialFilters = sp?.filters ?: SmartPlaylistFilters(),
                        initialArtistNames = artistNames,
                        onUpdate = { id, name, sort, filters ->
                            mainVm.updateSmartPlaylist(id, name, sort, filters)
                        }
                    )
                }

                composable("create-smart-playlist") {
                    CreateSmartPlaylistScreen(
                        genres = browseState.genres,
                        onBack = { navController.popBackStack() },
                        onCreate = { name, sort, filters ->
                            mainVm.createSmartPlaylist(name, sort, filters)
                        },
                        onSuggest = { kind, query -> mainVm.suggest(kind, query) }
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
                        onTrackLongPress = { contextTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { mainVm.toggleFavorite(it) },
                        hasMore = hasMoreHistory,
                        isLoadingMore = isLoadingMoreHistory,
                        onLoadMore = { mainVm.loadMoreHistory() }
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

                composable("podcasts") {
                    PodcastsScreen(
                        podcasts = podcastsList,
                        continueListening = podcastContinueListening,
                        isLoading = podcastIsLoading,
                        onPodcastClick = { podcast ->
                            podcastVm.loadPodcastDetail(podcast.id)
                            navController.navigate("podcast/${podcast.id}")
                        },
                        onEpisodePlay = { episode ->
                            podcastVm.playEpisode(episode)
                        },
                        onMarkPlayed = { episodeId, played ->
                            podcastVm.markEpisodePlayed(episodeId, played)
                        },
                        onSubscribeClick = { showSubscribeDialog = true },
                        onRefresh = {
                            podcastVm.loadPodcasts()
                            podcastVm.loadContinueListening()
                        }
                    )
                }

                composable("podcast/{id}") {
                    PodcastDetailScreen(
                        podcast = podcastSelectedPodcast,
                        episodes = podcastEpisodes,
                        isLoading = podcastIsLoading,
                        onBack = { navController.popBackStack() },
                        onPlayEpisode = { episode ->
                            podcastVm.playEpisode(episode)
                        },
                        onMarkPlayed = { episodeId, played ->
                            podcastVm.markEpisodePlayed(episodeId, played)
                        },
                        onRefresh = {
                            podcastSelectedPodcast?.let { podcastVm.refreshPodcast(it.id) }
                        },
                        onUnsubscribe = {
                            podcastSelectedPodcast?.let {
                                podcastVm.unsubscribe(it.id)
                                navController.popBackStack()
                            }
                        }
                    )
                }

                composable("audiobooks") {
                    AudiobooksScreen(
                        audiobooks = audiobooksList,
                        isLoading = audiobookIsLoading,
                        onAudiobookClick = { book ->
                            audiobookVm.loadAudiobookDetail(book.id)
                            navController.navigate("audiobook/${book.id}")
                        },
                        onRefresh = { audiobookVm.loadAudiobooks() }
                    )
                }

                composable(
                    route = "audiobook/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.IntType })
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getInt("id") ?: return@composable
                    LaunchedEffect(bookId) { audiobookVm.loadAudiobookDetail(bookId) }
                    AudiobookDetailScreen(
                        audiobook = audiobookSelected,
                        chapters = audiobookChapters,
                        progress = audiobookProgress,
                        playingChapterId = audiobookPlayingChapter?.id,
                        isLoading = audiobookIsLoading,
                        onBack = { navController.popBackStack() },
                        onPlayChapter = { chapter, resumeMs ->
                            audiobookSelected?.let { audiobookVm.playChapter(it, chapter, resumeMs) }
                        },
                        onContinueListening = {
                            audiobookSelected?.let { audiobookVm.continueListening(it) }
                        },
                        onMarkFinished = {
                            audiobookVm.markFinished(bookId)
                        }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        onLogout = onLogout,
                        onBrowseCache = { navController.navigate("cache-browser") }
                    )
                }

                composable("cache-browser") {
                    com.mvbar.android.ui.screens.cache.CacheBrowserScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

                    // Mini player at bottom of content area (tablet only)
                    if (useNavRail) {
                        AnimatedVisibility(
                            visible = playerState.currentTrack != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            MiniPlayerBar(
                                state = playerState,
                                onTogglePlay = { mainVm.playerManager.togglePlay() },
                                onNext = { mainVm.playerManager.next() },
                                onPrevious = { mainVm.playerManager.previous() },
                                onTap = { showNowPlaying = true }
                            )
                        }
                    }
                } // Column
            } // Row
        }

        // Load playlists/favorites data when player opens
        LaunchedEffect(showNowPlaying) {
            if (showNowPlaying) {
                mainVm.loadPlaylists()
                mainVm.loadSmartPlaylists()
                mainVm.loadFavorites()
            }
        }

        // Full-screen Now Playing overlay (slide up/down)
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
                onLoadLyrics = { mainVm.loadLyrics(it) },
                playlists = playlists,
                smartPlaylists = smartPlaylists,
                favorites = favorites,
                playlistTracks = playlistTracks,
                playlistTracksLoading = playlistLoading,
                smartPlaylistTracks = smartPlaylistDetail?.tracks ?: emptyList(),
                smartPlaylistTracksLoading = smartPlaylistLoading,
                onLoadPlaylistTracks = { id ->
                    val playlist = playlists.firstOrNull { it.id == id }
                    if (playlist != null) mainVm.loadPlaylistDetail(playlist)
                },
                onLoadSmartPlaylistTracks = { mainVm.loadSmartPlaylistDetail(it) },
                onPlayTrackWithQueue = { track, queue -> mainVm.playTrack(track, queue) },
                allTracks = allTracks,
                allTracksLoading = allTracksLoading,
                hasMoreAllTracks = hasMoreAllTracks,
                onLoadAllTracks = { mainVm.loadAllTracks() },
                onLoadMoreAllTracks = { mainVm.loadMoreAllTracks() },
                initialQueueOpen = mainVm.queuePanelOpen,
                onQueueOpenChanged = { mainVm.queuePanelOpen = it },
                onSearch = { showNowPlaying = false; showSearch = true }
            )
        }

        // Toast overlay — above everything
        MvbarToastHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (playerState.currentTrack != null) 180.dp else 100.dp)
        )
    }
    } // CompositionLocalProvider
}
