package com.mvbar.android.wear.ui

import com.mvbar.android.wear.net.Track

/**
 * Small in-memory holder for transient track-list navigation. The MusicTab
 * sets a loader + title here, then asks the navigator to open "tracklist".
 */
object TrackListBuffer {
    @Volatile var title: String = ""
    @Volatile var loader: (suspend () -> List<Track>)? = null

    fun set(title: String, loader: suspend () -> List<Track>) {
        this.title = title
        this.loader = loader
    }
}
