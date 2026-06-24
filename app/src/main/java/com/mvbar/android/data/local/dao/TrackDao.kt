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

    @Query("SELECT * FROM tracks ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    suspend fun getAllForBrowse(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Int): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<TrackEntity>

    @Query("""
        SELECT * FROM tracks
        WHERE title LIKE '%' || :q || '%'
            OR artist LIKE '%' || :q || '%'
            OR displayArtistName LIKE '%' || :q || '%'
            OR album LIKE '%' || :q || '%'
            OR genre LIKE '%' || :q || '%'
            OR country LIKE '%' || :q || '%'
            OR language LIKE '%' || :q || '%'
        ORDER BY title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun search(q: String, limit: Int = 50, offset: Int = 0): List<TrackEntity>

    @Query("""
        SELECT * FROM tracks
        WHERE LOWER(COALESCE(genre, '')) LIKE '%' || LOWER(:genre) || '%'
        ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, discNumber, trackNumber, title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getByGenre(genre: String, limit: Int, offset: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE LOWER(COALESCE(genre, '')) LIKE '%' || LOWER(:genre) || '%'")
    suspend fun countByGenre(genre: String): Int

    @Query("""
        SELECT * FROM tracks
        WHERE LOWER(COALESCE(country, '')) LIKE '%' || LOWER(:country) || '%'
        ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, discNumber, trackNumber, title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getByCountry(country: String, limit: Int, offset: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE LOWER(COALESCE(country, '')) LIKE '%' || LOWER(:country) || '%'")
    suspend fun countByCountry(country: String): Int

    @Query("""
        SELECT * FROM tracks
        WHERE LOWER(COALESCE(language, '')) LIKE '%' || LOWER(:language) || '%'
        ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, discNumber, trackNumber, title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getByLanguage(language: String, limit: Int, offset: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE LOWER(COALESCE(language, '')) LIKE '%' || LOWER(:language) || '%'")
    suspend fun countByLanguage(language: String): Int

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNumber, trackNumber")
    suspend fun getByAlbum(album: String): List<TrackEntity>

    @Query("""
        SELECT * FROM tracks
        WHERE artist = :artist OR displayArtistName = :artist OR albumArtist = :artist
        ORDER BY album COLLATE NOCASE ASC, discNumber, trackNumber, title COLLATE NOCASE ASC
    """)
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
