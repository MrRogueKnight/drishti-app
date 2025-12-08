package io.github.mrroguekknight.drishti.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import android.os.Handler
import android.os.Looper

class GnssManager(private val context: Context) {
  private val locationManager = context.getSystemService(LocationManager::class.java)

  private val gnssCallback = object : GnssMeasurementsEvent.Callback() {
    @SuppressLint("MissingPermission")
    override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
      val measurements = eventArgs.measurements
      measurements.forEach { m ->
        val svid = m.svid
        val constellation = m.constellationType
        val timeNanos = m.timeOffsetNanos
        val state = m.state
        val pseudorangeRate = m.pseudorangeRateMetersPerSecond
        val accumulatedDeltaRange = m.accumulatedDeltaRangeMeters
        // package these into your observation format
      }
    }

    override fun onStatusChanged(status: Int) { /* handle */ }
  }

  @SuppressLint("MissingPermission")
  fun start() {
    locationManager.registerGnssMeasurementsCallback(gnssCallback, Handler(Looper.getMainLooper()))
  }

  fun stop() {
    locationManager.unregisterGnssMeasurementsCallback(gnssCallback)
  }
}