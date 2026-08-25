package com.senniapp.brickwares.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide online/offline signal. Backs the UI gating of network-only actions (sign-in / sign-out),
 * which must be hidden with no connection. "Online" = an active network that is INTERNET-capable and
 * VALIDATED (actually reaches the internet), tracked via the default-network callback.
 */
class ConnectivityObserver(context: Context) {

    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _isOnline.value = currentlyOnline() }
            override fun onLost(network: Network) { _isOnline.value = currentlyOnline() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasInternet()
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val active = cm?.activeNetwork ?: return false
        return cm.getNetworkCapabilities(active)?.hasInternet() == true
    }

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
