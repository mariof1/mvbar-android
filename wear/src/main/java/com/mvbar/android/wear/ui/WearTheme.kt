package com.mvbar.android.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

object WearTheme {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF161616)
    val SurfaceRaised = Color(0xFF202124)
    val OnSurface = Color.White
    val OnSurfaceDim = Color(0xCCFFFFFF)
    val OnSurfaceSubtle = Color(0x99FFFFFF)
    val Cyan = Color(0xFF06B6D4)
    val CyanDark = Color(0xFF0891B2)
    val Orange = Color(0xFFF97316)
    val Pink = Color(0xFFEC4899)
    val Green = Color(0xFF4ADE80)
    val Error = Color(0xFFF87171)
}

private val MvbarWearColors = Colors(
    primary = WearTheme.Cyan,
    primaryVariant = WearTheme.CyanDark,
    secondary = WearTheme.Pink,
    secondaryVariant = WearTheme.Orange,
    background = WearTheme.Background,
    surface = WearTheme.Surface,
    error = WearTheme.Error,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = WearTheme.OnSurface,
    onSurface = WearTheme.OnSurface,
    onSurfaceVariant = WearTheme.OnSurfaceDim,
    onError = Color.Black
)

@Composable
fun MvbarWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MvbarWearColors,
        content = content
    )
}
