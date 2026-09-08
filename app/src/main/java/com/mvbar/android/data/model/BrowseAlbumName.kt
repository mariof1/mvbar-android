package com.mvbar.android.data.model

/** Same virtual album label as the server; never modifies cached or file tags. */
val Track.browseAlbumName: String
    get() = album?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Unknown Album — ${albumArtist?.trim()?.takeIf { it.isNotEmpty() }
            ?: artist?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown Artist"}"
