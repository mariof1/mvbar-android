package com.mvbar.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val token: String = "",
    val user: User? = null
)

@Serializable
data class User(
    val id: String = "",
    val email: String = "",
    val role: String = "user",
    @SerialName("avatar_path") val avatarPath: String? = null
)

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
    val genre: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    val year: Int? = null,
    val bpm: Double? = null,
    val path: String? = null,
    @SerialName("art_path") val artPath: String? = null,
    @SerialName("art_hash") val artHash: String? = null,
    @SerialName("library_id") val libraryId: Int? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
) {
    val displayTitle: String get() = title ?: "Untitled"
    val displayArtist: String get() = displayArtistName ?: artist ?: "Unknown Artist"
    val displayAlbum: String get() = album ?: "Unknown Album"
    val durationSeconds: Int get() {
        val ms = durationMs ?: (duration?.let { it * 1000 })
        return ((ms ?: 0.0) / 1000).toInt()
    }
    val durationFormatted: String get() {
        val secs = durationSeconds
        return "${secs / 60}:%02d".format(secs % 60)
    }
}

@Serializable
data class Artist(
    val id: Int? = null,
    val name: String = "",
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("album_count") val albumCount: Int = 0,
    @SerialName("art_path") val artPath: String? = null
)

@Serializable
data class Album(
    val album: String? = null,
    val name: String? = null,
    val artist: String? = null,
    @SerialName("display_artist") val displayArtist: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    val year: Int? = null,
    @SerialName("art_path") val artPath: String? = null,
    @SerialName("art_hash") val artHash: String? = null,
    @SerialName("total_discs") val totalDiscs: Int? = null
) {
    /** Album name - handles both 'album' (list) and 'name' (detail) API fields */
    val displayName: String get() = album ?: name ?: ""
}

@Serializable
data class Genre(
    val name: String = "",
    @SerialName("track_count") val trackCount: Int = 0
)

@Serializable
data class Playlist(
    val id: Int = 0,
    val name: String = "",
    @SerialName("user_id") val userId: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
    val items: List<PlaylistItem>? = null
)

@Serializable
data class PlaylistItem(
    val id: Int = 0,
    @SerialName("track_id") val trackId: Int = 0,
    val position: Int = 0,
    val track: Track? = null
)

@Serializable
data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList()
)

@Serializable
data class HistoryEntry(
    val id: Int = 0,
    @SerialName("track_id") val trackId: Int = 0,
    @SerialName("played_at") val playedAt: String? = null,
    val track: Track? = null
)

@Serializable
data class TracksResponse(val ok: Boolean = false, val tracks: List<Track> = emptyList())
@Serializable
data class PlaylistsResponse(val ok: Boolean = false, val playlists: List<Playlist> = emptyList())
@Serializable
data class FavoritesResponse(val ok: Boolean = false, val tracks: List<Track> = emptyList())
@Serializable
data class HistoryResponse(val ok: Boolean = false, val tracks: List<Track> = emptyList())
@Serializable
data class RecommendationsResponse(val ok: Boolean = false, val buckets: List<RecBucket> = emptyList())
@Serializable
data class RecBucket(
    val key: String = "",
    val name: String = "",
    val subtitle: String? = null,
    val reason: String? = null,
    val count: Int = 0,
    val tracks: List<Track> = emptyList(),
    @SerialName("art_paths") val artPaths: List<String> = emptyList(),
    @SerialName("art_hashes") val artHashes: List<String> = emptyList()
)
@Serializable
data class TracksListWrapper(val ok: Boolean = false, val artists: List<Artist> = emptyList())
@Serializable
data class AlbumsListWrapper(val ok: Boolean = false, val albums: List<Album> = emptyList())
@Serializable
data class GenresListWrapper(val ok: Boolean = false, val genres: List<Genre> = emptyList())
@Serializable
data class AlbumDetailResponse(val ok: Boolean = false, val album: Album? = null, val tracks: List<Track> = emptyList())
