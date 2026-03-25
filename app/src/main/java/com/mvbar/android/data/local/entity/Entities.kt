package com.mvbar.android.data.local.entity

import androidx.room.*

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Int,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val displayArtistName: String?,
    val durationMs: Double?,
    val duration: Double?,
    val genre: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val bpm: Double?,
    val path: String?,
    val artPath: String?,
    val artHash: String?,
    val libraryId: Int?,
    val isFavorite: Boolean,
    val playCount: Int,
    val createdAt: String?
)

@Entity(tableName = "artists", indices = [Index(value = ["name"], unique = true)])
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val artistId: Int?,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val artPath: String?
)

@Entity(tableName = "albums", indices = [Index(value = ["displayName"], unique = true)])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val album: String?,
    val name: String?,
    val displayName: String,
    val artist: String?,
    val displayArtist: String?,
    val albumArtist: String?,
    val trackCount: Int,
    val year: Int?,
    val artPath: String?,
    val artHash: String?,
    val totalDiscs: Int?
)

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey val name: String,
    val trackCount: Int
)

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey val name: String,
    val trackCount: Int,
    val artistCount: Int
)

@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey val name: String,
    val trackCount: Int,
    val artistCount: Int
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val userId: Int,
    val createdAt: String?,
    val itemCount: Int
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("playlistId")]
)
data class PlaylistItemEntity(
    @PrimaryKey val id: Int,
    val playlistId: Int,
    val trackId: Int,
    val position: Int
)

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: Int
)

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val trackId: Int,
    val position: Int
)

@Entity(tableName = "rec_buckets")
data class RecBucketEntity(
    @PrimaryKey val key: String,
    val name: String,
    val subtitle: String?,
    val reason: String?,
    val count: Int,
    val tracksJson: String,
    val artPathsJson: String,
    val artHashesJson: String
)

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: Int,
    val feedUrl: String,
    val title: String,
    val author: String?,
    val description: String?,
    val imageUrl: String?,
    val imagePath: String?,
    val link: String?,
    val language: String?,
    val unplayedCount: Int,
    val createdAt: String?
)

@Entity(tableName = "episodes", indices = [Index("podcastId")])
data class EpisodeEntity(
    @PrimaryKey val id: Int,
    val podcastId: Int,
    val title: String,
    val description: String?,
    val audioUrl: String?,
    val durationMs: Long?,
    val imageUrl: String?,
    val imagePath: String?,
    val publishedAt: String?,
    val positionMs: Long,
    val played: Boolean,
    val downloaded: Boolean,
    val podcastTitle: String?,
    val podcastImageUrl: String?,
    val podcastImagePath: String?
)

@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val author: String?,
    val narrator: String?,
    val description: String?,
    val coverPath: String?,
    val durationMs: Long,
    val chapterCount: Int,
    val createdAt: String?
)

@Entity(
    tableName = "audiobook_chapters",
    indices = [Index("audiobookId")],
    foreignKeys = [ForeignKey(
        entity = AudiobookEntity::class,
        parentColumns = ["id"],
        childColumns = ["audiobookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AudiobookChapterEntity(
    @PrimaryKey val id: Int,
    val audiobookId: Int,
    val title: String,
    val position: Int,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val createdAt: String?
)

@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,  // PLAY, SKIP, ADD_FAVORITE, REMOVE_FAVORITE
    val trackId: Int,
    val payload: String? = null,  // JSON for extra data (e.g. skip percentage)
    val createdAt: Long = System.currentTimeMillis()
)
