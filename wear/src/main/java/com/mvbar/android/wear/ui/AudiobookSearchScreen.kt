package com.mvbar.android.wear.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text
import com.mvbar.android.wear.net.Audiobook
import com.mvbar.android.wear.net.AudiobookDetailResponse
import com.mvbar.android.wear.player.PlayableItem
import com.mvbar.android.wear.player.WearPlayerHolder

@Composable
internal fun AudiobookSearchScreen(backend: Backend, book: Audiobook, onBack: () -> Unit, onOpenNowPlaying: () -> Unit) {
    var detail by remember(book.id) { mutableStateOf<AudiobookDetailResponse?>(null) }
    var error by remember(book.id) { mutableStateOf(false) }
    var attempt by remember(book.id) { mutableIntStateOf(0) }
    LaunchedEffect(book.id, attempt) {
        error = false
        try { detail = backend.api.audiobook(book.id) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    androidx.activity.compose.BackHandler(onBack = onBack)
    WearList {
        item { WearHeaderChip(book.title, book.author ?: "Audiobook", onBack) }
        when {
            error -> item {
                Chip(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth(), label = { Text("Could not load chapters") }, secondaryLabel = { Text("Tap to retry") })
            }
            detail == null -> item { LoadingChip("Loading chapters") }
            detail!!.chapters.isEmpty() -> item { EmptyChip("No chapters", "This audiobook has no playable chapters") }
            else -> {
                val chapters = detail!!.chapters.sortedBy { it.position }
                items(chapters, key = { it.id }) { chapter ->
                    Chip(
                        onClick = {
                            WearPlayerHolder.playQueue(backend.context, chapters.map { PlayableItem.BookChapter(detail!!.audiobook ?: book, it) }, chapters.indexOf(chapter))
                            onOpenNowPlaying()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    }
}
