package com.mvbar.android.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.repository.AuthRepository
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.social.SocialNotificationManager
import com.mvbar.android.social.SocialRealtimeManager
import retrofit2.HttpException
import java.io.IOException

class SocialNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (ApiClient.getToken().isNullOrBlank()) {
            if (!AuthRepository(applicationContext).restoreSession()) return Result.success()
        }

        return try {
            val summary = ApiClient.api.getSocialSummary()
            val shares = ApiClient.api.getTrackShares(limit = 50)
            val playlists = ApiClient.api.getPlaylists()
            SocialNotificationManager.processSnapshot(
                applicationContext,
                summary,
                shares.shares,
                playlists.playlists
            )
            // Keep an existing activity/view model in sync with the snapshot that
            // produced the notification instead of requiring a manual refresh.
            SocialRealtimeManager.requestRefresh()
            Result.success()
        } catch (e: HttpException) {
            DebugLog.e("SocialSync", "Server error ${e.code()}")
            if (e.code() in 500..599) Result.retry() else Result.success()
        } catch (e: IOException) {
            DebugLog.d("SocialSync", "Network unavailable")
            Result.retry()
        } catch (e: Exception) {
            DebugLog.e("SocialSync", "Notification check failed", e)
            Result.retry()
        }
    }
}
