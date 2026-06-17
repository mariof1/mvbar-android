package com.mvbar.android.ui.components

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.mvbar.android.R
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.ui.theme.OnSurfaceDim

@Composable
fun CastRouteButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }

    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize().alpha(0f),
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    val buttonSize = (44 * viewContext.resources.displayMetrics.density).toInt()
                    try {
                        val themedContext = ContextThemeWrapper(viewContext, R.style.Theme_Mvbar_MediaRouter)
                        val button = MediaRouteButton(themedContext).apply {
                            contentDescription = "Cast"
                            setAlwaysVisible(true)
                            CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
                        }
                        routeButton = button
                        addView(
                            button,
                            FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER)
                        )
                    } catch (e: Exception) {
                        routeButton = null
                        DebugLog.e("Cast", "Unable to render Cast route button", e)
                    }
                }
            },
            update = { it.contentDescription = "Cast" }
        )
        IconButton(
            onClick = { routeButton?.performClick() },
            modifier = Modifier.matchParentSize(),
            enabled = routeButton != null
        ) {
            Icon(Icons.Filled.Cast, contentDescription = "Cast", tint = OnSurfaceDim)
        }
    }
}
