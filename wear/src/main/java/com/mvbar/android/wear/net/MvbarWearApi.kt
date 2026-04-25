package com.mvbar.android.wear.net

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MvbarWearApi {

    // Music
    @GET("api/library/tracks")
    suspend fun recentTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "created_at",
        @Query("order") order: String = "desc"
    ): TracksResponse

    @GET("api/playlists")
    suspend fun playlists(): PlaylistsResponse

    @GET("api/playlists/{id}/items")
    suspend fun playlistTracks(@Path("id") id: Int): TracksResponse

    @GET("api/search")
    suspend fun search(@Query("q") query: String): SearchResults

    @GET("api/favorites")
    suspend fun favorites(): TracksResponse

    @POST("api/favorites/{id}")
    suspend fun addFavorite(@Path("id") id: Int)

    @DELETE("api/favorites/{id}")
    suspend fun removeFavorite(@Path("id") id: Int)

    // Podcasts
    @GET("api/podcasts")
    suspend fun podcasts(): PodcastsResponse

    @GET("api/podcasts/{id}")
    suspend fun podcastDetail(@Path("id") id: Int): PodcastDetailResponse

    @GET("api/podcasts/episodes/new")
    suspend fun newEpisodes(@Query("limit") limit: Int = 30): PodcastNewEpisodesResponse

    @POST("api/podcasts/episodes/{id}/progress")
    suspend fun setEpisodeProgress(
        @Path("id") id: Int,
        @Query("position_ms") positionMs: Long
    )

    @POST("api/podcasts/episodes/{id}/played")
    suspend fun markEpisodePlayed(@Path("id") id: Int)
}
