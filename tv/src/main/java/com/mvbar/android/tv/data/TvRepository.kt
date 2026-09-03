package com.mvbar.android.tv.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.mvbar.android.tv.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID
import java.util.concurrent.TimeUnit

private interface TvApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun currentUser(): Response<CurrentUserResponse>

    @GET("api/library/tracks")
    suspend fun tracks(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null
    ): Response<TracksResponse>

    @GET("api/browse/albums")
    suspend fun albums(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0
    ): Response<AlbumsResponse>

    @GET("api/browse/album")
    suspend fun albumTracks(@Query("album") album: String): Response<AlbumDetailResponse>

    @GET("api/favorites")
    suspend fun favorites(): Response<FavoritesResponse>

    @POST("api/favorites/{trackId}")
    suspend fun addFavorite(@Path("trackId") trackId: Int): Response<BasicResponse>

    @DELETE("api/favorites/{trackId}")
    suspend fun removeFavorite(@Path("trackId") trackId: Int): Response<BasicResponse>

    @GET("api/recommendations")
    suspend fun recommendations(): Response<RecommendationsResponse>

    @GET("api/playlists")
    suspend fun playlists(): Response<PlaylistsResponse>

    @POST("api/playlists")
    suspend fun createPlaylist(@Body request: PlaylistNameRequest): Response<CreatePlaylistResponse>

    @GET("api/playlists/{id}/items")
    suspend fun playlistItems(@Path("id") id: Int): Response<PlaylistItemsResponse>

    @POST("api/playlists/{id}/items")
    suspend fun addPlaylistItem(
        @Path("id") id: Int,
        @Body request: PlaylistTrackRequest
    ): Response<BasicResponse>

    @DELETE("api/playlists/{id}/items/{trackId}")
    suspend fun removePlaylistItem(
        @Path("id") id: Int,
        @Path("trackId") trackId: Int
    ): Response<BasicResponse>

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Int): Response<BasicResponse>

    @GET("api/playlists/{id}/collaborators")
    suspend fun playlistCollaborators(@Path("id") id: Int): Response<PlaylistCollaborationResponse>

    @GET("api/smart-playlists")
    suspend fun smartPlaylists(): Response<SmartPlaylistsResponse>

    @GET("api/smart-playlists/{id}")
    suspend fun smartPlaylist(@Path("id") id: Int, @Query("limit") limit: Int): Response<SmartPlaylistResponse>

    @GET("api/podcasts")
    suspend fun podcasts(): Response<PodcastsResponse>

    @GET("api/podcasts/{id}")
    suspend fun podcast(@Path("id") id: Int): Response<PodcastDetailResponse>

    @GET("api/podcasts/episodes/new")
    suspend fun newEpisodes(@Query("limit") limit: Int): Response<PodcastNewEpisodesResponse>

    @POST("api/podcasts/episodes/{id}/progress")
    suspend fun updateEpisodeProgress(
        @Path("id") id: Int,
        @Body request: EpisodeProgressRequest
    ): Response<Unit>

    @GET("api/audiobooks")
    suspend fun audiobooks(): Response<List<Audiobook>>

    @GET("api/audiobooks/{id}")
    suspend fun audiobook(@Path("id") id: Int): Response<AudiobookDetailResponse>

    @POST("api/audiobooks/{id}/progress")
    suspend fun updateAudiobookProgress(
        @Path("id") id: Int,
        @Body request: AudiobookProgressRequest
    ): Response<Unit>

    @GET("api/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int): Response<SearchResponse>

    @GET("api/similar-tracks/{trackId}")
    suspend fun similarTracks(
        @Path("trackId") trackId: Int,
        @Query("exclude") exclude: String?
    ): Response<SimilarTracksResponse>

    @GET("api/social/share-targets/{trackId}")
    suspend fun shareTargets(@Path("trackId") trackId: Int): Response<ShareTargetsResponse>

    @POST("api/social/shares")
    suspend fun shareTrack(@Body request: ShareTrackRequest): Response<ShareTrackResponse>

    @GET("api/browse/artists")
    suspend fun artists(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
        @Query("q") query: String? = null
    ): Response<ArtistsResponse>

    @GET("api/browse/artist/{id}")
    suspend fun artist(@Path("id") id: Int): Response<ArtistDetailResponse>

    @GET("api/browse/artist/{id}/tracks")
    suspend fun artistTracks(@Path("id") id: Int): Response<ArtistTracksResponse>

    @POST("api/history/{trackId}")
    suspend fun recordPlay(@Path("trackId") trackId: Int): Response<Unit>
}

class TvSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("mvbar_tv_session", Context.MODE_PRIVATE)

    val clientId: String
        get() = preferences.getString("client_id", null) ?: "tv_${UUID.randomUUID()}".also {
            preferences.edit().putString("client_id", it).apply()
        }

    fun load(): TvSession? {
        val serverUrl = preferences.getString("server_url", null) ?: return null
        val token = preferences.getString("token", null) ?: return null
        val email = preferences.getString("email", "").orEmpty()
        return TvSession(serverUrl, token, email)
    }

    fun save(session: TvSession) {
        preferences.edit()
            .putString("server_url", session.serverUrl)
            .putString("token", session.token)
            .putString("email", session.email)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove("token")
            .remove("email")
            .apply()
    }

    fun lastServerUrl(): String = preferences.getString("server_url", "").orEmpty()
}

class TvRepository(
    session: TvSession,
    private val clientId: String
) {
    val serverUrl = normalizeServerUrl(session.serverUrl)
    private val token = session.token
    private val api = createApi(serverUrl, token, clientId)

    suspend fun verifySession(): User = api.currentUser().requireBody().user
        ?: throw TvApiException("The server did not return the current user")

    suspend fun recentlyAdded(): List<Track> =
        api.tracks(limit = 60, sort = "created_at").requireBody().tracks

    suspend fun albums(): List<Album> = api.albums(limit = 100).requireBody().albums

    suspend fun albumTracks(album: String): List<Track> =
        api.albumTracks(album).requireBody().tracks

    suspend fun favorites(): List<Track> = api.favorites().requireBody().tracks

    suspend fun setFavorite(trackId: Int, favorite: Boolean) {
        if (favorite) api.addFavorite(trackId).requireBody() else api.removeFavorite(trackId).requireBody()
    }

    suspend fun recommendations(): List<RecommendationBucket> =
        api.recommendations().requireBody().buckets.filter { it.tracks.isNotEmpty() }

    suspend fun playlists(): List<TvPlaylist> {
        val standard = api.playlists().requireBody().playlists.map {
            TvPlaylist(
                id = it.id,
                name = it.name,
                itemCount = it.itemCount,
                kind = TvPlaylist.Kind.STANDARD,
                ownerEmail = it.owner?.email,
                collaborative = it.isCollaborative,
                isOwner = it.isOwner,
                collaboratorCount = it.collaboratorCount
            )
        }
        val smart = api.smartPlaylists().requireBody().items.map {
            TvPlaylist(it.id, it.name, 0, TvPlaylist.Kind.SMART)
        }
        return standard + smart
    }

    suspend fun playlistTracks(playlist: TvPlaylist): List<Track> = when (playlist.kind) {
        TvPlaylist.Kind.STANDARD -> api.playlistItems(playlist.id).requireBody().items.mapNotNull(PlaylistItem::toTrack)
        TvPlaylist.Kind.SMART -> api.smartPlaylist(playlist.id, 500).requireBody().tracks
    }

    suspend fun createPlaylist(name: String): TvPlaylist {
        val playlist = api.createPlaylist(PlaylistNameRequest(name)).requireBody().playlist
            ?: throw TvApiException("MVBar did not return the new playlist")
        return TvPlaylist(playlist.id, playlist.name, playlist.itemCount, TvPlaylist.Kind.STANDARD)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, trackId: Int) {
        api.addPlaylistItem(playlistId, PlaylistTrackRequest(trackId)).requireBody()
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, trackId: Int) {
        api.removePlaylistItem(playlistId, trackId).requireBody()
    }

    suspend fun deletePlaylist(playlistId: Int) {
        api.deletePlaylist(playlistId).requireBody()
    }

    suspend fun playlistCollaboration(playlistId: Int): PlaylistCollaborationResponse =
        api.playlistCollaborators(playlistId).requireBody()

    suspend fun podcasts(): List<Podcast> = api.podcasts().requireBody().podcasts

    suspend fun podcast(id: Int): PodcastDetailResponse = api.podcast(id).requireBody()

    suspend fun newEpisodes(): List<Episode> = api.newEpisodes(60).requireBody().episodes

    suspend fun audiobooks(): List<Audiobook> = api.audiobooks().requireBody()

    suspend fun audiobook(id: Int): AudiobookDetailResponse = api.audiobook(id).requireBody()

    suspend fun search(query: String): SearchResponse = api.search(query, 60).requireBody()

    suspend fun similarTracks(trackId: Int, excludeIds: List<Int>): List<Track> =
        api.similarTracks(trackId, excludeIds.distinct().joinToString(",").ifBlank { null })
            .requireBody()
            .tracks

    suspend fun shareTargets(trackId: Int): List<SocialUser> =
        api.shareTargets(trackId).requireBody().friends.filter { it.canAccess }

    suspend fun shareTrack(trackId: Int, recipientId: String): Int =
        api.shareTrack(ShareTrackRequest(trackId, listOf(recipientId))).requireBody().shared

    suspend fun artist(id: Int): Pair<ArtistDetailResponse, List<Track>> =
        api.artist(id).requireBody() to api.artistTracks(id).requireBody().tracks

    suspend fun findArtist(name: String): Artist? =
        api.artists(limit = 10, query = name).requireBody().artists.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }

    suspend fun recordPlay(trackId: Int) {
        runCatching { api.recordPlay(trackId) }
    }

    suspend fun updateEpisodeProgress(episodeId: Int, positionMs: Long, played: Boolean) {
        runCatching { api.updateEpisodeProgress(episodeId, EpisodeProgressRequest(positionMs, played)) }
    }

    suspend fun updateAudiobookProgress(
        audiobookId: Int,
        chapterId: Int,
        positionMs: Long,
        finished: Boolean
    ) {
        runCatching {
            api.updateAudiobookProgress(
                audiobookId,
                AudiobookProgressRequest(chapterId, positionMs, finished)
            )
        }
    }

    fun streamUrl(trackId: Int): String = "${serverUrl}api/library/tracks/$trackId/stream"
    fun trackArtUrl(trackId: Int): String = "${serverUrl}api/library/tracks/$trackId/art"
    fun artPathUrl(path: String): String = "${serverUrl}api/art/${Uri.encode(path, "/")}"
    fun podcastArtUrl(podcast: Podcast): String = podcast.imagePath?.let {
        "${serverUrl}api/podcast-art/${Uri.encode(it, "/")}"
    } ?: "${serverUrl}api/podcasts/${podcast.id}/art"
    fun episodeArtUrl(episode: Episode): String = episode.imagePath?.let {
        "${serverUrl}api/podcast-art/${Uri.encode(it, "/")}"
    } ?: episode.podcastImagePath?.let {
        "${serverUrl}api/podcast-art/${Uri.encode(it, "/")}"
    } ?: "${serverUrl}api/podcasts/episodes/${episode.id}/art"
    fun episodeStreamUrl(episodeId: Int): String = "${serverUrl}api/podcasts/episodes/$episodeId/stream"
    fun audiobookArtUrl(audiobookId: Int): String = "${serverUrl}api/audiobook-art/$audiobookId"
    fun audiobookChapterStreamUrl(audiobookId: Int, chapterId: Int): String =
        "${serverUrl}api/audiobooks/$audiobookId/chapters/$chapterId/stream"

    companion object {
        suspend fun login(
            serverUrl: String,
            email: String,
            password: String,
            clientId: String
        ): TvSession {
            val normalizedUrl = normalizeServerUrl(serverUrl)
            val response = createApi(normalizedUrl, null, clientId).login(LoginRequest(email.trim(), password))
            val body = response.requireLoginBody()
            if (!body.ok || body.token.isBlank()) throw TvApiException("The server rejected this sign-in")
            return TvSession(
                serverUrl = normalizedUrl,
                token = body.token,
                email = body.user?.email?.takeIf { it.isNotBlank() } ?: email.trim()
            )
        }

        private fun createApi(serverUrl: String, token: String?, clientId: String): TvApi {
            val auth = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-MVBar-Client", "android-tv")
                    .header("X-MVBar-Client-Id", clientId)
                    .header("X-MVBar-Version", BuildConfig.VERSION_NAME)
                    .header("X-MVBar-Device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    .header("X-MVBar-Platform", "Android TV ${Build.VERSION.RELEASE}")
                    .apply { token?.let { header("Authorization", "Bearer $it") } }
                    .build()
                chain.proceed(request)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(auth)
                .addNetworkInterceptor { chain ->
                    val request = chain.request()
                    chain.proceed(request).also { response ->
                        Log.i(
                            "MvbarTvHttp",
                            "${request.method} ${request.url.encodedPath} -> ${response.code}"
                        )
                    }
                }
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            }
            return Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TvApi::class.java)
        }
    }
}

class TvApiException(message: String, val unauthorized: Boolean = false) : Exception(message)

private fun <T> Response<T>.requireBody(): T {
    if (code() == 401) throw TvApiException("Your session has expired", unauthorized = true)
    if (code() == 403) throw TvApiException("This account does not have permission to access this content")
    if (!isSuccessful) throw TvApiException("Server request failed (${code()})")
    return body() ?: throw TvApiException("The server returned an empty response")
}

private fun Response<LoginResponse>.requireLoginBody(): LoginResponse {
    when (code()) {
        401 -> throw TvApiException("Incorrect email or password")
        403 -> throw TvApiException("This account is not allowed to sign in")
        429 -> throw TvApiException("Too many sign-in attempts. Wait a moment and try again")
    }
    if (!isSuccessful) throw TvApiException("Sign-in failed (${code()})")
    return body() ?: throw TvApiException("The server returned an empty sign-in response")
}

internal fun normalizeServerUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    if (trimmed.isBlank()) throw TvApiException("Enter your MVBar server address")
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val parsed = runCatching { java.net.URI(withScheme) }.getOrNull()
    if (parsed?.scheme !in setOf("http", "https") || parsed?.host.isNullOrBlank()) {
        throw TvApiException("Enter a valid http:// or https:// server address")
    }
    return "$withScheme/"
}
