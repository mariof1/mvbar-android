package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.AudiobookEntity
import com.mvbar.android.data.local.entity.AudiobookChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobooks ORDER BY title COLLATE NOCASE ASC")
    fun allAudiobooksFlow(): Flow<List<AudiobookEntity>>

    @Query("SELECT * FROM audiobooks ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllAudiobooks(): List<AudiobookEntity>

    @Query("SELECT * FROM audiobook_chapters WHERE audiobookId = :audiobookId ORDER BY position ASC")
    suspend fun getChapters(audiobookId: Int): List<AudiobookChapterEntity>

    @Query("SELECT * FROM audiobook_chapters WHERE audiobookId = :audiobookId ORDER BY position ASC")
    fun chaptersFlow(audiobookId: Int): Flow<List<AudiobookChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<AudiobookChapterEntity>)

    @Query("DELETE FROM audiobooks")
    suspend fun deleteAllAudiobooks()

    @Query("DELETE FROM audiobook_chapters")
    suspend fun deleteAllChapters()

    @Query("DELETE FROM audiobook_chapters WHERE audiobookId = :audiobookId")
    suspend fun deleteChaptersForAudiobook(audiobookId: Int)

    @Transaction
    suspend fun replaceAllAudiobooks(audiobooks: List<AudiobookEntity>) {
        deleteAllAudiobooks()
        insertAudiobooks(audiobooks)
    }

    @Transaction
    suspend fun replaceChapters(audiobookId: Int, chapters: List<AudiobookChapterEntity>) {
        deleteChaptersForAudiobook(audiobookId)
        insertChapters(chapters)
    }
}
