package com.mvbar.android.wear

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mvbar.android.wear.net.WearApiClient
import com.mvbar.android.wear.ui.AlbumDetailScreen
import com.mvbar.android.wear.ui.AlbumsScreen
import com.mvbar.android.wear.ui.Backend
import com.mvbar.android.wear.ui.HomeScreen
import com.mvbar.android.wear.ui.PairingScreen
import com.mvbar.android.wear.ui.PlaylistTracksScreen
import com.mvbar.android.wear.ui.QueueScreen
import com.mvbar.android.wear.ui.SettingsScreen
import com.mvbar.android.wear.ui.SmartPlaylistsScreen
import com.mvbar.android.wear.ui.TrackListBuffer
import com.mvbar.android.wear.ui.TrackListScreen
import com.mvbar.android.wear.ui.WearNowPlayingScreen
import com.mvbar.android.wear.ui.WearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NowPlayingRepository.attach(applicationContext)

        setContent {
            MaterialTheme {
                MvbarWearApp()
            }
        }
    }
}

@Composable
private fun MvbarWearApp() {
    val ctx = LocalContext.current
    val nav = rememberSwipeDismissableNavController()
    val backend = remember { Backend.get(ctx.applicationContext) }
    var configured by remember { mutableStateOf(WearApiClient.isConfigured(ctx.applicationContext)) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(WearApiClient.isConfigured(ctx.applicationContext))
                kotlinx.coroutines.delay(2000)
            }
        }.collect { configured = it }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(WearTheme.Background),
        timeText = { TimeText() }
    ) {
        if (!configured) {
            PairingScreen()
            return@Scaffold
        }
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    backend = backend,
                    onOpenNowPlaying = { nav.navigate("now_playing") },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenAlbums = { nav.navigate("albums") },
                    onOpenSmartPlaylists = { nav.navigate("smart_playlists") },
                    onOpenPlaylist = { id, name ->
                        nav.navigate("playlist/$id/${Uri.encode(name)}")
                    },
                    onOpenTrackList = { title, loader ->
                        TrackListBuffer.set(title, loader)
                        nav.navigate("tracklist")
                    }
                )
            }
            composable("now_playing") {
                WearNowPlayingScreen(onOpenQueue = { nav.navigate("queue") })
            }
            composable("queue") {
                QueueScreen(onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onSignOut = {
                        nav.popBackStack("home", inclusive = false)
                    }
                )
            }
            composable("albums") {
                AlbumsScreen(
                    backend = backend,
                    onBack = { nav.popBackStack() },
                    onOpenAlbum = { name -> nav.navigate("album/${Uri.encode(name)}") }
                )
            }
            composable(
                "album/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { entry ->
                val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
                AlbumDetailScreen(
                    backend = backend,
                    albumName = name,
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("now_playing") }
                )
            }
            composable("smart_playlists") {
                SmartPlaylistsScreen(
                    backend = backend,
                    onBack = { nav.popBackStack() },
                    onOpen = { id, name -> nav.navigate("smart/$id/${Uri.encode(name)}") }
                )
            }
            composable(
                "smart/{id}/{name}",
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("name") { type = NavType.StringType }
                )
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
                TrackListScreen(
                    backend = backend,
                    title = name,
                    loader = { backend.smartPlaylistTracks(id) },
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("now_playing") }
                )
            }
            composable(
                "playlist/{id}/{name}",
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("name") { type = NavType.StringType }
                )
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
                PlaylistTracksScreen(
                    backend = backend,
                    playlistId = id,
                    title = name,
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("now_playing") }
                )
            }
            composable("tracklist") {
                val title = TrackListBuffer.title
                val loader = TrackListBuffer.loader ?: return@composable
                TrackListScreen(
                    backend = backend,
                    title = title,
                    loader = loader,
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("now_playing") }
                )
            }
        }
    }
}
