package com.mvbar.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class GoogleTokenRequest(val idToken: String)

@Serializable
data class GoogleAuthEnabledResponse(val enabled: Boolean = false, val clientId: String? = null)

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
    val hasDuration: Boolean get() = durationMs != null || duration != null
    val durationSeconds: Int get() {
        val ms = durationMs ?: (duration?.let { it * 1000 })
        return ((ms ?: 0.0) / 1000).toInt()
    }
    val durationFormatted: String get() {
        if (!hasDuration) return ""
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
    @SerialName("genre") val name: String = "",
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
    val ok: Boolean = false,
    val hits: List<Track> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val albums: List<SearchAlbum> = emptyList(),
    val playlists: List<SearchPlaylist> = emptyList()
)

@Serializable
data class SearchArtist(
    val id: Int = 0,
    val name: String = "",
    @SerialName("art_path") val artPath: String? = null,
    @SerialName("art_hash") val artHash: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("album_count") val albumCount: Int = 0
)

@Serializable
data class SearchAlbum(
    val album: String = "",
    @SerialName("display_artist") val displayArtist: String? = null,
    @SerialName("artist_id") val artistId: Int? = null,
    @SerialName("art_track_id") val artTrackId: Int? = null,
    @SerialName("art_path") val artPath: String? = null,
    @SerialName("art_hash") val artHash: String? = null,
    @SerialName("track_count") val trackCount: Int = 0
)

@Serializable
data class SearchPlaylist(
    val id: Int = 0,
    val name: String = "",
    val kind: String? = null
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
data class TracksListWrapper(val ok: Boolean = false, val artists: List<Artist> = emptyList(), val total: Int = 0)
@Serializable
data class AlbumsListWrapper(val ok: Boolean = false, val albums: List<Album> = emptyList(), val total: Int = 0)
@Serializable
data class GenresListWrapper(val ok: Boolean = false, val genres: List<Genre> = emptyList(), val total: Int = 0)
@Serializable
data class AlbumDetailResponse(val ok: Boolean = false, val album: Album? = null, val tracks: List<Track> = emptyList())

@Serializable
data class ArtistDetailResponse(
    val ok: Boolean = false,
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    @SerialName("appearsOn") val appearsOn: List<Album> = emptyList()
)

@Serializable
data class PlaylistItemsResponse(
    val ok: Boolean = false,
    val items: List<PlaylistItem> = emptyList()
)

@Serializable
data class CreatePlaylistResponse(
    val ok: Boolean = false,
    val playlist: Playlist? = null
)

@Serializable
data class SmartPlaylist(
    val id: Int = 0,
    val name: String = "",
    val sort: String = "random",
    val filters: SmartPlaylistFilters = SmartPlaylistFilters(),
    val created: String? = null,
    val updated: String? = null,
    val type: String = "smart"
)

@Serializable
data class SmartPlaylistFilters(
    val include: SmartFilterSet = SmartFilterSet(),
    val exclude: SmartFilterSet = SmartFilterSet(),
    val duration: SmartDuration? = null,
    val favoriteOnly: Boolean = false,
    val maxResults: Int = 500
)

@Serializable
data class SmartFilterSet(
    val artists: List<Int> = emptyList(),
    val artistsMode: String = "any",
    val albums: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val genresMode: String = "any",
    val years: List<Int> = emptyList(),
    val countries: List<String> = emptyList()
)

@Serializable
data class SmartDuration(val min: Int? = null, val max: Int? = null)

@Serializable
data class SmartPlaylistsResponse(
    val ok: Boolean = false,
    val items: List<SmartPlaylist> = emptyList()
)

@Serializable
data class SmartPlaylistResponse(
    val ok: Boolean = false,
    val id: Int = 0,
    val name: String = "",
    val sort: String = "random",
    val filters: SmartPlaylistFilters = SmartPlaylistFilters(),
    val created: String? = null,
    val updated: String? = null,
    val type: String = "smart",
    val trackCount: Int = 0,
    val duration: Int = 0,
    val truncated: Boolean = false,
    val tracks: List<Track> = emptyList()
)

@Serializable
data class SmartPlaylistCreateRequest(
    val name: String,
    val sort: String = "random",
    val filters: SmartPlaylistFilters = SmartPlaylistFilters()
)

data class LyricLine(val timeMs: Long, val text: String)

@Serializable
data class SuggestItem(val id: Int? = null, val name: String? = null)

@Serializable
data class SuggestResponse(val items: List<kotlinx.serialization.json.JsonElement> = emptyList())

// ============================================================================
// PODCAST MODELS
// ============================================================================

@Serializable
data class Podcast(
    val id: Int = 0,
    @SerialName("feed_url") val feedUrl: String = "",
    val title: String = "",
    val author: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    val link: String? = null,
    val language: String? = null,
    @SerialName("unplayed_count") val unplayedCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Episode(
    val id: Int = 0,
    @SerialName("podcast_id") val podcastId: Int = 0,
    val title: String = "",
    val description: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("position_ms") val positionMs: Long = 0,
    val played: Boolean = false,
    val downloaded: Boolean = false,
    @SerialName("podcast_title") val podcastTitle: String? = null,
    @SerialName("podcast_image_url") val podcastImageUrl: String? = null,
    @SerialName("podcast_image_path") val podcastImagePath: String? = null
) {
    val durationFormatted: String get() {
        val ms = durationMs ?: return ""
        val totalSeconds = (ms / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
    }

    val progressPercent: Int get() {
        val dur = durationMs ?: return 0
        if (dur <= 0) return 0
        return ((positionMs.toDouble() / dur) * 100).toInt().coerceIn(0, 100)
    }

    val publishedFormatted: String get() {
        val dateStr = publishedAt ?: return ""
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            val date = sdf.parse(dateStr) ?: return dateStr.take(10)
            val now = System.currentTimeMillis()
            val diffDays = ((now - date.time) / (1000 * 60 * 60 * 24)).toInt()
            when {
                diffDays == 0 -> "Today"
                diffDays == 1 -> "Yesterday"
                diffDays < 7 -> "$diffDays days ago"
                diffDays < 30 -> "${diffDays / 7} weeks ago"
                else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(date)
            }
        } catch (_: Exception) { dateStr.take(10) }
    }
}

@Serializable
data class PodcastSearchResult(
    val id: Int = 0,
    val title: String = "",
    val author: String = "",
    val imageUrl: String? = null,
    val feedUrl: String? = null,
    val genre: String? = null,
    val episodeCount: Int? = null
)

@Serializable
data class PodcastsResponse(val ok: Boolean = false, val podcasts: List<Podcast> = emptyList())

@Serializable
data class PodcastDetailResponse(val ok: Boolean = false, val podcast: Podcast? = null, val episodes: List<Episode> = emptyList())

@Serializable
data class PodcastSearchResponse(val ok: Boolean = false, val results: List<PodcastSearchResult> = emptyList())

@Serializable
data class PodcastSubscribeRequest(val feedUrl: String)

@Serializable
data class PodcastSubscribeResponse(val ok: Boolean = false, val podcast: Podcast? = null)

@Serializable
data class PodcastNewEpisodesResponse(val ok: Boolean = false, val episodes: List<Episode> = emptyList())

@Serializable
data class PodcastRefreshResponse(val ok: Boolean = false, val newEpisodes: Int = 0)

@Serializable
data class EpisodeProgressRequest(val positionMs: Long? = null, val played: Boolean? = null)

@Serializable
data class EpisodePlayedRequest(val played: Boolean)

