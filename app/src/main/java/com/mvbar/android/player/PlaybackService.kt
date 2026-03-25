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
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.toModel
import com.mvbar.android.data.NetworkMonitor
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
    private val db by lazy { MvbarDatabase.getInstance(this) }
    private var reconnectListener: (() -> Unit)? = null

    companion object {
        private const val ROOT_ID = "[root]"
        private const val FOR_YOU_ID = "[foryou]"
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
            DebugLog.i("Player", "No API configured — restoring session synchronously")
            kotlinx.coroutines.runBlocking {
                try {
                    AuthRepository(this@PlaybackService).restoreSession()
                    DebugLog.i("Player", "Restored API session in PlaybackService (baseUrl=${ApiClient.getBaseUrl()}, hasToken=${ApiClient.getToken() != null})")
                } catch (e: Exception) {
                    DebugLog.e("Player", "Failed to restore session in PlaybackService", e)
                }
            }
        } else {
            DebugLog.i("Player", "API already configured (baseUrl=${ApiClient.getBaseUrl()}, hasToken=${ApiClient.getToken() != null})")
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

        // Monitor network: refresh browse tree when connectivity is restored
        NetworkMonitor.init(this)
        val listener: () -> Unit = {
            DebugLog.i("Player", "Network restored — refreshing Android Auto browse tree")
            mediaSession?.let { session ->
                val topLevelIds = listOf(
                    ROOT_ID, FOR_YOU_ID, RECENT_ID, FAVORITES_ID,
                    ALBUMS_ID, ARTISTS_ID, PLAYLISTS_ID, GENRES_ID,
                    PODCASTS_ID, AUDIOBOOKS_ID
                )
                for (id in topLevelIds) {
                    session.connectedControllers.forEach { ctrl ->
                        session.notifyChildrenChanged(ctrl, id, 0, null)
                    }
                }
            }
        }
        reconnectListener = listener
        NetworkMonitor.addReconnectListener(listener)

        DebugLog.i("Player", "PlaybackService created with Android Auto support")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onDestroy() {
        reconnectListener?.let { NetworkMonitor.removeReconnectListener(it) }
        reconnectListener = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            DebugLog.i("Auto", "onConnect: package=${controller.packageName} uid=${controller.controllerVersion}")
            val playerCommands = Player.Commands.Builder()
                .addAllCommands()
                .build()
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            DebugLog.i("Auto", "onGetLibraryRoot from ${browser.packageName}")
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
                    DebugLog.i("Auto", "onGetChildren: parentId=$parentId page=$page")
                    val items = when {
                        parentId == ROOT_ID -> getRootChildren()
                        parentId == FOR_YOU_ID -> getForYouBuckets()
                        parentId == RECENT_ID -> getRecentTracks()
                        parentId == FAVORITES_ID -> getFavoriteTracks()
                        parentId == ALBUMS_ID -> getAlbumsList()
                        parentId == ARTISTS_ID -> getArtistsList()
                        parentId == PLAYLISTS_ID -> getPlaylistsList()
                        parentId == GENRES_ID -> getGenresList()
                        parentId == PODCASTS_ID -> getPodcastsList()
                        parentId == AUDIOBOOKS_ID -> getAudiobooksList()
                        parentId.startsWith("bucket:") -> getBucketTracks(parentId.removePrefix("bucket:"))
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
                    mediaId.startsWith("playlist:") || mediaId.startsWith("smartpl:") ||
                    mediaId.startsWith("genre:") || mediaId.startsWith("bucket:") ||
                    mediaId.startsWith("podcast:") || mediaId.startsWith("audiobook:") ||
                    mediaId in listOf(ROOT_ID, FOR_YOU_ID, RECENT_ID, FAVORITES_ID, ALBUMS_ID, ARTISTS_ID, PLAYLISTS_ID, GENRES_ID, PODCASTS_ID, AUDIOBOOKS_ID)
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
        browseFolderItem(FOR_YOU_ID, "For You", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browseFolderItem(RECENT_ID, "Recently Added", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browseFolderItem(FAVORITES_ID, "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browseFolderItem(ALBUMS_ID, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        browseFolderItem(ARTISTS_ID, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
        browseFolderItem(PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        browseFolderItem(GENRES_ID, "Genres", MediaMetadata.MEDIA_TYPE_FOLDER_GENRES),
        browseFolderItem(PODCASTS_ID, "Podcasts", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browseFolderItem(AUDIOBOOKS_ID, "Audiobooks", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
    )

    /** Try API call if online, otherwise skip straight to DB fallback (avoids 30s timeout) */
    private suspend fun <T> apiOrCache(
        tag: String,
        apiCall: suspend () -> T,
        cacheCall: suspend () -> T
    ): T {
        if (!NetworkMonitor.isOnline.value) {
            DebugLog.d("Auto", "$tag: offline, using cache")
            return cacheCall()
        }
        return try {
            apiCall()
        } catch (e: Exception) {
            DebugLog.w("Auto", "$tag API failed, using cache", e)
            cacheCall()
        }
    }

    private suspend fun getRecentTracks(): List<MediaItem> {
        return apiOrCache("Recent tracks",
            apiCall = {
                val response = ApiClient.api.getRecentlyAdded(limit = 50)
                response.tracks.map { trackToMediaItem(it) }
            },
            cacheCall = {
                db.trackDao().getRecentlyAdded(50).map { trackToMediaItem(it.toModel()) }
            }
        )
    }

    private var cachedBuckets: List<com.mvbar.android.data.model.RecBucket> = emptyList()

    private suspend fun getForYouBuckets(): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getRecommendations()
            cachedBuckets = response.buckets
            response.buckets.mapIndexed { index, bucket ->
                val artUri = bucket.artPaths.firstOrNull()?.let { ApiClient.artPathUrl(it) }
                MediaItem.Builder()
                    .setMediaId("bucket:$index")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(bucket.name)
                            .setSubtitle(bucket.subtitle ?: "${bucket.count} tracks")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .apply { artUri?.let { setArtworkUri(Uri.parse(it)) } }
                            .build()
                    )
                    .build()
            }
        } catch (e: Exception) {
            DebugLog.w("Auto", "For You API failed, using cache", e)
            val cached = db.recommendationDao().getAll().map { it.toModel() }
            cachedBuckets = cached
            cached.mapIndexed { index, bucket ->
                val artUri = bucket.artPaths.firstOrNull()?.let { ApiClient.artPathUrl(it) }
                MediaItem.Builder()
                    .setMediaId("bucket:$index")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(bucket.name)
                            .setSubtitle(bucket.subtitle ?: "${bucket.count} tracks")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .apply { artUri?.let { setArtworkUri(Uri.parse(it)) } }
                            .build()
                    )
                    .build()
            }
        }
    }

    private suspend fun getBucketTracks(indexStr: String): List<MediaItem> {
        val index = indexStr.toIntOrNull() ?: return emptyList()
        val bucket = cachedBuckets.getOrNull(index)
        if (bucket != null && bucket.tracks.isNotEmpty()) {
            return bucket.tracks.map { trackToMediaItem(it) }
        }
        if (!NetworkMonitor.isOnline.value) return emptyList()
        return try {
            val api = ApiClient.api
            val response = api.getRecommendations()
            cachedBuckets = response.buckets
            response.buckets.getOrNull(index)?.tracks?.map { trackToMediaItem(it) } ?: emptyList()
        } catch (e: Exception) {
            DebugLog.w("Auto", "Bucket tracks API failed", e)
            emptyList()
        }
    }

    private suspend fun getFavoriteTracks(): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getFavorites()
            response.tracks.map { trackToMediaItem(it) }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Favorites API failed, using cache", e)
            db.favoriteDao().getFavorites().map { trackToMediaItem(it.toModel()) }
        }
    }

    private suspend fun getAlbumsList(): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getAlbums(limit = 100)
            response.albums.map { album ->
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Albums API failed, using cache", e)
            db.browseDao().getAlbums(100, 0).map { entity ->
                val album = entity.toModel()
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
    }

    private suspend fun getArtistsList(): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getArtists(limit = 100)
            response.artists.mapNotNull { artist ->
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Artists API failed, using cache", e)
            db.browseDao().getArtists(100, 0).mapNotNull { entity ->
                val artist = entity.toModel()
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
    }

    private suspend fun getPlaylistsList(): List<MediaItem> {
        val offline = !NetworkMonitor.isOnline.value
        val playlists = try {
            if (offline) throw java.io.IOException("offline")
            val api = ApiClient.api
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Playlists API failed, using cache", e)
            db.playlistDao().getAll().map { entity ->
                MediaItem.Builder()
                    .setMediaId("playlist:${entity.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(entity.name)
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .build()
                    )
                    .build()
            }
        }

        val smartPlaylists = try {
            if (offline) throw java.io.IOException("offline")
            val api = ApiClient.api
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
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getGenres(limit = 100)
            response.genres.map { genre ->
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Genres API failed, using cache", e)
            db.browseDao().getGenres(100, 0).map { entity ->
                MediaItem.Builder()
                    .setMediaId("genre:${entity.name}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(entity.name)
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_GENRE)
                            .build()
                    )
                    .build()
            }
        }
    }

    private suspend fun getAlbumTracks(albumName: String): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getAlbumTracks(albumName)
            response.tracks.map { trackToMediaItem(it) }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Album tracks API failed, using cache", e)
            db.trackDao().getByAlbum(albumName).map { trackToMediaItem(it.toModel()) }
        }
    }

    private suspend fun getArtistTracks(artistId: Int): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getArtistTracks(artistId)
            response.tracks.map { trackToMediaItem(it) }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Artist tracks API failed, using cache", e)
            // Try to find artist name from browse cache, then query by name
            val artists = db.browseDao().getArtists(200, 0)
            val artistName = artists.find { it.artistId == artistId }?.name
            if (artistName != null) {
                db.trackDao().getByArtist(artistName).map { trackToMediaItem(it.toModel()) }
            } else emptyList()
        }
    }

    private suspend fun getPlaylistTracks(playlistId: Int): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getPlaylistItems(playlistId)
            response.items.mapNotNull { it.track?.let { t -> trackToMediaItem(t) } }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Playlist tracks API failed, using cache", e)
            db.playlistDao().getItems(playlistId).mapNotNull {
                val track = db.trackDao().getById(it.trackId)
                track?.let { t -> trackToMediaItem(t.toModel()) }
            }
        }
    }

    private suspend fun getSmartPlaylistTracks(smartPlaylistId: Int): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getSmartPlaylist(smartPlaylistId, limit = 100)
            response.tracks.map { trackToMediaItem(it) }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Smart playlist tracks API failed", e)
            emptyList()
        }
    }

    private suspend fun getGenreTracks(genreName: String): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getGenreTracks(genreName, limit = 100)
            response.tracks.map { trackToMediaItem(it) }
        } catch (e: Exception) {
            DebugLog.w("Auto", "Genre tracks API failed, using cache", e)
            db.trackDao().getByGenre(genreName, 100, 0).map { trackToMediaItem(it.toModel()) }
        }
    }

    // Podcast browse

    private suspend fun getPodcastsList(): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getPodcasts()
            response.podcasts.map { podcast ->
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Podcasts API failed, using cache", e)
            db.podcastDao().getAllPodcasts().map { entity ->
                val podcast = entity.toModel()
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
    }

    private suspend fun getPodcastEpisodes(podcastId: Int): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getPodcastDetail(podcastId)
            response.episodes.map { episode ->
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Podcast episodes API failed, using cache", e)
            db.podcastDao().getEpisodes(podcastId).map { entity ->
                val episode = entity.toModel()
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
                            .setArtworkUri(Uri.parse(artUri))
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                            .build()
                    )
                    .build()
            }
        }
    }

    // Audiobook browse

    private suspend fun getAudiobooksList(): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val books = api.getAudiobooks()
            books.map { book ->
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Audiobooks API failed, using cache", e)
            db.audiobookDao().getAllAudiobooks().map { entity ->
                val book = entity.toModel()
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
    }

    private suspend fun getAudiobookChapters(audiobookId: Int): List<MediaItem> {
        return try {
            if (!NetworkMonitor.isOnline.value) throw java.io.IOException("offline")
            val api = ApiClient.api
            val response = api.getAudiobookDetail(audiobookId)
            val book = response.audiobook ?: return emptyList()
            val artUri = ApiClient.audiobookArtUrl(audiobookId)
            response.chapters.map { chapter ->
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
        } catch (e: Exception) {
            DebugLog.w("Auto", "Audiobook chapters API failed, using cache", e)
            val book = db.audiobookDao().getAllAudiobooks().find { it.id == audiobookId }
            val artUri = ApiClient.audiobookArtUrl(audiobookId)
            db.audiobookDao().getChapters(audiobookId).map { entity ->
                val chapter = entity.toModel()
                val pseudoId = -(audiobookId * 100000 + chapter.id)
                val streamUrl = ApiClient.audiobookChapterStreamUrl(audiobookId, chapter.id)
                MediaItem.Builder()
                    .setMediaId(pseudoId.toString())
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(chapter.title)
                            .setArtist(book?.author)
                            .setAlbumTitle(book?.title)
                            .setArtworkUri(Uri.parse(artUri))
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .build()
                    )
                    .build()
            }
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
