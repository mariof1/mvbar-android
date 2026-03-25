package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun allFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Int): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' LIMIT :limit")
    suspend fun search(q: String, limit: Int = 50): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE genre = :genre LIMIT :limit OFFSET :offset")
    suspend fun getByGenre(genre: String, limit: Int, offset: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNumber, trackNumber")
    suspend fun getByAlbum(album: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE artist = :artist OR displayArtistName = :artist OR albumArtist = :artist")
    suspend fun getByArtist(artist: String): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(tracks: List<TrackEntity>) {
        deleteAll()
        insertAll(tracks)
    }

    @Query("SELECT * FROM tracks ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentlyAdded(limit: Int): List<TrackEntity>
}
