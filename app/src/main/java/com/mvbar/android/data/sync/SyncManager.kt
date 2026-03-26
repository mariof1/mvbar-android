package com.mvbar.android.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

object SyncManager {
    private const val PREFS_NAME = "mvbar_sync_prefs"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"
    private const val KEY_SYNC_INTERVAL = "sync_interval_hours"
    private const val WORK_NAME = "mvbar_periodic_sync"
    private const val FAV_WORK_NAME = "mvbar_favorites_sync"

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    fun getSyncIntervalHours(): Int {
        return if (::prefs.isInitialized) prefs.getInt(KEY_SYNC_INTERVAL, 6) else 6
    }

    fun setSyncIntervalHours(context: Context, hours: Int) {
        prefs.edit().putInt(KEY_SYNC_INTERVAL, hours).apply()
        schedulePeriodic(context, hours)
    }

    fun updateLastSyncTime(timestamp: Long) {
        if (::prefs.isInitialized) {
            prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
        }
        _lastSyncTime.value = timestamp
    }

    fun setIsSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
        if (!syncing) _syncStatus.value = ""
    }

    fun setSyncStatus(status: String) {
        _syncStatus.value = status
    }

    fun schedulePeriodic(context: Context, intervalHours: Int = getSyncIntervalHours()) {
        DebugLog.i("SyncManager", "Scheduling periodic sync every ${intervalHours}h")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun syncNow(context: Context) {
        DebugLog.i("SyncManager", "Triggering manual sync")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    /** Schedule a lightweight favorites-only sync every 15 minutes in the background. */
    fun scheduleFavoritesSync(context: Context) {
        DebugLog.i("SyncManager", "Scheduling favorites sync every 15min")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<FavoritesSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FAV_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
