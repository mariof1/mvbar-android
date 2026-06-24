package com.mvbar.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mvbar.android.data.local.entity.PendingActionEntity

@Dao
interface PendingActionDao {
    @Insert
    suspend fun insert(action: PendingActionEntity)

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingActionEntity>

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_actions WHERE actionType = :actionType AND trackId = :trackId")
    suspend fun deleteByTypeAndTrack(actionType: String, trackId: Int)

    @Query("UPDATE pending_actions SET trackId = :newId WHERE trackId = :oldId")
    suspend fun replaceTrackId(oldId: Int, newId: Int)

    @Query("DELETE FROM pending_actions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int
}
