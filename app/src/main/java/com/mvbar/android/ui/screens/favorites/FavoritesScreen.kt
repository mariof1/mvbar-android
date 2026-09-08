package com.mvbar.android.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.model.Track
import com.mvbar.android.ui.components.ErrorMessage
import com.mvbar.android.ui.components.TrackListItem
import com.mvbar.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<Track>,
    currentTrackId: Int?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onRefresh: () -> Unit,
    onReorder: (Int, Int?) -> Unit,
    isReordering: Boolean = false,
    isLoading: Boolean = false,
    error: String? = null,
    onTrackLongPress: ((Track) -> Unit)? = null
) {
    LaunchedEffect(Unit) { onRefresh() }

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    var ordered by remember(favorites) { mutableStateOf(favorites) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val busy by rememberUpdatedState(isReordering || isLoading)
    val currentFavorites by rememberUpdatedState(favorites)
    val submit by rememberUpdatedState(onReorder)
    fun finishDrag(cancel: Boolean) {
        val id = draggingId ?: return
        draggingId = null
        if (cancel) ordered = currentFavorites
        else {
            val next = ordered
            val index = next.indexOfFirst { it.id == id }
            if (index >= 0 && next.map { it.id } != currentFavorites.map { it.id }) {
                submit(id, next.getOrNull(index + 1)?.id)
            }
            // The view model owns saved state and rollback, including offline failures.
            ordered = currentFavorites
        }
    }
    fun moveOverPointer() {
        val id = draggingId ?: return
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            dragY >= it.offset && dragY < it.offset + it.size
        } ?: return
        if (target.key == id) return
        val from = ordered.indexOfFirst { it.id == id }
        val to = ordered.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        // Cross the destination's midpoint before moving, preventing boundary jitter.
        val midpoint = target.offset + target.size / 2f
        if ((from < to && dragY < midpoint) || (from > to && dragY > midpoint)) return
        ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
    }
    val movePointer by rememberUpdatedState({ moveOverPointer() })
    val endDrag by rememberUpdatedState({ cancel: Boolean -> finishDrag(cancel) })
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            val info = listState.layoutInfo
            val edge = 100f
            val delta = when {
                dragY < info.viewportStartOffset + edge -> -18f
                dragY > info.viewportEndOffset - edge -> 18f
                else -> 0f
            }
            if (delta != 0f) listState.scrollBy(delta)
            movePointer()
            delay(16)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { if (draggingId == null && !busy) onRefresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                error != null -> {
                    ErrorMessage(
                        message = error,
                        onRetry = onRefresh,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                isLoading && favorites.isEmpty() -> {
                    CircularProgressIndicator(
                        color = Cyan500,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                favorites.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No favorites yet", color = OnSurfaceDim)
                    }
                }
                else -> {
                    Column {
                    Text(if (isReordering) "Saving order…" else "Hold a track or grip and drag to reorder",
                        color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(ordered, key = { it.id }) { track ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (draggingId == track.id) Cyan500.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .pointerInput(track.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                if (!busy) {
                                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == track.id }
                                                    if (item != null) { draggingId = track.id; dragY = item.offset + offset.y }
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                if (draggingId == track.id) {
                                                    change.consume()
                                                    // A moved row changes local pointer coordinates. Re-anchor to
                                                    // the list rather than accumulating the row's movement too.
                                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == track.id }
                                                    if (item != null) dragY = item.offset + change.position.y
                                                    movePointer()
                                                }
                                            },
                                            onDragEnd = { endDrag(false) },
                                            onDragCancel = { endDrag(true) }
                                        )
                                    }
                            ) {
                            Icon(Icons.Default.DragHandle, contentDescription = null,
                                tint = OnSurfaceDim, modifier = Modifier.size(48.dp).padding(12.dp).semantics {
                                    contentDescription = "Reorder ${track.title}"
                                    customActions = listOf(-1 to "Move up", 1 to "Move down").map { (direction, label) ->
                                        CustomAccessibilityAction(label) {
                                            val index = ordered.indexOfFirst { it.id == track.id }
                                            val target = index + direction
                                            if (!busy && index >= 0 && target in ordered.indices) {
                                                val next = ordered.toMutableList().apply { add(target, removeAt(index)) }
                                                submit(track.id, next.getOrNull(target + 1)?.id)
                                                true
                                            } else false
                                        }
                                    }
                                })
                            TrackListItem(
                                track = track.copy(isFavorite = true),
                                isPlaying = track.id == currentTrackId,
                                onPlay = { if (draggingId == null) onPlayTrack(track, ordered) },
                                onFavorite = { if (!busy && draggingId == null) onToggleFavorite(track.id) },
                                onMore = onTrackLongPress?.let { { it(track) } },
                                modifier = Modifier.weight(1f).padding(end = 12.dp)
                            )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
