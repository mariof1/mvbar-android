package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.FavoriteTrackEntity
import com.mvbar.android.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN favorite_tracks f ON t.id = f.trackId
        ORDER BY t.title COLLATE NOCASE ASC
    """)
    fun favoritesFlow(): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN favorite_tracks f ON t.id = f.trackId
        ORDER BY t.title COLLATE NOCASE ASC
    """)
    suspend fun getFavorites(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteTrackEntity>)

    @Query("DELETE FROM favorite_tracks")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(favorites: List<FavoriteTrackEntity>) {
        deleteAll()
        insertAll(favorites)
    }
}
