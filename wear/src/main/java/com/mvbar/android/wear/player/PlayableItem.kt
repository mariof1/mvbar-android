package com.mvbar.android.wear.player

import com.mvbar.android.wear.net.Episode
import com.mvbar.android.wear.net.Track

sealed interface PlayableItem {
    val id: Int
    val title: String
    val subtitle: String
    val artUrl: String?
    val durationMs: Long?
    val isPodcast: Boolean

    data class BookChapter(
        val book: com.mvbar.android.wear.net.Audiobook,
        val chapter: com.mvbar.android.wear.net.AudiobookChapter
    ) : PlayableItem {
        override val id get() = chapter.id
        override val title get() = chapter.title
        override val subtitle get() = book.title
        override val artUrl get() = "/api/audiobook-art/${book.id}"
        override val durationMs get() = chapter.durationMs
        override val isPodcast get() = false
    }

    val mediaKey: String get() = when (this) {
        is Music -> "tr-$id"
        is PodcastEp -> "ep-$id"
        is BookChapter -> "ab-${book.id}-$id"
    }

    data class Music(val track: Track) : PlayableItem {
        override val id get() = track.id
        override val title get() = track.displayTitle
        override val subtitle get() = track.displayArtist
        override val artUrl get() = track.artPath
        override val durationMs get() = track.durationMs?.toLong()
        override val isPodcast get() = false
    }

    data class PodcastEp(val episode: Episode) : PlayableItem {
        override val id get() = episode.id
        override val title get() = episode.title
        override val subtitle get() = episode.podcastTitle ?: ""
        override val artUrl get() = episode.imagePath ?: episode.podcastImagePath
        override val durationMs get() = episode.durationMs
        override val isPodcast get() = true
    }
}
