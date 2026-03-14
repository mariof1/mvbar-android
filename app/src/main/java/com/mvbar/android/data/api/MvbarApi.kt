package com.mvbar.android.data.api

import com.mvbar.android.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface MvbarApi {

    // Auth
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

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
    suspend fun getArtists(): BrowseArtistsResponse

    @GET("api/browse/artists/{id}")
    suspend fun getArtist(@Path("id") id: Int): Artist

    @GET("api/browse/artists/{id}/tracks")
    suspend fun getArtistTracks(@Path("id") id: Int): TracksResponse

    @GET("api/browse/albums")
    suspend fun getAlbums(): BrowseAlbumsResponse

    @GET("api/browse/albums/{name}/tracks")
    suspend fun getAlbumTracks(@Path("name") name: String): TracksResponse

    @GET("api/browse/genres")
    suspend fun getGenres(): BrowseGenresResponse

    @GET("api/browse/genres/{name}/tracks")
    suspend fun getGenreTracks(@Path("name") name: String): TracksResponse

    // Favorites
    @GET("api/favorites")
    suspend fun getFavorites(): FavoritesResponse

    @POST("api/favorites/{id}")
    suspend fun toggleFavorite(@Path("id") trackId: Int): Response<Unit>

    // History
    @GET("api/history")
    suspend fun getHistory(@Query("limit") limit: Int = 50): HistoryResponse

    @POST("api/history")
    suspend fun recordPlay(@Body body: Map<String, Int>): Response<Unit>

    // Playlists
    @GET("api/playlists")
    suspend fun getPlaylists(): PlaylistsResponse

    @GET("api/playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: Int): Playlist

    @POST("api/playlists")
    suspend fun createPlaylist(@Body body: Map<String, String>): Playlist

    @POST("api/playlists/{id}/items")
    suspend fun addToPlaylist(@Path("id") id: Int, @Body body: Map<String, Int>): Response<Unit>

    @DELETE("api/playlists/{id}/items/{itemId}")
    suspend fun removeFromPlaylist(@Path("id") id: Int, @Path("itemId") itemId: Int): Response<Unit>

    // Search
    @GET("api/search")
    suspend fun search(@Query("q") query: String): SearchResults

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
}
