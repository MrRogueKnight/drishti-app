package io.github.mrroguekknight.drishti.fusion

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurement
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

    private val _gnssMetrics = kotlinx.coroutines.flow.MutableStateFlow(GnssMetrics())
    val gnssMetrics: kotlinx.coroutines.flow.StateFlow<GnssMetrics> = _gnssMetrics

    @SuppressLint("MissingPermission")
    fun start(onGnssMeasurement: (GnssMeasurementsEvent) -> Unit, onLocationUpdate: (LocationResult) -> Unit) {
        val handler = Handler(Looper.getMainLooper())

        // Raw GNSS Measurements
        gnssMeasurementsListener = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                // Forward raw event
                onGnssMeasurement(event)
                
                // Analyze for Quality and Stationary Detection
                processMeasurements(event)
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

    private fun processMeasurements(event: GnssMeasurementsEvent) {
        var totalCn0 = 0.0
        var validSatCount = 0
        var dopplerSum = 0.0
        
        for (measurement in event.measurements) {
            if (measurement.constellationType == GnssStatus.CONSTELLATION_GPS || 
                measurement.constellationType == GnssStatus.CONSTELLATION_GALILEO ||
                measurement.constellationType == GnssStatus.CONSTELLATION_GLONASS) {
                
                if (measurement.cn0DbHz > 15.0) { // Filter weak signals
                    totalCn0 += measurement.cn0DbHz
                    validSatCount++
                    
                    // Check if uncertainty is valid (non-zero or below threshold)
                    val uncert = measurement.pseudorangeRateUncertaintyMetersPerSecond
                    if (uncert > 0 && uncert < 10.0) {
                        dopplerSum += Math.abs(measurement.pseudorangeRateMetersPerSecond)
                    }
                }
            }
        }
        
        val avgCn0 = if (validSatCount > 0) totalCn0 / validSatCount else 0.0
        
        // Doppler "Energy" metric. Low energy = Stationary. High energy = Moving.
        // This is a heuristic.
        val isStationary = validSatCount > 4 && dopplerSum < 0.5 // Threshold TBD
        
        _gnssMetrics.value = GnssMetrics(
            averageCn0 = avgCn0,
            satelliteCount = validSatCount,
            dopplerEnergy = dopplerSum,
            isLikelyStationary = isStationary
        )
    }

    fun stop() {
        gnssMeasurementsListener?.let { locationManager.unregisterGnssMeasurementsCallback(it) }
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
    
    data class GnssMetrics(
        val averageCn0: Double = 0.0,
        val satelliteCount: Int = 0,
        val dopplerEnergy: Double = 0.0,
        val isLikelyStationary: Boolean = false
    )
}
