package com.mvbar.android.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing network online state.
 * When false, UI components should grey out uncached media items.
 */
val LocalIsOnline = staticCompositionLocalOf { true }
