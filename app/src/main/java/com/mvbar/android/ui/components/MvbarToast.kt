package com.mvbar.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvbar.android.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ToastIcon { QUEUE, SUCCESS, ERROR, FAVORITE, PLAYLIST }

data class ToastItem(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val icon: ToastIcon = ToastIcon.SUCCESS
)

object ToastManager {
    private val _events = MutableSharedFlow<ToastItem>(extraBufferCapacity = 5)
    val events = _events.asSharedFlow()

    fun show(message: String, icon: ToastIcon = ToastIcon.SUCCESS) {
        _events.tryEmit(ToastItem(message = message, icon = icon))
    }
}

@Composable
fun MvbarToastHost(modifier: Modifier = Modifier) {
    var currentToast by remember { mutableStateOf<ToastItem?>(null) }

    LaunchedEffect(Unit) {
        ToastManager.events.collect { toast ->
            currentToast = toast
            delay(2500)
            if (currentToast?.id == toast.id) currentToast = null
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = currentToast != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            currentToast?.let { toast ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val (icon, tint) = toastIconData(toast.icon)
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        toast.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun toastIconData(icon: ToastIcon): Pair<ImageVector, Color> = when (icon) {
    ToastIcon.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic to Cyan500
    ToastIcon.SUCCESS -> Icons.Filled.CheckCircle to Cyan500
    ToastIcon.ERROR -> Icons.Filled.Error to Pink500
    ToastIcon.FAVORITE -> Icons.Filled.Favorite to Pink500
    ToastIcon.PLAYLIST -> Icons.AutoMirrored.Filled.PlaylistAdd to Cyan500
}
