package com.mvbar.android.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible

/** Bridge blocking cache IO to cancellation, including a checkpoint for each chunk. */
internal suspend fun writeCacheCancellably(write: (checkCancelled: () -> Unit) -> Unit) {
    val context = currentCoroutineContext()
    runInterruptible(Dispatchers.IO) {
        context.ensureActive()
        write { context.ensureActive() }
    }
    context.ensureActive()
}
