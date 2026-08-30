package com.mvbar.android.social

import kotlinx.coroutines.channels.Channel

object SocialNavigationRequests {
    private val requests = Channel<Unit>(capacity = Channel.CONFLATED)

    val events = requests

    fun openSocial() {
        requests.trySend(Unit)
    }
}

object PlaylistNavigationRequests {
    private val requests = Channel<Unit>(capacity = Channel.CONFLATED)

    val events = requests

    fun openPlaylists() {
        requests.trySend(Unit)
    }
}
