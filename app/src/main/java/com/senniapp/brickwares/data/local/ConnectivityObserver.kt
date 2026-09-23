package com.senniapp.brickwares.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide online/offline signal. Backs the UI gating of network-only actions (sign-in / sign-out) and
 * the reconnect recovery in Home / SyncCoordinator. "Online" = an active default network that is
 * INTERNET-capable, tracked via the default-network callback.
 *
 * It deliberately does NOT also require NET_CAPABILITY_VALIDATED. VALIDATED reflects the SYSTEM's own
 * probe (to Google endpoints) succeeding, which can stay unset for a long time — or indefinitely — on
 * networks/regions where that probe is throttled or blocked, even while the app's own traffic reaches
 * Supabase fine. Requiring it left the app stuck "Offline" on a working connection (Settings chip,
 * Home's New Sets, reconnect-sync never recovering) despite Search loading. INTERNET-capability recovers
 * the instant a default network appears; a request that still fails surfaces the error state anyway.
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
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
