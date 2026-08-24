package com.mvbar.android.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusResumePolicyTest {
    @Test
    fun `transient interruption resumes playback that was playing`() {
        val policy = AudioFocusResumePolicy()

        policy.onExplicitPlay()
        policy.onTransientFocusLoss(wasPlaying = true)

        assertTrue(policy.consumeResumeOnFocusGain())
        assertFalse(policy.consumeResumeOnFocusGain())
    }

    @Test
    fun `pause while already focus-paused prevents later resume`() {
        val policy = AudioFocusResumePolicy()

        policy.onExplicitPlay()
        policy.onTransientFocusLoss(wasPlaying = true)
        // A transport pause can arrive after focus loss has already paused the
        // player, so no second playWhenReady=false callback is guaranteed.
        policy.onExplicitPause()

        assertFalse(policy.consumeResumeOnFocusGain())
        assertTrue(policy.userExplicitlyPaused)
    }

    @Test
    fun `manual play after pause permits future focus resume`() {
        val policy = AudioFocusResumePolicy()

        policy.onExplicitPause()
        policy.onExplicitPlay()
        policy.onTransientFocusLoss(wasPlaying = true)

        assertTrue(policy.consumeResumeOnFocusGain())
    }
}
