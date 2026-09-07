package com.mvbar.android.player

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class CacheWriteCancellationTest {
    @Test fun cancellationInterruptsBlockedWriteWithoutReportingCompletion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finished = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val job = launch {
            writeCacheCancellably { _ ->
                try {
                    started.complete(Unit)
                    CountDownLatch(1).await()
                    finished.set(true)
                } finally { closed.set(true) }
            }
        }
        withTimeout(5_000) {
            started.await()
            job.cancelAndJoin()
        }
        assertTrue(job.isCancelled)
        assertTrue(closed.get())
        assertFalse(finished.get())
    }

    @Test fun ordinaryWriteCompletesAllChunks() = runBlocking {
        var chunks = 0
        writeCacheCancellably { checkCancelled ->
            repeat(3) { checkCancelled(); chunks++ }
        }
        assertEquals(3, chunks)
    }
}
