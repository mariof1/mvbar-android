package com.mvbar.android.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.*
import com.mvbar.android.debug.DebugLog

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val api = try { ApiClient.api } catch (e: Exception) {
            DebugLog.e("SyncWorker", "API not configured", e)
            return Result.retry()
        }

        val db = MvbarDatabase.getInstance(applicationContext)
        SyncManager.setIsSyncing(true)
        DebugLog.i("SyncWorker", "Starting sync...")

        return try {
            // 1. Sync all tracks (paginated)
            SyncManager.setSyncStatus("Syncing tracks...")
            syncTracks(db)

            // 2. Sync browse data (paginated)
            SyncManager.setSyncStatus("Syncing artists...")
            syncArtists(db)
            SyncManager.setSyncStatus("Syncing albums...")
            syncAlbums(db)
            SyncManager.setSyncStatus("Syncing genres...")
            syncGenres(db)
            SyncManager.setSyncStatus("Syncing countries...")
            syncCountries(db)
            SyncManager.setSyncStatus("Syncing languages...")
            syncLanguages(db)

            // 3. Sync favorites
            SyncManager.setSyncStatus("Syncing favorites...")
            try {
                val favs = api.getFavorites()
                db.favoriteDao().replaceAll(favs.tracks.map { FavoriteTrackEntity(it.id) })
                DebugLog.i("SyncWorker", "Synced ${favs.tracks.size} favorites")
            } catch (e: Exception) {
                DebugLog.e("SyncWorker", "Favorites sync failed", e)
            }

            // 4. Sync history
            SyncManager.setSyncStatus("Syncing history...")
            try {
                val hist = api.getHistory(200)
                db.historyDao().replaceAll(
                    hist.tracks.mapIndexed { idx, t -> HistoryEntryEntity(trackId = t.id, position = idx) }
                )
                DebugLog.i("SyncWorker", "Synced ${hist.tracks.size} history entries")
            } catch (e: Exception) {
                DebugLog.e("SyncWorker", "History sync failed", e)
            }

            // 5. Sync playlists + items
            SyncManager.setSyncStatus("Syncing playlists...")
            try {
                val plResp = api.getPlaylists()
                db.playlistDao().replaceAll(plResp.playlists.map { it.toEntity() })
                for (pl in plResp.playlists) {
                    try {
                        val items = api.getPlaylistItems(pl.id)
                        db.playlistDao().replaceItems(
                            pl.id,
                            items.items.map { it.toEntity(pl.id) }
                        )
                    } catch (e: Exception) {
                        DebugLog.e("SyncWorker", "Playlist items sync failed for ${pl.id}", e)
                    }
                }
                DebugLog.i("SyncWorker", "Synced ${plResp.playlists.size} playlists")
            } catch (e: Exception) {
                DebugLog.e("SyncWorker", "Playlists sync failed", e)
            }

            // 6. Sync recommendations
            SyncManager.setSyncStatus("Syncing recommendations...")
            try {
                val recs = api.getRecommendations()
                db.recommendationDao().replaceAll(recs.buckets.map { it.toEntity() })
                DebugLog.i("SyncWorker", "Synced ${recs.buckets.size} recommendation buckets")
            } catch (e: Exception) {
                DebugLog.e("SyncWorker", "Recommendations sync failed", e)
            }

            // 7. Sync podcasts + episodes
            SyncManager.setSyncStatus("Syncing podcasts...")
            try {
                val pods = api.getPodcasts()
                db.podcastDao().replaceAllPodcasts(pods.podcasts.map { it.toEntity() })
                for (pod in pods.podcasts) {
                    try {
                        val detail = api.getPodcastDetail(pod.id)
                        db.podcastDao().replaceEpisodes(
                            pod.id,
                            detail.episodes.map { it.toEntity() }
                        )
                    } catch (e: Exception) {
                        DebugLog.e("SyncWorker", "Episodes sync failed for podcast ${pod.id}", e)
                    }
                }
                DebugLog.i("SyncWorker", "Synced ${pods.podcasts.size} podcasts")
            } catch (e: Exception) {
                DebugLog.e("SyncWorker", "Podcasts sync failed", e)
            }

            val now = System.currentTimeMillis()
            SyncManager.updateLastSyncTime(now)
            SyncManager.setIsSyncing(false)
            DebugLog.i("SyncWorker", "Sync complete")
            Result.success()
        } catch (e: Exception) {
            DebugLog.e("SyncWorker", "Sync failed", e)
            SyncManager.setIsSyncing(false)
            Result.retry()
        }
    }

    private suspend fun syncTracks(db: MvbarDatabase) {
        val api = ApiClient.api
        try {
            val pageSize = 200
            val allTracks = mutableListOf<TrackEntity>()
            var offset = 0
            while (true) {
                val resp = api.getTracks(limit = pageSize, offset = offset)
                allTracks.addAll(resp.tracks.map { it.toEntity() })
                SyncManager.setSyncStatus("Syncing tracks... ${allTracks.size} fetched")
                if (resp.tracks.size < pageSize) break
                offset += pageSize
            }
            db.trackDao().replaceAll(allTracks)
            DebugLog.i("SyncWorker", "Synced ${allTracks.size} tracks")
        } catch (e: Exception) {
            DebugLog.e("SyncWorker", "Tracks sync failed", e)
        }
    }

    private suspend fun syncArtists(db: MvbarDatabase) {
        val api = ApiClient.api
        try {
            val all = mutableListOf<ArtistEntity>()
            var offset = 0
            val pageSize = 200
            while (true) {
                val resp = api.getArtists(limit = pageSize, offset = offset)
                all.addAll(resp.artists.map { it.toEntity() })
                if (resp.artists.size < pageSize) break
                offset += pageSize
            }
            db.browseDao().replaceAllArtists(all)
            DebugLog.i("SyncWorker", "Synced ${all.size} artists")
        } catch (e: Exception) {
            DebugLog.e("SyncWorker", "Artists sync failed", e)
        }
    }

    private suspend fun syncAlbums(db: MvbarDatabase) {
        val api = ApiClient.api
        try {
            val all = mutableListOf<AlbumEntity>()
            var offset = 0
            val pageSize = 200
            while (true) {
                val resp = api.getAlbums(limit = pageSize, offset = offset)
                all.addAll(resp.albums.map { it.toEntity() })
                if (resp.albums.size < pageSize) break
                offset += pageSize
            }
            db.browseDao().replaceAllAlbums(all)
            DebugLog.i("SyncWorker", "Synced ${all.size} albums")
        } catch (e: Exception) {
            DebugLog.e("SyncWorker", "Albums sync failed", e)
        }
    }

    private suspend fun syncGenres(db: MvbarDatabase) {
        val api = ApiClient.api
        try {
            val all = mutableListOf<GenreEntity>()
            var offset = 0
            val pageSize = 200
            while (true) {
                val resp = api.getGenres(limit = pageSize, offset = offset)
                all.addAll(resp.genres.map { it.toEntity() })
                if (resp.genres.size < pageSize) break
                offset += pageSize
            }
            db.browseDao().replaceAllGenres(all)
            DebugLog.i("SyncWorker", "Synced ${all.size} genres")
        } catch (e: Exception) {
            DebugLog.e("SyncWorker", "Genres sync failed", e)
        }
    }

    private suspend fun syncCountries(db: MvbarDatabase) {
        val api = ApiClient.api
        try {
            val all = mutableListOf<CountryEntity>()
            var offset = 0
            val pageSize = 200
            while (true) {
                val resp = api.getCountries(limit = pageSize, offset = offset)
                all.addAll(resp.countries.map { it.toEntity() })
                if (resp.countries.size < pageSize) break
                offset += pageSize
            }
            db.browseDao().replaceAllCountries(all)
            DebugLog.i("SyncWorker", "Synced ${all.size} countries")
        } catch (e: Exception) {
            DebugLog.e("SyncWorker", "Countries sync failed", e)
        }
    }

    private suspend fun syncLanguages(db: MvbarDatabase) {
        val api = ApiClient.api
        try {
            val all = mutableListOf<LanguageEntity>()
            var offset = 0
            val pageSize = 200
            while (true) {
                val resp = api.getLanguages(limit = pageSize, offset = offset)
                all.addAll(resp.languages.map { it.toEntity() })
                if (resp.languages.size < pageSize) break
                offset += pageSize
            }
            db.browseDao().replaceAllLanguages(all)
            DebugLog.i("SyncWorker", "Synced ${all.size} languages")
        } catch (e: Exception) {
            DebugLog.e("SyncWorker", "Languages sync failed", e)
        }
    }
}
