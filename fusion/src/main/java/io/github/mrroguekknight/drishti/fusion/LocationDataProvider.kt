package io.github.mrroguekknight.drishti.fusion

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationDataProvider(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission") // Permissions are checked in Splash/Main
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        val listener = object : LocationListener {
            private var lastLocation: Location? = null
            private var lastTime: Long = 0

            override fun onLocationChanged(location: Location) {
                val currentTime = System.currentTimeMillis()
                
                // Calculate speed manually if not provided
                if (!location.hasSpeed() && lastLocation != null) {
                    val distance = lastLocation!!.distanceTo(location) // in meters
                    val timeDiff = (currentTime - lastTime) / 1000.0 // in seconds
                    
                    if (timeDiff > 0) {
                        val calculatedSpeed = distance / timeDiff
                        // Filter noise: ignore if speed is unlikely high or very low (drift)
                        // Threshold: 0.01 m/s (~0.036 km/h) to 100 m/s
                        if (calculatedSpeed > 0.01 && calculatedSpeed < 100) { 
                             location.speed = calculatedSpeed.toFloat()
                        } else {
                             location.speed = 0f
                        }
                    }
                }
                
                lastLocation = location
                lastTime = currentTime
                
                trySend(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // Request updates from both providers for best results
            // Use 0L and 0f for maximum frequency (Real-time)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                 locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    listener
                )
            }
        } catch (e: Exception) {
            // Handle permission or other errors
            android.util.Log.e("LocationDataProvider", "Error getting location updates", e)
            // Do not close with exception as it crashes the collector
            close() 
        }

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }
}
