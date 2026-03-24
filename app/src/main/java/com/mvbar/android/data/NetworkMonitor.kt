package com.mvbar.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.mvbar.android.debug.DebugLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var registered = false

    fun init(context: Context) {
        if (registered) return
        registered = true

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Set initial state
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        DebugLog.i("Network", "Initial state: online=${_isOnline.value}")

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !_isOnline.value
                _isOnline.value = true
                if (wasOffline) {
                    DebugLog.i("Network", "Connectivity restored")
                    notifyReconnected()
                }
            }

            override fun onLost(network: Network) {
                // Check if there's still another active network
                val stillConnected = cm.activeNetwork?.let {
                    cm.getNetworkCapabilities(it)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
                if (!stillConnected) {
                    _isOnline.value = false
                    DebugLog.i("Network", "Connectivity lost")
                }
            }
        })
    }

    // Listeners notified when we transition from offline → online
    private val reconnectListeners = mutableListOf<() -> Unit>()

    fun addReconnectListener(listener: () -> Unit) {
        synchronized(reconnectListeners) { reconnectListeners.add(listener) }
    }

    fun removeReconnectListener(listener: () -> Unit) {
        synchronized(reconnectListeners) { reconnectListeners.remove(listener) }
    }

    private fun notifyReconnected() {
        synchronized(reconnectListeners) {
            reconnectListeners.forEach { it.invoke() }
        }
    }

    /** Flow that emits true each time connectivity is restored (offline → online) */
    fun reconnectEvents(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            private var wasOnline = _isOnline.value

            override fun onAvailable(network: Network) {
                if (!wasOnline) {
                    trySend(true)
                }
                wasOnline = true
            }

            override fun onLost(network: Network) {
                val stillConnected = cm.activeNetwork?.let {
                    cm.getNetworkCapabilities(it)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
                if (!stillConnected) wasOnline = false
            }
        }

        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
