package com.mvbar.android.tv.data

import com.mvbar.android.shared.formatArtistDisplay
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
data class CurrentUserResponse(
    val ok: Boolean = false,
    val user: User? = null
)

@Serializable
data class User(
    val id: String = "",
    val email: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null
)

@Serializable
data class ArtistCredit(
    val id: Int? = null,
    val name: String = ""
)

@Serializable
data class Track(
    val id: Int = 0,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    @SerialName("display_artist") val displayArtistName: String? = null,
    val artists: List<ArtistCredit> = emptyList(),
    @SerialName("duration_ms") val durationMs: Double? = null,
    val duration: Double? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    @SerialName("art_path") val artPath: String? = null,
    @SerialName("art_hash") val artHash: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "Untitled"
    val displayArtist: String get() = formatArtistDisplay(
        artists.map { it.name }.takeIf { it.isNotEmpty() }?.joinToString("; "),
        displayArtistName,
        artist,
        albumArtist
    ) ?: "Unknown Artist"
    val displayAlbum: String get() = album?.takeIf { it.isNotBlank() } ?: "Unknown Album"
}

@Serializable
data class Album(
    val album: String? = null,
    val name: String? = null,
    val artist: String? = null,
    @SerialName("display_artist") val displayArtist: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    val year: Int? = null,
    @SerialName("art_path") val artPath: String? = null
) {
    val displayName: String get() = album ?: name ?: "Unknown Album"
    val artistName: String get() = formatArtistDisplay(displayArtist, albumArtist, artist) ?: "Unknown Artist"
}

@Serializable
data class TracksResponse(val ok: Boolean = false, val tracks: List<Track> = emptyList())

@Serializable
data class AlbumsResponse(
    val ok: Boolean = false,
    val albums: List<Album> = emptyList(),
    val total: Int = 0
)

@Serializable
data class AlbumDetailResponse(
    val ok: Boolean = false,
    val album: Album? = null,
    val tracks: List<Track> = emptyList()
)

@Serializable
data class FavoritesResponse(val ok: Boolean = false, val tracks: List<Track> = emptyList())

@Serializable
data class BasicResponse(val ok: Boolean = false)

@Serializable
data class RecommendationBucket(
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
data class RecommendationsResponse(
    val ok: Boolean = false,
    val buckets: List<RecommendationBucket> = emptyList()
)

@Serializable
data class PlaylistOwner(
    val id: String = "",
    val email: String = ""
)

@Serializable
data class Playlist(
    val id: Int = 0,
    val name: String = "",
    @SerialName("item_count") val itemCount: Int = 0,
    val owner: PlaylistOwner? = null,
    @SerialName("is_owner") val isOwner: Boolean = true,
    @SerialName("is_collaborative") val isCollaborative: Boolean = false,
    @SerialName("collaborator_count") val collaboratorCount: Int = 0
)

@Serializable
data class PlaylistItem(
    val id: Int = 0,
    @SerialName("track_id") val trackId: Int = 0,
    val position: Int = 0,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Double? = null,
    @SerialName("art_path") val artPath: String? = null,
    val track: Track? = null
) {
    fun toTrack(): Track? = track ?: if (trackId > 0 || !title.isNullOrBlank()) {
        Track(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artPath = artPath
        )
    } else {
        null
    }
}

@Serializable
data class PlaylistsResponse(val ok: Boolean = false, val playlists: List<Playlist> = emptyList())

@Serializable
data class PlaylistNameRequest(val name: String)

@Serializable
data class CreatePlaylistResponse(val ok: Boolean = false, val playlist: Playlist? = null)

@Serializable
data class PlaylistTrackRequest(@SerialName("trackId") val trackId: Int)

@Serializable
data class PlaylistItemsResponse(val ok: Boolean = false, val items: List<PlaylistItem> = emptyList())

@Serializable
data class SmartPlaylist(
    val id: Int = 0,
    val name: String = "",
    val type: String = "smart"
)

@Serializable
data class SmartPlaylistsResponse(val ok: Boolean = false, val items: List<SmartPlaylist> = emptyList())

@Serializable
data class SmartPlaylistResponse(
    val id: Int = 0,
    val name: String = "",
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)

@Serializable
data class SocialUser(
    val id: String = "",
    val email: String = "",
    @SerialName("avatarPath") val avatarPath: String? = null,
    @SerialName("canAccess") val canAccess: Boolean = true
)

@Serializable
data class ShareTargetsResponse(
    val ok: Boolean = false,
    val friends: List<SocialUser> = emptyList()
)

@Serializable
data class ShareTrackRequest(
    @SerialName("trackId") val trackId: Int,
    @SerialName("recipientIds") val recipientIds: List<String>,
    val message: String? = null
)

@Serializable
data class ShareTrackResponse(val ok: Boolean = false, val shared: Int = 0)

@Serializable
data class SimilarTracksResponse(
    val ok: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val message: String? = null
)

@Serializable
data class PlaylistCollaborator(
    val user: SocialUser = SocialUser(),
    @SerialName("addedAt") val addedAt: String? = null
)

@Serializable
data class PlaylistCollaborationResponse(
    val ok: Boolean = false,
    val owner: SocialUser = SocialUser(),
    @SerialName("isOwner") val isOwner: Boolean = false,
    val collaborators: List<PlaylistCollaborator> = emptyList(),
    @SerialName("eligibleFriends") val eligibleFriends: List<SocialUser> = emptyList()
)

data class TvPlaylist(
    val id: Int,
    val name: String,
    val itemCount: Int,
    val kind: Kind,
    val ownerEmail: String? = null,
    val collaborative: Boolean = false,
    val isOwner: Boolean = true,
    val collaboratorCount: Int = 0
) {
    enum class Kind { STANDARD, SMART }
}

@Serializable
data class Artist(
    val id: Int = 0,
    val name: String = "",
    @SerialName("art_path") val artPath: String? = null,
    @SerialName("art_hash") val artHash: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("album_count") val albumCount: Int = 0
)

@Serializable
data class ArtistsResponse(
    val ok: Boolean = false,
    val artists: List<Artist> = emptyList(),
    val total: Int = 0
)

@Serializable
data class ArtistDetailResponse(
    val ok: Boolean = false,
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    @SerialName("appearsOn") val appearsOn: List<Album> = emptyList()
)

@Serializable
data class ArtistTracksResponse(val ok: Boolean = false, val tracks: List<Track> = emptyList())

@Serializable
data class Podcast(
    val id: Int = 0,
    val title: String = "",
    val author: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("unplayed_count") val unplayedCount: Int = 0
)

@Serializable
data class Episode(
    val id: Int = 0,
    @SerialName("podcast_id") val podcastId: Int = 0,
    val title: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("position_ms") val positionMs: Long = 0,
    val played: Boolean = false,
    @SerialName("podcast_title") val podcastTitle: String? = null,
    @SerialName("podcast_image_path") val podcastImagePath: String? = null
)

@Serializable
data class PodcastsResponse(val ok: Boolean = false, val podcasts: List<Podcast> = emptyList())

@Serializable
data class PodcastDetailResponse(
    val ok: Boolean = false,
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList()
)

@Serializable
data class PodcastNewEpisodesResponse(val ok: Boolean = false, val episodes: List<Episode> = emptyList())

@Serializable
data class EpisodeProgressRequest(
    @SerialName("position_ms") val positionMs: Long,
    val played: Boolean? = null
)

@Serializable
data class AudiobookProgress(
    @SerialName("chapter_id") val chapterId: Int = 0,
    @SerialName("position_ms") val positionMs: Long = 0,
    val finished: Boolean = false
)

@Serializable
data class Audiobook(
    val id: Int = 0,
    val title: String = "",
    val author: String? = null,
    val narrator: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("chapter_count") val chapterCount: Int = 0,
    val progress: AudiobookProgress? = null
)

@Serializable
data class AudiobookChapter(
    val id: Int = 0,
    @SerialName("audiobook_id") val audiobookId: Int = 0,
    val title: String = "",
    val position: Int = 0,
    @SerialName("duration_ms") val durationMs: Long? = null
)

@Serializable
data class AudiobookDetailResponse(
    val audiobook: Audiobook? = null,
    val chapters: List<AudiobookChapter> = emptyList(),
    val progress: AudiobookProgress? = null
)

@Serializable
data class AudiobookProgressRequest(
    @SerialName("chapter_id") val chapterId: Int,
    @SerialName("position_ms") val positionMs: Long,
    val finished: Boolean? = null
)

@Serializable
data class SearchPlaylist(val id: Int = 0, val name: String = "", val kind: String? = null)

@Serializable
data class SearchResponse(
    val ok: Boolean = false,
    val hits: List<Track> = emptyList(),
    val playlists: List<SearchPlaylist> = emptyList(),
    val podcasts: List<Podcast> = emptyList(),
    @SerialName("podcastEpisodes") val podcastEpisodesCamel: List<Episode> = emptyList(),
    @SerialName("podcast_episodes") val podcastEpisodesSnake: List<Episode> = emptyList()
) {
    val podcastEpisodes: List<Episode>
        get() = (podcastEpisodesCamel + podcastEpisodesSnake).distinctBy(Episode::id)
}

data class TvSession(
    val serverUrl: String,
    val token: String,
    val email: String
)
