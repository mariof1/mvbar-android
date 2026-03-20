package com.mvbar.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mvbar.android.data.local.dao.*
import com.mvbar.android.data.local.entity.*

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
        EpisodeEntity::class
    ],
    version = 1,
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
