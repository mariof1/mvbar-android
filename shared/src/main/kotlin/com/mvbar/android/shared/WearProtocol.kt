package com.mvbar.android.shared

/**
 * Constants shared between the phone app and the Wear OS companion.
 *
 * Communication uses the Wearable Data Layer:
 *  - `MessageClient` for fire-and-forget commands (play/pause/next/seek)
 *  - `DataClient` for replicated state (now-playing snapshot, auth token)
 *
 * Paths must match exactly on both sides.
 */
object WearProtocol {

    // MessageClient paths (watch -> phone)
    const val PATH_CMD_PLAY_PAUSE = "/mvbar/cmd/play_pause"
    const val PATH_CMD_NEXT = "/mvbar/cmd/next"
    const val PATH_CMD_PREV = "/mvbar/cmd/prev"
    const val PATH_CMD_SEEK_FORWARD = "/mvbar/cmd/seek_forward"
    const val PATH_CMD_SEEK_BACK = "/mvbar/cmd/seek_back"
    const val PATH_CMD_FAVORITE = "/mvbar/cmd/favorite"

    // DataClient paths (phone -> watch)
    const val PATH_NOW_PLAYING = "/mvbar/state/now_playing"
    const val PATH_AUTH = "/mvbar/state/auth"

    // DataItem keys
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ALBUM = "album"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_POSITION_MS = "position_ms"
    const val KEY_IS_PLAYING = "is_playing"
    const val KEY_IS_PODCAST = "is_podcast"
    const val KEY_IS_AUDIOBOOK = "is_audiobook"
    const val KEY_FAVORITE = "favorite"
    const val KEY_ARTWORK = "artwork"
    const val KEY_TIMESTAMP = "timestamp"

    const val KEY_AUTH_TOKEN = "token"
    const val KEY_SERVER_URL = "server_url"

    const val CAPABILITY_PHONE_APP = "mvbar_phone_app"
    const val CAPABILITY_WEAR_APP = "mvbar_wear_app"
}
