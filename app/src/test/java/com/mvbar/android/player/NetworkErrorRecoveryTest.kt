package com.mvbar.android.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkErrorRecoveryTest {
    @Test fun singleEpisodeGetsBothRetriesThenStops() {
        assertEquals(NetworkErrorRecovery.RETRY, networkErrorRecovery(1, 1, true))
        assertEquals(NetworkErrorRecovery.RETRY, networkErrorRecovery(2, 1, true))
        assertEquals(NetworkErrorRecovery.PAUSE, networkErrorRecovery(3, 1, true))
    }

    @Test fun AudiobookNeverSkipsToAnotherChapterAfterNetworkFailure() {
        for (errors in 3..30) {
            assertEquals(NetworkErrorRecovery.PAUSE, networkErrorRecovery(errors, 26, true))
        }
    }

    @Test fun musicRetainsItsExistingRecoveryPolicy() {
        assertEquals(NetworkErrorRecovery.RETRY, networkErrorRecovery(1, 26, false))
        assertEquals(NetworkErrorRecovery.RETRY, networkErrorRecovery(2, 26, false))
        assertEquals(NetworkErrorRecovery.SKIP, networkErrorRecovery(3, 26, false))
        assertEquals(NetworkErrorRecovery.PAUSE, networkErrorRecovery(27, 26, false))
        assertEquals(NetworkErrorRecovery.PAUSE, networkErrorRecovery(2, 1, false))
    }
}
