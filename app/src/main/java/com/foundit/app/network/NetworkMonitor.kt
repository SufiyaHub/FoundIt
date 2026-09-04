package com.foundit.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

class NetworkMonitor(
    context: Context,
    private val onConnectionChanged: (Boolean) -> Unit
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onConnectionChanged(true)
            }

            override fun onLost(network: Network) {
                onConnectionChanged(isOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                onConnectionChanged(networkCapabilities.hasInternet())
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback!!)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback!!)
        }

        onConnectionChanged(isOnline())
    }

    fun stop() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
    }

    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasInternet()
    }

    private fun NetworkCapabilities.hasInternet(): Boolean {
        return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
