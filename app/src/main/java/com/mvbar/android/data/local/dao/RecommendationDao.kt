package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.RecBucketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao {
    @Query("SELECT * FROM rec_buckets")
    fun allFlow(): Flow<List<RecBucketEntity>>

    @Query("SELECT * FROM rec_buckets")
    suspend fun getAll(): List<RecBucketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(buckets: List<RecBucketEntity>)

    @Query("DELETE FROM rec_buckets")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(buckets: List<RecBucketEntity>) {
        deleteAll()
        insertAll(buckets)
    }
}
