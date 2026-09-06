package com.mvbar.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object RecentSearchType {
    const val AUDIOBOOK = "audiobook"
    const val TRACK = "track"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val PLAYLIST = "playlist"
    const val PODCAST = "podcast"
    const val PODCAST_EPISODE = "podcast_episode"
}

@Serializable
data class RecentSearchPayload(
    val id: Int? = null,
    val title: String? = null,
    val artist: String? = null,
    val artistId: Int? = null,
    val artistName: String? = null,
    val album: String? = null,
    val kind: String? = null,
    val podcastId: Int? = null,
    @SerialName("podcast_id") val episodePodcastId: Int? = null,
    val description: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("image_url") val episodeImageUrl: String? = null,
    @SerialName("image_path") val episodeImagePath: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("position_ms") val positionMs: Long = 0,
    val played: Boolean = false,
    @SerialName("podcast_title") val podcastTitle: String? = null,
    @SerialName("podcast_image_url") val podcastImageUrl: String? = null,
    @SerialName("podcast_image_path") val podcastImagePath: String? = null
)

@Serializable
data class RecentSearchRequest(
    val itemType: String,
    val itemKey: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val payload: RecentSearchPayload
) {
    fun optimisticItem() = RecentSearchItem(
        itemType = itemType,
        itemKey = itemKey,
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        payload = payload
    )
}

@Serializable
data class RecentSearchItem(
    val itemType: String = "",
    val itemKey: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val payload: RecentSearchPayload = RecentSearchPayload(),
    val accessedAt: String = ""
) {
    val stableKey: String get() = "$itemType:$itemKey"

    fun asRequest() = RecentSearchRequest(itemType, itemKey, title, subtitle, imageUrl, payload)

    fun asAudiobook(): Audiobook? {
        if (itemType != RecentSearchType.AUDIOBOOK) return null
        val id = payload.id?.takeIf { it > 0 } ?: return null
        return Audiobook(id = id, title = title)
    }

    fun asTrack(): Track? {
        if (itemType != RecentSearchType.TRACK) return null
        val trackId = payload.id?.takeIf { it > 0 } ?: return null
        return Track(id = trackId, title = payload.title ?: title, artist = payload.artist)
    }

    fun asArtist(): SearchArtist? {
        if (itemType != RecentSearchType.ARTIST) return null
        val id = payload.artistId?.takeIf { it > 0 } ?: return null
        return SearchArtist(id = id, name = payload.artistName ?: title)
    }

    fun asAlbum(): SearchAlbum? {
        if (itemType != RecentSearchType.ALBUM) return null
        val album = payload.album?.takeIf { it.isNotBlank() } ?: title.takeIf { it.isNotBlank() } ?: return null
        return SearchAlbum(album = album, displayArtist = payload.artist, artistId = payload.artistId)
    }

    fun asPlaylist(): SearchPlaylist? {
        if (itemType != RecentSearchType.PLAYLIST) return null
        val id = payload.id?.takeIf { it > 0 } ?: return null
        return SearchPlaylist(id = id, name = title, kind = payload.kind)
    }

    fun asPodcast(): Podcast? {
        if (itemType != RecentSearchType.PODCAST) return null
        val id = payload.podcastId?.takeIf { it > 0 } ?: return null
        return Podcast(id = id, title = title)
    }

    fun asEpisode(): Episode? {
        if (itemType != RecentSearchType.PODCAST_EPISODE) return null
        val id = payload.id?.takeIf { it > 0 } ?: return null
        val podcastId = payload.episodePodcastId?.takeIf { it > 0 } ?: return null
        return Episode(
            id = id,
            podcastId = podcastId,
            title = payload.title ?: title,
            description = payload.description,
            audioUrl = payload.audioUrl,
            durationMs = payload.durationMs,
            imageUrl = payload.episodeImageUrl,
            imagePath = payload.episodeImagePath,
            publishedAt = payload.publishedAt,
            positionMs = payload.positionMs,
            played = payload.played,
            podcastTitle = payload.podcastTitle,
            podcastImageUrl = payload.podcastImageUrl,
            podcastImagePath = payload.podcastImagePath
        )
    }
}

@Serializable
data class RecentSearchesResponse(
    val ok: Boolean = false,
    val searches: List<RecentSearchItem> = emptyList()
)

@Serializable
data class RecentSearchActionResponse(
    val ok: Boolean = false,
    val removed: Int = 0
)

object RecentSearchSelection {
    fun audiobook(book: Audiobook) = RecentSearchRequest(
        itemType = RecentSearchType.AUDIOBOOK,
        itemKey = book.id.toString(),
        title = book.title,
        subtitle = listOfNotNull("Audiobook", book.author?.takeIf { it.isNotBlank() }).joinToString(" · "),
        imageUrl = "/api/audiobook-art/${book.id}",
        payload = RecentSearchPayload(id = book.id)
    )

    fun track(track: Track) = RecentSearchRequest(
        itemType = RecentSearchType.TRACK,
        itemKey = track.id.toString(),
        title = track.title ?: track.path ?: "Untitled",
        subtitle = "${track.displayArtist} · Song",
        imageUrl = "/api/library/tracks/${track.id}/art",
        payload = RecentSearchPayload(id = track.id, title = track.title, artist = track.displayArtist)
    )

    fun artist(artist: SearchArtist) = RecentSearchRequest(
        itemType = RecentSearchType.ARTIST,
        itemKey = artist.id.toString(),
        title = artist.name,
        subtitle = "Artist",
        imageUrl = artist.artPath?.let { path ->
            "/api/art/$path" + (artist.artHash?.let { hash -> "?h=$hash" } ?: "")
        } ?: artist.artTrackId?.let { "/api/library/tracks/$it/art" },
        payload = RecentSearchPayload(artistId = artist.id, artistName = artist.name)
    )

    fun album(album: SearchAlbum): RecentSearchRequest {
        val artist = album.displayArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val ownerKey = album.artistId?.let(::JsonPrimitive) ?: JsonPrimitive(artist)
        val itemKey = JsonArray(listOf(ownerKey, JsonPrimitive(album.album))).toString()
        return RecentSearchRequest(
            itemType = RecentSearchType.ALBUM,
            itemKey = itemKey,
            title = album.album,
            subtitle = "$artist · Album",
            imageUrl = album.artTrackId?.let { "/api/library/tracks/$it/art" }
                ?: album.artPath?.let { path ->
                    "/api/art/$path" + (album.artHash?.let { hash -> "?h=$hash" } ?: "")
                },
            payload = RecentSearchPayload(album = album.album, artist = album.displayArtist, artistId = album.artistId)
        )
    }

    fun playlist(playlist: SearchPlaylist) = RecentSearchRequest(
        itemType = RecentSearchType.PLAYLIST,
        itemKey = "${playlist.kind ?: "playlist"}:${playlist.id}",
        title = playlist.name,
        subtitle = if (playlist.kind == "smart") "Smart playlist" else "Playlist",
        imageUrl = null,
        payload = RecentSearchPayload(id = playlist.id, kind = playlist.kind ?: "playlist")
    )

    fun podcast(podcast: Podcast) = RecentSearchRequest(
        itemType = RecentSearchType.PODCAST,
        itemKey = podcast.id.toString(),
        title = podcast.title,
        subtitle = listOfNotNull(podcast.author?.takeIf { it.isNotBlank() }, "Podcast").joinToString(" · "),
        imageUrl = "/api/podcasts/${podcast.id}/art",
        payload = RecentSearchPayload(podcastId = podcast.id)
    )

    fun episode(episode: Episode) = RecentSearchRequest(
        itemType = RecentSearchType.PODCAST_EPISODE,
        itemKey = episode.id.toString(),
        title = episode.title,
        subtitle = listOfNotNull(episode.podcastTitle?.takeIf { it.isNotBlank() }, "Podcast episode").joinToString(" · "),
        imageUrl = "/api/podcasts/episodes/${episode.id}/art",
        payload = RecentSearchPayload(
            id = episode.id,
            title = episode.title,
            episodePodcastId = episode.podcastId,
            description = null,
            audioUrl = episode.audioUrl,
            durationMs = episode.durationMs,
            episodeImageUrl = episode.imageUrl,
            episodeImagePath = episode.imagePath,
            publishedAt = episode.publishedAt,
            positionMs = episode.positionMs,
            played = episode.played,
            podcastTitle = episode.podcastTitle,
            podcastImageUrl = episode.podcastImageUrl,
            podcastImagePath = episode.podcastImagePath
        )
    )
}
