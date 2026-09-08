package com.mvbar.android.player

internal enum class NetworkErrorRecovery { RETRY, SKIP, PAUSE }

internal fun networkErrorRecovery(
    consecutiveErrors: Int,
    queueSize: Int,
    isLongForm: Boolean
): NetworkErrorRecovery = when {
    // Never skip chapters or episodes because their stream is temporarily unavailable.
    isLongForm -> if (consecutiveErrors <= 2) NetworkErrorRecovery.RETRY else NetworkErrorRecovery.PAUSE
    consecutiveErrors > queueSize -> NetworkErrorRecovery.PAUSE
    consecutiveErrors <= 2 -> NetworkErrorRecovery.RETRY
    queueSize > 1 -> NetworkErrorRecovery.SKIP
    else -> NetworkErrorRecovery.PAUSE
}
