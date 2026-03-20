package com.mvbar.android.data.local.entity

import com.mvbar.android.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

// Track ↔ TrackEntity
fun Track.toEntity() = TrackEntity(
    id = id, title = title, artist = artist, album = album,
    albumArtist = albumArtist, displayArtistName = displayArtistName,
    durationMs = durationMs, duration = duration, genre = genre,
    trackNumber = trackNumber, discNumber = discNumber, year = year,
    bpm = bpm, path = path, artPath = artPath, artHash = artHash,
    libraryId = libraryId, isFavorite = isFavorite, playCount = playCount,
    createdAt = createdAt
)

fun TrackEntity.toModel() = Track(
    id = id, title = title, artist = artist, album = album,
    albumArtist = albumArtist, displayArtistName = displayArtistName,
    durationMs = durationMs, duration = duration, genre = genre,
    trackNumber = trackNumber, discNumber = discNumber, year = year,
    bpm = bpm, path = path, artPath = artPath, artHash = artHash,
    libraryId = libraryId, isFavorite = isFavorite, playCount = playCount,
    createdAt = createdAt
)

// Artist ↔ ArtistEntity
fun Artist.toEntity() = ArtistEntity(
    artistId = id, name = name, trackCount = trackCount,
    albumCount = albumCount, artPath = artPath
)

fun ArtistEntity.toModel() = Artist(
    id = artistId, name = name, trackCount = trackCount,
    albumCount = albumCount, artPath = artPath
)

// Album ↔ AlbumEntity
fun Album.toEntity() = AlbumEntity(
    album = album, name = name, displayName = displayName,
    artist = artist, displayArtist = displayArtist,
    albumArtist = albumArtist, trackCount = trackCount,
    year = year, artPath = artPath, artHash = artHash,
    totalDiscs = totalDiscs
)

fun AlbumEntity.toModel() = Album(
    album = album, name = name, artist = artist,
    displayArtist = displayArtist, albumArtist = albumArtist,
    trackCount = trackCount, year = year, artPath = artPath,
    artHash = artHash, totalDiscs = totalDiscs
)

// Genre ↔ GenreEntity
fun Genre.toEntity() = GenreEntity(name = name, trackCount = trackCount)
fun GenreEntity.toModel() = Genre(name = name, trackCount = trackCount)

// Country ↔ CountryEntity
fun Country.toEntity() = CountryEntity(name = name, trackCount = trackCount, artistCount = artistCount)
fun CountryEntity.toModel() = Country(name = name, trackCount = trackCount, artistCount = artistCount)

// Language ↔ LanguageEntity
fun Language.toEntity() = LanguageEntity(name = name, trackCount = trackCount, artistCount = artistCount)
fun LanguageEntity.toModel() = Language(name = name, trackCount = trackCount, artistCount = artistCount)

// Playlist ↔ PlaylistEntity
fun Playlist.toEntity() = PlaylistEntity(
    id = id, name = name, userId = userId,
    createdAt = createdAt, itemCount = itemCount
)

fun PlaylistEntity.toModel(items: List<PlaylistItem>? = null) = Playlist(
    id = id, name = name, userId = userId,
    createdAt = createdAt, itemCount = itemCount, items = items
)

// PlaylistItem ↔ PlaylistItemEntity
fun PlaylistItem.toEntity(playlistId: Int) = PlaylistItemEntity(
    id = id, playlistId = playlistId, trackId = trackId, position = position
)

fun PlaylistItemEntity.toModel(track: Track? = null) = PlaylistItem(
    id = id, trackId = trackId, position = position, track = track
)

// RecBucket ↔ RecBucketEntity
fun RecBucket.toEntity() = RecBucketEntity(
    key = key, name = name, subtitle = subtitle, reason = reason, count = count,
    tracksJson = json.encodeToString(tracks),
    artPathsJson = json.encodeToString(artPaths),
    artHashesJson = json.encodeToString(artHashes)
)

fun RecBucketEntity.toModel() = RecBucket(
    key = key, name = name, subtitle = subtitle, reason = reason, count = count,
    tracks = try { json.decodeFromString(tracksJson) } catch (_: Exception) { emptyList() },
    artPaths = try { json.decodeFromString(artPathsJson) } catch (_: Exception) { emptyList() },
    artHashes = try { json.decodeFromString(artHashesJson) } catch (_: Exception) { emptyList() }
)

// Podcast ↔ PodcastEntity
fun Podcast.toEntity() = PodcastEntity(
    id = id, feedUrl = feedUrl, title = title, author = author,
    description = description, imageUrl = imageUrl, imagePath = imagePath,
    link = link, language = language, unplayedCount = unplayedCount,
    createdAt = createdAt
)

fun PodcastEntity.toModel() = Podcast(
    id = id, feedUrl = feedUrl, title = title, author = author,
    description = description, imageUrl = imageUrl, imagePath = imagePath,
    link = link, language = language, unplayedCount = unplayedCount,
    createdAt = createdAt
)

// Episode ↔ EpisodeEntity
fun Episode.toEntity() = EpisodeEntity(
    id = id, podcastId = podcastId, title = title,
    description = description, audioUrl = audioUrl, durationMs = durationMs,
    imageUrl = imageUrl, imagePath = imagePath, publishedAt = publishedAt,
    positionMs = positionMs, played = played, downloaded = downloaded,
    podcastTitle = podcastTitle, podcastImageUrl = podcastImageUrl,
    podcastImagePath = podcastImagePath
)

fun EpisodeEntity.toModel() = Episode(
    id = id, podcastId = podcastId, title = title,
    description = description, audioUrl = audioUrl, durationMs = durationMs,
    imageUrl = imageUrl, imagePath = imagePath, publishedAt = publishedAt,
    positionMs = positionMs, played = played, downloaded = downloaded,
    podcastTitle = podcastTitle, podcastImageUrl = podcastImageUrl,
    podcastImagePath = podcastImagePath
)
