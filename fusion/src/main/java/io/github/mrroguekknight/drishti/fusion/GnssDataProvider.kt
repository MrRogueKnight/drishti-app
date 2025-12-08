package io.github.mrroguekknight.drishti.fusion

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

/**
 * Manages the collection of GNSS data, both raw and fused.
 */
class GnssDataProvider(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private var gnssMeasurementsListener: GnssMeasurementsEvent.Callback? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onGnssMeasurement: (GnssMeasurementsEvent) -> Unit, onLocationUpdate: (LocationResult) -> Unit) {
        val handler = Handler(Looper.getMainLooper())

        // Raw GNSS Measurements
        gnssMeasurementsListener = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                onGnssMeasurement(event)
            }
        }.also {
            locationManager.registerGnssMeasurementsCallback(it, handler)
        }

        // Fused Location Updates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                onLocationUpdate(locationResult)
            }
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }

    fun stop() {
        gnssMeasurementsListener?.let { locationManager.unregisterGnssMeasurementsCallback(it) }
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}
