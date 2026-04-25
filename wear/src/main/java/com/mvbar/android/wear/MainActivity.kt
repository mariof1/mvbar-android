package com.mvbar.android.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mvbar.android.wear.net.WearApiClient
import com.mvbar.android.wear.ui.Backend
import com.mvbar.android.wear.ui.CacheSettingsScreen
import com.mvbar.android.wear.ui.HomeScreen
import com.mvbar.android.wear.ui.PairingScreen
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

    // Re-check pairing each time the app comes back to the foreground.
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
                    onOpenCacheSettings = { nav.navigate("cache") }
                )
            }
            composable("now_playing") { WearNowPlayingScreen() }
            composable("cache") {
                CacheSettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
