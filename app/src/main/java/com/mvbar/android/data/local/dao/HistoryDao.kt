package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.HistoryEntryEntity
import com.mvbar.android.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN history_entries h ON t.id = h.trackId
        ORDER BY h.position ASC
    """)
    fun historyFlow(): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN history_entries h ON t.id = h.trackId
        ORDER BY h.position ASC
    """)
    suspend fun getHistory(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HistoryEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId: Int)

    @Query("UPDATE history_entries SET position = position + 1")
    suspend fun incrementPositions()

    @Transaction
    suspend fun recordPlay(trackId: Int) {
        deleteTrack(trackId)
        incrementPositions()
        insert(HistoryEntryEntity(trackId = trackId, position = 0))
    }

    @Query("DELETE FROM history_entries")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entries: List<HistoryEntryEntity>) {
        deleteAll()
        insertAll(entries)
    }
}
