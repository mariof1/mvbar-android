package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.PlaylistEntity
import com.mvbar.android.data.local.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun allFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getItems(playlistId: Int): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItemsForPlaylist(playlistId: Int)

    @Query("DELETE FROM playlist_items")
    suspend fun deleteAllItems()

    @Transaction
    suspend fun replaceAll(playlists: List<PlaylistEntity>) {
        deleteAllItems()
        deleteAllPlaylists()
        insertPlaylists(playlists)
    }

    @Transaction
    suspend fun replaceItems(playlistId: Int, items: List<PlaylistItemEntity>) {
        deleteItemsForPlaylist(playlistId)
        insertItems(items)
    }
}
