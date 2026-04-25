package com.mvbar.android.wear.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: Int = 0,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    @SerialName("display_artist") val displayArtistName: String? = null,
    @SerialName("duration_ms") val durationMs: Double? = null,
    val duration: Double? = null,
    @SerialName("art_path") val artPath: String? = null,
    @SerialName("art_hash") val artHash: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false
) {
    val displayTitle: String get() = title ?: "Untitled"
    val displayArtist: String get() = displayArtistName ?: artist ?: "Unknown Artist"
}

@Serializable
data class Album(
    val id: Int = 0,
    val title: String = "",
    val artist: String? = null,
    @SerialName("art_path") val artPath: String? = null
)

@Serializable
data class Playlist(
    val id: Int = 0,
    val name: String = "",
    @SerialName("track_count") val trackCount: Int = 0
)

@Serializable
data class Podcast(
    val id: Int = 0,
    val title: String = "",
    val author: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("unplayed_count") val unplayedCount: Int = 0
)

@Serializable
data class Episode(
    val id: Int = 0,
    @SerialName("podcast_id") val podcastId: Int = 0,
    val title: String = "",
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("position_ms") val positionMs: Long = 0,
    val played: Boolean = false,
    @SerialName("podcast_title") val podcastTitle: String? = null,
    @SerialName("podcast_image_path") val podcastImagePath: String? = null
) {
    val durationFormatted: String get() {
        val ms = durationMs ?: return ""
        val totalSeconds = (ms / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
    }
}

@Serializable
data class TracksResponse(val ok: Boolean = false, val tracks: List<Track> = emptyList())

@Serializable
data class PlaylistsResponse(val ok: Boolean = false, val playlists: List<Playlist> = emptyList())

@Serializable
data class PodcastsResponse(val ok: Boolean = false, val podcasts: List<Podcast> = emptyList())

@Serializable
data class PodcastDetailResponse(
    val ok: Boolean = false,
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList()
)

@Serializable
data class PodcastNewEpisodesResponse(
    val ok: Boolean = false,
    val episodes: List<Episode> = emptyList()
)

@Serializable
data class SearchResults(
    val ok: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)
