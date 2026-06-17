package com.mvbar.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteDialogFactory
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.ui.theme.Cyan500
import com.mvbar.android.ui.theme.OnSurfaceDim

@Composable
fun CastRouteButton(
    modifier: Modifier = Modifier,
    isCasting: Boolean = false
) {
    val context = LocalContext.current
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
            .build()
    }

    LaunchedEffect(Unit) {
        try {
            CastContext.getSharedInstance(context.applicationContext)
        } catch (e: Exception) {
            DebugLog.e("Cast", "Unable to initialize Cast context", e)
        }
    }

    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (isCasting) Cyan500 else OnSurfaceDim
        val description = if (isCasting) "Cast connected" else "Cast"

        IconButton(
            onClick = {
                val shown = showCastDialog(context, selector)
                DebugLog.i("Cast", "Cast route button tapped; isCasting=$isCasting dialogShown=$shown")
                if (!shown) {
                    Toast.makeText(context, "Cast dialog is not available", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.matchParentSize()
        ) {
            Icon(Icons.Filled.Cast, contentDescription = description, tint = tint)
        }
    }
}

private const val CHOOSER_TAG = "com.mvbar.android.CAST_CHOOSER"
private const val CONTROLLER_TAG = "com.mvbar.android.CAST_CONTROLLER"

private fun showCastDialog(context: Context, selector: MediaRouteSelector): Boolean {
    val activity = context.findActivity() as? FragmentActivity ?: return false
    val fragmentManager = activity.supportFragmentManager
    if (fragmentManager.isStateSaved) return false
    if (fragmentManager.findFragmentByTag(CHOOSER_TAG) != null) return true
    if (fragmentManager.findFragmentByTag(CONTROLLER_TAG) != null) return true

    return try {
        val castContext = CastContext.getSharedInstance(activity.applicationContext)
        val hasConnectedSession = castContext.sessionManager.currentCastSession?.isConnected == true
        val router = MediaRouter.getInstance(activity)
        val factory = MediaRouteDialogFactory.getDefault()

        if (!hasConnectedSession && router.selectedRoute.isDefaultOrBluetooth) {
            factory.onCreateChooserDialogFragment().apply {
                routeSelector = selector
            }.show(fragmentManager, CHOOSER_TAG)
        } else {
            factory.onCreateControllerDialogFragment().show(fragmentManager, CONTROLLER_TAG)
        }
        true
    } catch (e: Exception) {
        DebugLog.e("Cast", "Cast route dialog failed", e)
        false
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
