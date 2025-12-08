package io.github.mrroguekknight.drishti.network

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper

class WifiP2pHelper(private val context: Context) {

    private val manager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null

    fun initialize() {
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
    }

    fun discoverPeers(actionListener: WifiP2pManager.ActionListener) {
        channel?.let {
            manager?.discoverPeers(it, actionListener)
        }
    }
}