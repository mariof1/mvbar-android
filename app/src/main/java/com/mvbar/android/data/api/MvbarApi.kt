package com.mvbar.android.data.api

import com.mvbar.android.data.model.*
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

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<CurrentUserResponse>

    // Library / Tracks
    @GET("api/library/tracks")
    suspend fun getTracks(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null
    ): TracksResponse

    @GET("api/library/tracks/count")
    suspend fun getTrackCount(): Map<String, Int>

    @GET("api/library/tracks/{id}/cast-url")
    suspend fun getCastUrl(@Path("id") trackId: Int): CastUrlResponse

    @GET("api/preferences")
    suspend fun getPreferences(): PreferencesResponse

    // Browse
    @GET("api/browse/artists")
    suspend fun getArtists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("letter") letter: String? = null
    ): TracksListWrapper

    @GET("api/browse/artist/{id}")
    suspend fun getArtistDetail(@Path("id") id: Int): ArtistDetailResponse

    @GET("api/browse/artist/{id}/tracks")
    suspend fun getArtistTracks(
        @Path("id") id: Int,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): TracksResponse

    @GET("api/browse/albums")
    suspend fun getAlbums(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("letter") letter: String? = null
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
    suspend fun addFavorite(@Path("id") trackId: Int): Response<Unit>

    @DELETE("api/favorites/{id}")
    suspend fun removeFavorite(@Path("id") trackId: Int): Response<Unit>

    // History
    @GET("api/history")
    suspend fun getHistory(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): HistoryResponse

    @POST("api/history/{trackId}")
    suspend fun recordPlay(
        @Path("trackId") trackId: Int,
        @Body signal: PlaybackSignalRequest = PlaybackSignalRequest()
    ): Response<Unit>

    // Stats
    @POST("api/stats/skip/{trackId}")
    suspend fun recordSkip(
        @Path("trackId") trackId: Int,
        @Body signal: PlaybackSignalRequest = PlaybackSignalRequest()
    ): Response<Unit>

    // Playlists
    @GET("api/playlists")
    suspend fun getPlaylists(): PlaylistsResponse

    @GET("api/playlists/{id}/items")
    suspend fun getPlaylistItems(@Path("id") id: Int): PlaylistItemsResponse

    @POST("api/playlists")
    suspend fun createPlaylist(@Body body: Map<String, String>): CreatePlaylistResponse

    @PATCH("api/playlists/{id}")
    suspend fun renamePlaylist(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): retrofit2.Response<okhttp3.ResponseBody>

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Int): Response<Unit>

    @POST("api/playlists/{id}/items")
    suspend fun addToPlaylist(@Path("id") id: Int, @Body body: Map<String, Int>): Response<Unit>

    @DELETE("api/playlists/{id}/items/{trackId}")
    suspend fun removeFromPlaylist(@Path("id") id: Int, @Path("trackId") trackId: Int): Response<Unit>

    // Friends and private track sharing
    @GET("api/social/summary")
    suspend fun getSocialSummary(): SocialSummary

    @GET("api/social/users")
    suspend fun searchSocialUsers(@Query("q") query: String): SocialUserSearchResponse

    @POST("api/social/friend-requests")
    suspend fun sendFriendRequest(@Body body: FriendRequestBody): FriendRequestResponse

    @POST("api/social/friend-requests/{id}/accept")
    suspend fun acceptFriendRequest(@Path("id") relationshipId: Int): SocialActionResponse

    @DELETE("api/social/friend-requests/{id}")
    suspend fun removeFriendRequest(@Path("id") relationshipId: Int): SocialActionResponse

    @DELETE("api/social/friends/{userId}")
    suspend fun removeFriend(@Path("userId") userId: String): SocialActionResponse

    @GET("api/social/share-targets/{trackId}")
    suspend fun getShareTargets(@Path("trackId") trackId: Int): ShareTargetsResponse

    @POST("api/social/shares")
    suspend fun shareTrack(@Body body: ShareTrackBody): ShareTrackResponse

    @GET("api/social/shares")
    suspend fun getTrackShares(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): TrackSharesResponse

    @POST("api/social/shares/{id}/read")
    suspend fun markTrackShareRead(@Path("id") shareId: Int): SocialActionResponse

    @POST("api/social/shares/read-all")
    suspend fun markAllTrackSharesRead(): SocialActionResponse

    @DELETE("api/social/shares/{id}")
    suspend fun deleteTrackShare(@Path("id") shareId: Int): SocialActionResponse

    // Lyrics
    @GET("api/library/tracks/{id}/lyrics")
    suspend fun getLyrics(@Path("id") trackId: Int): Response<LyricsResponse>

    @POST("api/library/tracks/{id}/lyrics/prefetch")
    suspend fun prefetchLyrics(@Path("id") trackId: Int): Response<Unit>

    // Search
    @GET("api/scan/progress")
    suspend fun scanProgress(): ScanProgress

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SearchResults

    @GET("api/search/recent")
    suspend fun getRecentSearches(@Query("limit") limit: Int = 10): RecentSearchesResponse

    @POST("api/search/recent")
    suspend fun saveRecentSearch(@Body request: RecentSearchRequest): RecentSearchItem

    @DELETE("api/search/recent")
    suspend fun removeRecentSearch(
        @Query("type") itemType: String,
        @Query("key") itemKey: String
    ): RecentSearchActionResponse

    @DELETE("api/search/recent")
    suspend fun clearRecentSearches(): RecentSearchActionResponse

    @POST("api/ai/intent")
    suspend fun createAiMix(@Body request: AiIntentRequest): AiIntentResponse

    // Recommendations
    @GET("api/recommendations")
    suspend fun getRecommendations(): RecommendationsResponse

    @POST("api/recommendations/feedback")
    suspend fun sendRecommendationFeedback(
        @Body request: RecommendationFeedbackRequest
    ): RecommendationFeedbackResponse

    @GET("api/recommendations/feedback")
    suspend fun getRecommendationFeedback(): RecommendationPreferencesResponse

    @DELETE("api/recommendations/feedback/all")
    suspend fun clearAllRecommendationFeedback(): RecommendationResetResponse

    @DELETE("api/recommendations/feedback/hidden-buckets")
    suspend fun clearHiddenRecommendationBuckets(): RecommendationResetResponse

    // Similar tracks (Last.fm-based auto-continue)
    @GET("api/similar-tracks/{trackId}")
    suspend fun getSimilarTracks(
        @Path("trackId") trackId: Int,
        @Query("exclude") exclude: String? = null
    ): SimilarTracksResponse

    // Recently added
    @GET("api/library/tracks")
    suspend fun getRecentlyAdded(
        @Query("sort") sort: String = "created_at",
        @Query("order") order: String = "desc",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
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

    @POST("api/smart-playlists/{id}/convert")
    suspend fun convertSmartPlaylist(
        @Path("id") id: Int,
        @Body body: Map<String, Boolean> = emptyMap()
    ): retrofit2.Response<okhttp3.ResponseBody>

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

    @GET("api/podcasts/preview")
    suspend fun previewPodcast(@Query("feedUrl") feedUrl: String): PodcastPreviewResponse

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
