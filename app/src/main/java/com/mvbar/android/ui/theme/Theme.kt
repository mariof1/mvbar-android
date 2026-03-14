package com.mvbar.android.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MvbarDarkColorScheme = darkColorScheme(
    primary = Cyan500,
    onPrimary = Color.Black,
    primaryContainer = Cyan900,
    onPrimaryContainer = Cyan400,
    secondary = Pink500,
    onSecondary = Color.White,
    secondaryContainer = Pink500.copy(alpha = 0.2f),
    onSecondaryContainer = Pink400,
    tertiary = Cyan400,
    background = BackgroundDark,
    onBackground = OnSurface,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerHigh = SurfaceContainerDark,
    outline = WhiteOverlay15,
    outlineVariant = WhiteOverlay10,
)

@Composable
fun MvbarTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = MvbarDarkColorScheme,
        typography = MvbarTypography,
        content = content
    )
}
