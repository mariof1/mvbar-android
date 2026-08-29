package com.mvbar.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mvbar.android.data.local.dao.*
import com.mvbar.android.data.local.entity.*

/** Migration 2→3: add pending_actions table for offline activity queue */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_actions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                actionType TEXT NOT NULL,
                trackId INTEGER NOT NULL,
                payload TEXT,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

/** Migration 3→4: keep country/language metadata for offline Browse filters. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN country TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN language TEXT")
    }
}

/** Migration 4→5: retain collaborative-playlist permissions and labels offline. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN ownerEmail TEXT")
        db.execSQL("ALTER TABLE playlists ADD COLUMN isOwner INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE playlists ADD COLUMN isCollaborative INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN collaboratorCount INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        TrackEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        GenreEntity::class,
        CountryEntity::class,
        LanguageEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        FavoriteTrackEntity::class,
        HistoryEntryEntity::class,
        RecBucketEntity::class,
        PodcastEntity::class,
        EpisodeEntity::class,
        AudiobookEntity::class,
        AudiobookChapterEntity::class,
        PendingActionEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MvbarDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun browseDao(): BrowseDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun podcastDao(): PodcastDao
    abstract fun audiobookDao(): AudiobookDao
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        @Volatile
        private var INSTANCE: MvbarDatabase? = null

        fun getInstance(context: Context): MvbarDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MvbarDatabase::class.java,
                    "mvbar_cache.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
