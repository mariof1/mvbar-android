package com.mvbar.android.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mvbar.android.MainActivity
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.repository.AuthRepository
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val ROOT_ID = "[root]"
        private const val RECENT_ID = "[recent]"
        private const val ALBUMS_ID = "[albums]"
        private const val ARTISTS_ID = "[artists]"
        private const val PLAYLISTS_ID = "[playlists]"
        private const val GENRES_ID = "[genres]"
        private const val FAVORITES_ID = "[favorites]"
        private const val PODCASTS_ID = "[podcasts]"
        private const val AUDIOBOOKS_ID = "[audiobooks]"
    }

    override fun onCreate() {
        super.onCreate()

        AudioCacheManager.init(this)

        // Ensure API is configured (critical for Android Auto which may start service without Activity)
        if (ApiClient.getBaseUrl() == "http://localhost/") {
            serviceScope.launch {
                try {
                    AuthRepository(this@PlaybackService).restoreSession()
                    DebugLog.i("Player", "Restored API session in PlaybackService")
                } catch (e: Exception) {
                    DebugLog.e("Player", "Failed to restore session in PlaybackService", e)
                }
            }
        }

        val okClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                ApiClient.getToken()?.let {
                    builder.addHeader("Authorization", "Bearer $it")
                }
                chain.proceed(builder.build())
            }
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okClient)
        val dataSourceFactory = AudioCacheManager.createCacheDataSourceFactory(upstreamFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.pauseAtEndOfMediaItems = false

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DebugLog.e("Player", "Playback error: ${error.errorCodeName}", error)
            }
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(AuthBitmapLoader())
            .build()

        DebugLog.i("Player", "PlaybackService created with Android Auto support")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setTitle("mvbar")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val items = when {
                        parentId == ROOT_ID -> getRootChildren()
                        parentId == RECENT_ID -> getRecentTracks()
                        parentId == FAVORITES_ID -> getFavoriteTracks()
                        parentId == ALBUMS_ID -> getAlbumsList()
                        parentId == ARTISTS_ID -> getArtistsList()
                        parentId == PLAYLISTS_ID -> getPlaylistsList()
                        parentId == GENRES_ID -> getGenresList()
                        parentId == PODCASTS_ID -> getPodcastsList()
                        parentId == AUDIOBOOKS_ID -> getAudiobooksList()
                        parentId.startsWith("album:") -> getAlbumTracks(parentId.removePrefix("album:"))
                        parentId.startsWith("artist:") -> getArtistTracks(parentId.removePrefix("artist:").toInt())
                        parentId.startsWith("playlist:") -> getPlaylistTracks(parentId.removePrefix("playlist:").toInt())
                        parentId.startsWith("smartpl:") -> getSmartPlaylistTracks(parentId.removePrefix("smartpl:").toInt())
                        parentId.startsWith("genre:") -> getGenreTracks(parentId.removePrefix("genre:"))
                        parentId.startsWith("podcast:") -> getPodcastEpisodes(parentId.removePrefix("podcast:").toInt())
                        parentId.startsWith("audiobook:") -> getAudiobookChapters(parentId.removePrefix("audiobook:").toInt())
                        else -> emptyList()
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    DebugLog.e("Auto", "Browse error for $parentId", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Determine if this is a browsable folder or a playable track
            val isBrowsable = mediaId.startsWith("album:") || mediaId.startsWith("artist:") ||
                    mediaId.startsWith("playlist:") || mediaId.startsWith("smartpl:") || mediaId.startsWith("genre:") ||
                    mediaId.startsWith("podcast:") || mediaId.startsWith("audiobook:") ||
                    mediaId in listOf(ROOT_ID, RECENT_ID, FAVORITES_ID, ALBUMS_ID, ARTISTS_ID, PLAYLISTS_ID, GENRES_ID, PODCASTS_ID, AUDIOBOOKS_ID)
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(isBrowsable)
                        .setIsPlayable(!isBrowsable)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Resolve media items: keep existing URI if present, otherwise build from track ID
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) {
                    // Already has a stream URI (e.g. audiobook chapter, podcast episode)
                    item
                } else {
                    val trackId = item.mediaId.toIntOrNull()
                    if (trackId != null) {
                        val streamUrl = ApiClient.streamUrl(trackId)
                        item.buildUpon()
                            .setUri(streamUrl)
                            .build()
                    } else {
                        item
                    }
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.future {
                try {
                    val api = ApiClient.api
                    val results = api.search(query, limit = 20)
                    val items = results.hits.map { trackToMediaItem(it) }
                    session.notifySearchResultChanged(browser, query, items.size, params)
                } catch (e: Exception) {
                    DebugLog.e("Auto", "Search error", e)
                }
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val api = ApiClient.api
                    val results = api.search(query, limit = pageSize)
                    val items = results.hits.map { trackToMediaItem(it) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    DebugLog.e("Auto", "Search results error", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }
    }

    // Browse tree builders

    private fun getRootChildren(): List<MediaItem> = listOf(
        browseFolderItem(RECENT_ID, "Recently Added", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browseFolderItem(FAVORITES_ID, "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browseFolderItem(ALBUMS_ID, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        browseFolderItem(ARTISTS_ID, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
        browseFolderItem(PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        browseFolderItem(GENRES_ID, "Genres", MediaMetadata.MEDIA_TYPE_FOLDER_GENRES),
        browseFolderItem(PODCASTS_ID, "Podcasts", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browseFolderItem(AUDIOBOOKS_ID, "Audiobooks", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
    )

    private suspend fun getRecentTracks(): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getRecentlyAdded(limit = 50)
        return response.tracks.map { trackToMediaItem(it) }
    }

    private suspend fun getFavoriteTracks(): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getFavorites()
        return response.tracks.map { trackToMediaItem(it) }
    }

    private suspend fun getAlbumsList(): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getAlbums(limit = 100)
        return response.albums.map { album ->
            val artUri = album.artPath?.let { ApiClient.artPathUrl(it) }
            MediaItem.Builder()
                .setMediaId("album:${album.displayName}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(album.displayName)
                        .setArtist(album.artist)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                        .apply { artUri?.let { setArtworkUri(Uri.parse(it)) } }
                        .build()
                )
                .build()
        }
    }

    private suspend fun getArtistsList(): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getArtists(limit = 100)
        return response.artists.mapNotNull { artist ->
            val id = artist.id ?: return@mapNotNull null
            MediaItem.Builder()
                .setMediaId("artist:$id")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(artist.name)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                        .build()
                )
                .build()
        }
    }

    private suspend fun getPlaylistsList(): List<MediaItem> {
        val api = ApiClient.api
        val playlists = try {
            api.getPlaylists().playlists.map { playlist ->
                MediaItem.Builder()
                    .setMediaId("playlist:${playlist.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(playlist.name)
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .build()
                    )
                    .build()
            }
        } catch (_: Exception) { emptyList() }

        val smartPlaylists = try {
            api.getSmartPlaylists().items.map { sp ->
                MediaItem.Builder()
                    .setMediaId("smartpl:${sp.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("⚡ ${sp.name}")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .build()
                    )
                    .build()
            }
        } catch (_: Exception) { emptyList() }

        return playlists + smartPlaylists
    }

    private suspend fun getGenresList(): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getGenres(limit = 100)
        return response.genres.map { genre ->
            MediaItem.Builder()
                .setMediaId("genre:${genre.name}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(genre.name)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_GENRE)
                        .build()
                )
                .build()
        }
    }

    private suspend fun getAlbumTracks(albumName: String): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getAlbumTracks(albumName)
        return response.tracks.map { trackToMediaItem(it) }
    }

    private suspend fun getArtistTracks(artistId: Int): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getArtistTracks(artistId)
        return response.tracks.map { trackToMediaItem(it) }
    }

    private suspend fun getPlaylistTracks(playlistId: Int): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getPlaylistItems(playlistId)
        return response.items.mapNotNull { it.track?.let { t -> trackToMediaItem(t) } }
    }

    private suspend fun getSmartPlaylistTracks(smartPlaylistId: Int): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getSmartPlaylist(smartPlaylistId, limit = 100)
        return response.tracks.map { trackToMediaItem(it) }
    }

    private suspend fun getGenreTracks(genreName: String): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getGenreTracks(genreName, limit = 100)
        return response.tracks.map { trackToMediaItem(it) }
    }

    // Podcast browse

    private suspend fun getPodcastsList(): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getPodcasts()
        return response.podcasts.map { podcast ->
            val artUri = podcast.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
                ?: ApiClient.podcastArtUrl(podcast.id)
            MediaItem.Builder()
                .setMediaId("podcast:${podcast.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(podcast.title)
                        .setArtist(podcast.author)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                        .setArtworkUri(Uri.parse(artUri))
                        .build()
                )
                .build()
        }
    }

    private suspend fun getPodcastEpisodes(podcastId: Int): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getPodcastDetail(podcastId)
        return response.episodes.map { episode ->
            val artUri = episode.imagePath?.let { ApiClient.podcastArtPathUrl(it) }
                ?: episode.podcastImagePath?.let { ApiClient.podcastArtPathUrl(it) }
                ?: ApiClient.episodeArtUrl(episode.id)
            val streamUrl = ApiClient.episodeStreamUrl(episode.id)
            val pseudoId = -episode.id
            MediaItem.Builder()
                .setMediaId(pseudoId.toString())
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(episode.podcastTitle ?: response.podcast?.title)
                        .setArtworkUri(Uri.parse(artUri))
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                        .build()
                )
                .build()
        }
    }

    // Audiobook browse

    private suspend fun getAudiobooksList(): List<MediaItem> {
        val api = ApiClient.api
        val books = api.getAudiobooks()
        return books.map { book ->
            val artUri = ApiClient.audiobookArtUrl(book.id)
            MediaItem.Builder()
                .setMediaId("audiobook:${book.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(book.title)
                        .setArtist(book.author)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setArtworkUri(Uri.parse(artUri))
                        .build()
                )
                .build()
        }
    }

    private suspend fun getAudiobookChapters(audiobookId: Int): List<MediaItem> {
        val api = ApiClient.api
        val response = api.getAudiobookDetail(audiobookId)
        val book = response.audiobook ?: return emptyList()
        val artUri = ApiClient.audiobookArtUrl(audiobookId)
        return response.chapters.map { chapter ->
            val pseudoId = -(audiobookId * 100000 + chapter.id)
            val streamUrl = ApiClient.audiobookChapterStreamUrl(audiobookId, chapter.id)
            MediaItem.Builder()
                .setMediaId(pseudoId.toString())
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.title)
                        .setArtist(book.author)
                        .setAlbumTitle(book.title)
                        .setArtworkUri(Uri.parse(artUri))
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()
                )
                .build()
        }
    }

    // Helpers

    private fun trackToMediaItem(track: com.mvbar.android.data.model.Track): MediaItem {
        val artUrl = track.artPath?.let { ApiClient.artPathUrl(it) }
            ?: ApiClient.trackArtUrl(track.id)
        val streamUrl = ApiClient.streamUrl(track.id)
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.displayTitle)
                    .setArtist(track.displayArtist)
                    .setAlbumTitle(track.displayAlbum)
                    .setArtworkUri(Uri.parse(artUrl))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun browseFolderItem(
        id: String,
        title: String,
        mediaType: Int
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .build()
        )
        .build()
}
