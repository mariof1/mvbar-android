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

    @Query("""
        SELECT * FROM playlists
        WHERE name LIKE '%' || :q || '%'
        ORDER BY name COLLATE NOCASE ASC
        LIMIT :limit
    """)
    suspend fun search(q: String, limit: Int): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Int): PlaylistEntity?

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getItems(playlistId: Int): List<PlaylistItemEntity>

    @Query("SELECT COALESCE(MIN(id), 0) - 1 FROM playlists WHERE id < 0")
    suspend fun nextLocalPlaylistId(): Int

    @Query("SELECT COALESCE(MIN(id), 0) - 1 FROM playlist_items WHERE id < 0")
    suspend fun nextLocalItemId(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun nextItemPosition(playlistId: Int): Int

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun containsTrack(playlistId: Int, trackId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Int, name: String)

    @Query("UPDATE playlists SET itemCount = MAX(0, itemCount + :delta) WHERE id = :playlistId")
    suspend fun adjustItemCount(playlistId: Int, delta: Int)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Int)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItemsForPlaylist(playlistId: Int)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteItemForTrack(playlistId: Int, trackId: Int): Int

    @Query("DELETE FROM playlist_items")
    suspend fun deleteAllItems()

    @Transaction
    suspend fun deletePlaylistWithItems(id: Int) {
        deleteItemsForPlaylist(id)
        deletePlaylist(id)
    }

    @Transaction
    suspend fun addTrack(playlistId: Int, trackId: Int) {
        if (containsTrack(playlistId, trackId) > 0) return
        insertItem(
            PlaylistItemEntity(
                id = nextLocalItemId(),
                playlistId = playlistId,
                trackId = trackId,
                position = nextItemPosition(playlistId)
            )
        )
        adjustItemCount(playlistId, 1)
    }

    @Transaction
    suspend fun removeTrack(playlistId: Int, trackId: Int) {
        val deleted = deleteItemForTrack(playlistId, trackId)
        if (deleted > 0) adjustItemCount(playlistId, -deleted)
    }

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
