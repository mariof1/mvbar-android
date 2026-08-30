package com.mvbar.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.player.PlayerManager
import com.mvbar.android.player.PlaybackService
import com.mvbar.android.social.SocialNavigationRequests
import com.mvbar.android.social.SocialNotificationManager
import com.mvbar.android.social.PlaylistNavigationRequests
import com.mvbar.android.ui.navigation.MainScreen
import com.mvbar.android.ui.screens.login.LoginScreen
import com.mvbar.android.ui.theme.Cyan500
import com.mvbar.android.ui.theme.MvbarTheme
import com.mvbar.android.viewmodel.AuthViewModel
import com.mvbar.android.viewmodel.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleMediaSearchIntent(intent)
        handleSocialIntent(intent)
        setContent {
            MvbarTheme {
                LaunchedEffect(Unit) {
                    delay(750)
                    AudioCacheManager.warmUp(this@MainActivity)
                }

                val authVm: AuthViewModel = viewModel()
                val authState by authVm.state.collectAsState()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(authState.isLoggedIn) {
                    if (!authState.isLoggedIn || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        return@LaunchedEffect
                    }
                    val prefs = getSharedPreferences("mvbar_permissions", MODE_PRIVATE)
                    val alreadyAsked = prefs.getBoolean("social_notifications_asked", false)
                    if (!alreadyAsked && ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        prefs.edit().putBoolean("social_notifications_asked", true).apply()
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

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
        setIntent(intent)
        handleMediaSearchIntent(intent)
        handleSocialIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleCastVolumeKey(keyCode, isKeyDown = true)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (handleCastVolumeKey(keyCode, isKeyDown = false)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun handleCastVolumeKey(keyCode: Int, isKeyDown: Boolean): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        val playerManager = PlayerManager.getInstance(applicationContext)
        if (!playerManager.isCasting()) return false

        if (isKeyDown) {
            val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
            playerManager.adjustCastVolume(direction)
        }
        return true
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

    private fun handleSocialIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(SocialNotificationManager.EXTRA_OPEN_SOCIAL, false) == true) {
            intent.removeExtra(SocialNotificationManager.EXTRA_OPEN_SOCIAL)
            SocialNavigationRequests.openSocial()
        }
        if (intent?.getBooleanExtra(SocialNotificationManager.EXTRA_OPEN_PLAYLISTS, false) == true) {
            intent.removeExtra(SocialNotificationManager.EXTRA_OPEN_PLAYLISTS)
            PlaylistNavigationRequests.openPlaylists()
        }
    }
}
