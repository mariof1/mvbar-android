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

    @Query("""
        SELECT * FROM podcasts
        WHERE title COLLATE NOCASE LIKE '%' || :query || '%'
           OR COALESCE(author, '') COLLATE NOCASE LIKE '%' || :query || '%'
           OR COALESCE(description, '') COLLATE NOCASE LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        LIMIT :limit
    """)
    suspend fun searchPodcasts(query: String, limit: Int): List<PodcastEntity>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    suspend fun getEpisodes(podcastId: Int): List<EpisodeEntity>

    @Query("""
        SELECT * FROM episodes
        WHERE title COLLATE NOCASE LIKE '%' || :query || '%'
           OR COALESCE(podcastTitle, '') COLLATE NOCASE LIKE '%' || :query || '%'
           OR COALESCE(description, '') COLLATE NOCASE LIKE '%' || :query || '%'
        ORDER BY publishedAt DESC
        LIMIT :limit
    """)
    suspend fun searchEpisodes(query: String, limit: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE positionMs > 0 AND played = 0 ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getContinueListening(limit: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE played = 0 ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getUnplayedEpisodes(limit: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun episodesFlow(podcastId: Int): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcasts(podcasts: List<PodcastEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM podcasts")
    suspend fun deleteAllPodcasts()

    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcast(podcastId: Int)

    @Query("DELETE FROM episodes")
    suspend fun deleteAllEpisodes()

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: Int)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :episodeId")
    suspend fun updateEpisodePosition(episodeId: Int, positionMs: Long)

    @Query("UPDATE episodes SET played = :played WHERE id = :episodeId")
    suspend fun updateEpisodePlayed(episodeId: Int, played: Boolean)

    @Query("UPDATE podcasts SET unplayedCount = MAX(0, unplayedCount + :delta) WHERE id = :podcastId")
    suspend fun adjustUnplayedCount(podcastId: Int, delta: Int)

    @Query("SELECT podcastId FROM episodes WHERE id = :episodeId")
    suspend fun podcastIdForEpisode(episodeId: Int): Int?

    @Query("SELECT played FROM episodes WHERE id = :episodeId")
    suspend fun isEpisodePlayed(episodeId: Int): Boolean?

    @Transaction
    suspend fun markEpisodePlayedLocal(episodeId: Int, played: Boolean) {
        val wasPlayed = isEpisodePlayed(episodeId)
        if (wasPlayed == played) return
        val podcastId = podcastIdForEpisode(episodeId)
        updateEpisodePlayed(episodeId, played)
        if (podcastId != null) adjustUnplayedCount(podcastId, if (played) -1 else 1)
    }

    @Query("SELECT * FROM episodes WHERE id IN (:ids)")
    suspend fun getEpisodesByIds(ids: List<Int>): List<EpisodeEntity>

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
