package com.mvbar.android.data.api

import com.mvbar.android.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MvbarApi {

    // Auth
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/auth/google/enabled")
    suspend fun isGoogleAuthEnabled(): GoogleAuthEnabledResponse

    @POST("api/auth/google/token")
    suspend fun googleSignIn(@Body request: GoogleTokenRequest): Response<LoginResponse>

    // Library / Tracks
    @GET("api/library/tracks")
    suspend fun getTracks(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null
    ): TracksResponse

    @GET("api/library/tracks/count")
    suspend fun getTrackCount(): Map<String, Int>

    // Browse
    @GET("api/browse/artists")
    suspend fun getArtists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): TracksListWrapper

    @GET("api/browse/artist/{id}")
    suspend fun getArtistDetail(@Path("id") id: Int): ArtistDetailResponse

    @GET("api/browse/artists/{id}/tracks")
    suspend fun getArtistTracks(@Path("id") id: Int): TracksResponse

    @GET("api/browse/albums")
    suspend fun getAlbums(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): AlbumsListWrapper

    @GET("api/browse/album")
    suspend fun getAlbumTracks(@Query("album") name: String): AlbumDetailResponse

    @GET("api/browse/genres")
    suspend fun getGenres(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): GenresListWrapper

    @GET("api/browse/genre/{name}/tracks")
    suspend fun getGenreTracks(
        @Path("name") name: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): TracksResponse

    @GET("api/browse/countries")
    suspend fun getCountries(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): CountriesListWrapper

    @GET("api/browse/country/{name}/tracks")
    suspend fun getCountryTracks(
        @Path("name") name: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): TracksResponse

    @GET("api/browse/languages")
    suspend fun getLanguages(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): LanguagesListWrapper

    @GET("api/browse/language/{name}/tracks")
    suspend fun getLanguageTracks(
        @Path("name") name: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): TracksResponse

    // Favorites
    @GET("api/favorites")
    suspend fun getFavorites(): FavoritesResponse

    @POST("api/favorites/{id}")
    suspend fun toggleFavorite(@Path("id") trackId: Int): Response<Unit>

    // History
    @GET("api/history")
    suspend fun getHistory(@Query("limit") limit: Int = 50): HistoryResponse

    @POST("api/history/{trackId}")
    suspend fun recordPlay(@Path("trackId") trackId: Int): Response<Unit>

    // Playlists
    @GET("api/playlists")
    suspend fun getPlaylists(): PlaylistsResponse

    @GET("api/playlists/{id}/items")
    suspend fun getPlaylistItems(@Path("id") id: Int): PlaylistItemsResponse

    @POST("api/playlists")
    suspend fun createPlaylist(@Body body: Map<String, String>): CreatePlaylistResponse

    @POST("api/playlists/{id}/items")
    suspend fun addToPlaylist(@Path("id") id: Int, @Body body: Map<String, Int>): Response<Unit>

    @DELETE("api/playlists/{id}/items/{trackId}")
    suspend fun removeFromPlaylist(@Path("id") id: Int, @Path("trackId") trackId: Int): Response<Unit>

    // Lyrics
    @GET("api/library/tracks/{id}/lyrics")
    suspend fun getLyrics(@Path("id") trackId: Int): Response<ResponseBody>

    @POST("api/library/tracks/{id}/lyrics/prefetch")
    suspend fun prefetchLyrics(@Path("id") trackId: Int): Response<Unit>

    // Search
    @GET("api/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 20): SearchResults

    // Recommendations
    @GET("api/recommendations")
    suspend fun getRecommendations(): RecommendationsResponse

    // Recently added
    @GET("api/library/tracks")
    suspend fun getRecentlyAdded(
        @Query("sort") sort: String = "created_at",
        @Query("order") order: String = "desc",
        @Query("limit") limit: Int = 50
    ): TracksResponse

    // Smart Playlists
    @GET("api/smart-playlists")
    suspend fun getSmartPlaylists(): SmartPlaylistsResponse

    @POST("api/smart-playlists")
    suspend fun createSmartPlaylist(@Body body: SmartPlaylistCreateRequest): SmartPlaylistResponse

    @GET("api/smart-playlists/{id}")
    suspend fun getSmartPlaylist(
        @Path("id") id: Int,
        @Query("limit") limit: Int = 500
    ): SmartPlaylistResponse

    @PUT("api/smart-playlists/{id}")
    suspend fun updateSmartPlaylist(
        @Path("id") id: Int,
        @Body body: SmartPlaylistCreateRequest
    ): SmartPlaylistResponse

    @GET("api/smart-playlists/suggest")
    suspend fun suggestSmartPlaylist(
        @Query("kind") kind: String,
        @Query("q") query: String = "",
        @Query("limit") limit: Int = 20,
        @Query("ids") ids: String? = null
    ): SuggestResponse

    @DELETE("api/smart-playlists/{id}")
    suspend fun deleteSmartPlaylist(@Path("id") id: Int): Response<Unit>

    // Podcasts
    @GET("api/podcasts")
    suspend fun getPodcasts(): PodcastsResponse

    @GET("api/podcasts/{id}")
    suspend fun getPodcastDetail(@Path("id") id: Int): PodcastDetailResponse

    @GET("api/podcasts/search")
    suspend fun searchPodcasts(@Query("q") query: String, @Query("limit") limit: Int = 25): PodcastSearchResponse

    @POST("api/podcasts/subscribe")
    suspend fun subscribePodcast(@Body body: PodcastSubscribeRequest): PodcastSubscribeResponse

    @DELETE("api/podcasts/{id}/unsubscribe")
    suspend fun unsubscribePodcast(@Path("id") id: Int): Response<Unit>

    @GET("api/podcasts/episodes/new")
    suspend fun getNewEpisodes(@Query("limit") limit: Int = 50): PodcastNewEpisodesResponse

    @POST("api/podcasts/episodes/{id}/progress")
    suspend fun updateEpisodeProgress(@Path("id") id: Int, @Body body: EpisodeProgressRequest): Response<Unit>

    @POST("api/podcasts/episodes/{id}/played")
    suspend fun markEpisodePlayed(@Path("id") id: Int, @Body body: EpisodePlayedRequest): Response<Unit>

    @POST("api/podcasts/{id}/refresh")
    suspend fun refreshPodcast(@Path("id") id: Int): PodcastRefreshResponse

    // Audiobooks
    @GET("api/audiobooks")
    suspend fun getAudiobooks(): List<Audiobook>

    @GET("api/audiobooks/{id}")
    suspend fun getAudiobookDetail(@Path("id") id: Int): AudiobookDetailResponse

    @POST("api/audiobooks/{id}/progress")
    suspend fun updateAudiobookProgress(
        @Path("id") audiobookId: Int,
        @Body body: AudiobookProgressRequest
    ): Response<Unit>

    @POST("api/audiobooks/{id}/mark-finished")
    suspend fun markAudiobookFinished(@Path("id") audiobookId: Int): Response<Unit>
}
