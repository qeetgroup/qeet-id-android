package com.qeetgroup.qeetid.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the phone has a network that reaches the internet. Sign-in codes don't care; approvals do. */
interface Connectivity {
    val online: StateFlow<Boolean>
}

class SystemConnectivity(
    context: Context,
) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(hasInternet(manager?.activeNetwork))
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        manager?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }
            },
        )
    }

    private fun hasInternet(network: Network?): Boolean =
        network != null && manager?.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
