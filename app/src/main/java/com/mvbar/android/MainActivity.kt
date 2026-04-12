package com.mvbar.android

import android.content.Intent
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mvbar.android.player.PlaybackService
import com.mvbar.android.ui.navigation.MainScreen
import com.mvbar.android.ui.screens.login.LoginScreen
import com.mvbar.android.ui.theme.Cyan500
import com.mvbar.android.ui.theme.MvbarTheme
import com.mvbar.android.viewmodel.AuthViewModel
import com.mvbar.android.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleMediaSearchIntent(intent)
        setContent {
            MvbarTheme {
                val authVm: AuthViewModel = viewModel()
                val authState by authVm.state.collectAsState()

                // Derive a stable screen key so AnimatedContent only animates on
                // major transitions (loading → login → main), not every AuthState change.
                val screenKey = when {
                    authState.isLoggedIn -> "main"
                    authState.isLoading && !authState.isLoggedIn &&
                        authState.error == null && !authState.googleEnabled -> "loading"
                    else -> "login"
                }

                AnimatedContent(
                    targetState = screenKey,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                    },
                    label = "auth"
                ) { target ->
                    when (target) {
                        "loading" -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Cyan500)
                            }
                        }
                        "login" -> {
                            LoginScreen(
                                authState = authState,
                                onLogin = { server, email, pass -> authVm.login(server, email, pass) },
                                onGoogleSignIn = { server, idToken -> authVm.googleSignIn(server, idToken) },
                                onCheckGoogleAuth = { server -> authVm.checkGoogleAuth(server) }
                            )
                        }
                        else -> {
                            val mainVm: MainViewModel = viewModel()
                            val playerState by mainVm.playerManager.state.collectAsState()
                            MainScreen(
                                mainVm = mainVm,
                                playerState = playerState,
                                onLogout = { authVm.logout() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMediaSearchIntent(intent)
    }

    private fun handleMediaSearchIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == "android.media.action.MEDIA_PLAY_FROM_SEARCH" ||
            action == "android.intent.action.MEDIA_PLAY_FROM_SEARCH") {
            val query = intent.getStringExtra("query")
                ?: intent.getStringExtra(android.app.SearchManager.QUERY)
                ?: return
            if (query.isBlank()) return
            // Forward to PlaybackService voice command handler
            val svcIntent = Intent(this, PlaybackService::class.java).apply {
                this.action = PlaybackService.ACTION_VOICE_COMMAND
                putExtra("command", "play")
                putExtra("query", query)
            }
            startForegroundService(svcIntent)
        }
    }
}
