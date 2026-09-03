package com.mvbar.android.tv

import android.app.SearchManager
import android.content.Intent
import android.provider.MediaStore
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mvbar.android.tv.ui.MvbarTvApp

class MainActivity : ComponentActivity() {
    private val viewModel: TvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { MvbarTvApp(viewModel) }
        handleSearchIntent(intent)
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSearchIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY -> {
            viewModel.playPlayback()
            true
        }
        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            viewModel.pausePlayback()
            true
        }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
            viewModel.togglePlayPause()
            true
        }
        KeyEvent.KEYCODE_MEDIA_NEXT -> {
            viewModel.next()
            true
        }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            viewModel.previous()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun handleSearchIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEARCH && action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: return
        viewModel.openVoiceSearch(
            query,
            playImmediately = action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
        )
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == "mvbar-tv") {
            viewModel.handleDeepLink(intent.data ?: return)
        }
    }
}
