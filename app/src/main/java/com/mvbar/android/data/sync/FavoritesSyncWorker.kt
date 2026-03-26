package com.mvbar.android.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.FavoriteTrackEntity
import com.mvbar.android.data.repository.AuthRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.AudioCacheManager

/**
 * Lightweight background worker that syncs only favorites every 15 minutes.
 * Much lighter than the full SyncWorker — single API call, no track/browse sync.
 */
class FavoritesSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (ApiClient.getBaseUrl() == "http://localhost/") {
            val restored = AuthRepository(applicationContext).restoreSession()
            if (!restored) return Result.failure()
        }

        val api = try { ApiClient.api } catch (e: Exception) {
            DebugLog.e("FavSync", "API not configured", e)
            return Result.retry()
        }

        return try {
            val db = MvbarDatabase.getInstance(applicationContext)
            val favs = api.getFavorites()
            db.favoriteDao().replaceAll(favs.tracks.map { FavoriteTrackEntity(it.id) })
            DebugLog.i("FavSync", "Background synced ${favs.tracks.size} favorites")
            AudioCacheManager.reCacheFavorites()
            Result.success()
        } catch (e: Exception) {
            DebugLog.e("FavSync", "Background favorites sync failed", e)
            Result.retry()
        }
    }
}
