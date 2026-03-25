package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.EpisodeEntity
import com.mvbar.android.data.local.entity.PodcastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE ASC")
    fun allPodcastsFlow(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllPodcasts(): List<PodcastEntity>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    suspend fun getEpisodes(podcastId: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun episodesFlow(podcastId: Int): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcasts(podcasts: List<PodcastEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM podcasts")
    suspend fun deleteAllPodcasts()

    @Query("DELETE FROM episodes")
    suspend fun deleteAllEpisodes()

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: Int)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :episodeId")
    suspend fun updateEpisodePosition(episodeId: Int, positionMs: Long)

    @Transaction
    suspend fun replaceAllPodcasts(podcasts: List<PodcastEntity>) {
        deleteAllEpisodes()
        deleteAllPodcasts()
        insertPodcasts(podcasts)
    }

    @Transaction
    suspend fun replaceEpisodes(podcastId: Int, episodes: List<EpisodeEntity>) {
        deleteEpisodesForPodcast(podcastId)
        insertEpisodes(episodes)
    }
}
