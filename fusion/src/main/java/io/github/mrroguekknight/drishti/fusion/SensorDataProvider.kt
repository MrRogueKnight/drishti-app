package io.github.mrroguekknight.drishti.fusion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.github.mrroguekknight.drishti.fusion.models.ImuSample
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages the collection of IMU data (accelerometer and gyroscope).
 */
class SensorDataProvider(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)


    private var sensorListener: SensorEventListener? = null
    private var sensorThread: HandlerThread? = null

    // Store the last known sensor data
    private var lastAccelData: FloatArray? = null
    private var lastGyroData: FloatArray? = null
    private var lastMagData: FloatArray? = null
    private var lastAccelTimestamp: Long = 0

    private val _imuUpdates = MutableSharedFlow<ImuSample>(replay = 1, extraBufferCapacity = 64)
    val imuUpdates: SharedFlow<ImuSample> = _imuUpdates.asSharedFlow()
    
    // Bearing (compass heading) in degrees (0-360, where 0 is north)
    private val _bearingUpdates = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val bearingUpdates: kotlinx.coroutines.flow.StateFlow<Float> = _bearingUpdates
    
    // Rotation matrices for bearing calculation
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)


    /**
     * Starts listening for sensor data on a background thread.
     */
    fun start() {
        if (sensorThread != null) return // Already running

        sensorThread = HandlerThread("SensorThread").apply { start() }
        val handler = Handler(sensorThread!!.looper)

        sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val timestamp = SystemClock.elapsedRealtimeNanos()
                    when (it.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            lastAccelData = it.values.clone()
                            lastAccelTimestamp = timestamp
                            
                            // Calculate bearing if we have magnetometer data
                            updateBearing()
                        }
                        Sensor.TYPE_GYROSCOPE -> {
                            lastGyroData = it.values.clone()

                            // Create a sample only when we have a recent accelerometer reading
                            // This is a simple synchronization strategy.
                            if (lastAccelData != null && (timestamp - lastAccelTimestamp) < 20_000_000L) { // 20ms
                                val sample = ImuSample(
                                    accX = lastAccelData!![0].toDouble(),
                                    accY = lastAccelData!![1].toDouble(),
                                    accZ = lastAccelData!![2].toDouble(),
                                    gyroX = lastGyroData!![0].toDouble(),
                                    gyroY = lastGyroData!![1].toDouble(),
                                    gyroZ = lastGyroData!![2].toDouble(),
                                    timestamp = timestamp
                                )
                                _imuUpdates.tryEmit(sample)
                            }
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            lastMagData = it.values.clone()
                            
                            // Calculate bearing if we have accelerometer data
                            updateBearing()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Handle accuracy changes if needed
            }
        }

        if (accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
        } else {
            android.util.Log.w("SensorDataProvider", "Accelerometer not found")
        }
        
        if (gyroscope != null) {
            sensorManager.registerListener(sensorListener, gyroscope, SensorManager.SENSOR_DELAY_GAME, handler)
        } else {
            android.util.Log.w("SensorDataProvider", "Gyroscope not found")
        }
        
        if (magnetometer != null) {
            sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_GAME, handler)
        } else {
            android.util.Log.w("SensorDataProvider", "Magnetometer not found")
        }
    }
    
    /**
     * Calculate bearing (compass heading) from accelerometer and magnetometer data.
     */
    private fun updateBearing() {
        val accel = lastAccelData
        val mag = lastMagData
        
        if (accel == null || mag == null) return
        
        // Get rotation matrix from accelerometer and magnetometer
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, accel, mag)
        
        if (success) {
            // Get orientation angles from rotation matrix
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            
            // orientationAngles[0] is azimuth (rotation around Z-axis) in radians
            // Convert to degrees and normalize to 0-360
            var azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            
            // Normalize to 0-360
            azimuthDegrees = (azimuthDegrees + 360) % 360
            
            // Apply simple low-pass filter to smooth jitter
            val alpha = 0.15f // Smoothing factor (lower = smoother but slower response)
            val currentBearing = _bearingUpdates.value
            val smoothedBearing = currentBearing + alpha * (azimuthDegrees - currentBearing)
            
            _bearingUpdates.value = (smoothedBearing + 360) % 360
        }
    }

    /**
     * Stops listening for sensor data.
     */
    fun stop() {
        sensorListener?.let { sensorManager.unregisterListener(it) }
        sensorThread?.quitSafely()
        sensorThread = null
        sensorListener = null
    }
}