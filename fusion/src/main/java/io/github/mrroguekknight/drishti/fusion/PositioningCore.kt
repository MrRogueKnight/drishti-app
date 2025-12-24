package io.github.mrroguekknight.drishti.fusion

import android.content.Context
import android.location.GnssStatus
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object PositioningCore {

    private const val TAG = "PositioningCore"

    // Dependencies
    private var gnssDataProvider: GnssDataProvider? = null
    private var sensorDataProvider: SensorDataProvider? = null
    private var dgpsManager: DgpsCorrectionManager = DgpsCorrectionManager()

    // Core Engine
    private val kalmanFilter = KalmanFilter2D()
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // State
    private var isRunning = false

    // Output Flow
    private val _filteredLocation = MutableStateFlow<KalmanFilter2D.FilteredState?>(null)
    val filteredLocation: StateFlow<KalmanFilter2D.FilteredState?> = _filteredLocation.asStateFlow()

    // Configuration
    private const val SNR_THRESHOLD_DB = 15.0f

    fun initialize(context: Context) {
        if (gnssDataProvider == null) {
            gnssDataProvider = GnssDataProvider(context)
        }
        if (sensorDataProvider == null) {
            sensorDataProvider = SensorDataProvider(context)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        Log.i(TAG, "Starting Positioning Core")

        // 1. Start Sensor Provider (IMU) - DISABLED per user request (Only GPS)
        // sensorDataProvider?.start()

        // 2. Start GNSS Provider
        gnssDataProvider?.start(
            onGnssMeasurement = { event ->
                // Future: Use raw measurements here if needed
            },
            onLocationUpdate = { result ->
                result.lastLocation?.let { location ->
                    val lat = location.latitude
                    val lon = location.longitude
                    val acc = location.accuracy
                    val time = location.time

                    if (!kalmanFilter.isInitialized()) {
                        Log.i(TAG, "Initializing KF at $lat, $lon")
                        kalmanFilter.initialize(lat, lon, time)
                    } else {
                        // PREDICT Step (Constant Velocity, no Accel)
                        // Using GPS timestamp to determine dt
                        // We ignore acceleration (0.0) as strictly requested ("forget accelerometer")
                        kalmanFilter.predict(time, 0.0, 0.0)
                        
                        // UPDATE Step
                        kalmanFilter.update(lat, lon, acc)
                    }

                    // Emit result immediately after update
                    val state = kalmanFilter.getState()
                    _filteredLocation.value = state
                }
            }
        )

        // 3. Coroutine for IMU Prediction - REMOVED
        // We now drive the KF strictly via GPS updates in step 2.
        
        // 4. GNSS Metrics / ZUPT Logic
        scope.launch {
            gnssDataProvider?.gnssMetrics?.collectLatest { metrics ->
                // "Step 3 (Refined) - ZUPT and Adaptive Noise"
                
                // If stationary (detected via Doppler or other sensors), force velocity to zero
                if (metrics.isLikelyStationary) {
                    // ZUPT: Zero Velocity Update
                    kalmanFilter.forceStop()
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        gnssDataProvider?.stop()
        sensorDataProvider?.stop()
        Log.i(TAG, "Stopped Positioning Core")
    }
}
