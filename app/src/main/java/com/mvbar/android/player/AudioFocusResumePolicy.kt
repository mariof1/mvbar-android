package com.mvbar.android.player

/**
 * Tracks whether playback may resume after a transient audio-focus interruption.
 * Explicit pause intent always wins, including when the player was already paused
 * by the focus manager and therefore emits no additional state-change callback.
 */
internal class AudioFocusResumePolicy {
    var userExplicitlyPaused: Boolean = false
        private set

    private var resumeOnFocusGain: Boolean = false

    fun onExplicitPlay() {
        userExplicitlyPaused = false
        resumeOnFocusGain = false
    }

    fun onExplicitPause() {
        userExplicitlyPaused = true
        resumeOnFocusGain = false
    }

    fun onTransientFocusLoss(wasPlaying: Boolean) {
        resumeOnFocusGain = wasPlaying && !userExplicitlyPaused
    }

    fun waitForDelayedFocus() {
        resumeOnFocusGain = !userExplicitlyPaused
    }

    fun cancelResume() {
        resumeOnFocusGain = false
    }

    fun consumeResumeOnFocusGain(): Boolean {
        val shouldResume = resumeOnFocusGain && !userExplicitlyPaused
        resumeOnFocusGain = false
        return shouldResume
    }
}
