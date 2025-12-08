package io.github.mrroguekknight.drishti.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

class NetworkStatusHelper(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    data class NetworkStatus(
        val isConnected: Boolean = false,
        val type: String = "None",
        val signalStrength: Int = 0, // 0-4 or similar
        val linkSpeed: Int = 0, // Mbps
        val ipAddress: String = "0.0.0.0",
        val frequency: Int = 0 // MHz
    )

    fun getNetworkStatusFlow(): Flow<NetworkStatus> = flow {
        while (true) {
            emit(getCurrentNetworkStatus())
            delay(1000) // Update every second
        }
    }

    private fun getCurrentNetworkStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus()
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus()

        var type = "Unknown"
        var signalStrength = 0
        var linkSpeed = 0
        var ipAddress = "0.0.0.0"
        var frequency = 0

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            type = "Wi-Fi"
            val wifiInfo = wifiManager.connectionInfo
            signalStrength = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)
            linkSpeed = wifiInfo.linkSpeed
            frequency = wifiInfo.frequency
            ipAddress = formatIpAddress(wifiInfo.ipAddress)
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            type = "Cellular"
            // Signal strength for cellular is harder to get synchronously without listeners, 
            // but we can try to infer or leave as placeholder for now if permissions are complex.
            // For now, we'll assume full strength if connected or implement TelephonyCallback later if needed.
            signalStrength = 3 // Placeholder
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            type = "Ethernet"
            signalStrength = 4
        }

        return NetworkStatus(
            isConnected = true,
            type = type,
            signalStrength = signalStrength,
            linkSpeed = linkSpeed,
            ipAddress = ipAddress,
            frequency = frequency
        )
    }

    private fun formatIpAddress(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            (ip and 0xff),
            (ip shr 8 and 0xff),
            (ip shr 16 and 0xff),
            (ip shr 24 and 0xff)
        )
    }
}
