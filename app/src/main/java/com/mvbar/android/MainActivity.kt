package com.mvbar.android

import android.os.Bundle
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
        setContent {
            MvbarTheme {
                val authVm: AuthViewModel = viewModel()
                val authState by authVm.state.collectAsState()

                AnimatedContent(
                    targetState = authState,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                    },
                    label = "auth"
                ) { state ->
                    when {
                        state.isLoading && !state.isLoggedIn -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Cyan500)
                            }
                        }
                        !state.isLoggedIn -> {
                            LoginScreen(
                                authState = state,
                                onLogin = { server, email, pass -> authVm.login(server, email, pass) }
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
}
