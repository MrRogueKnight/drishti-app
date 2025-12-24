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
    private val linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var sensorListener: SensorEventListener? = null
    private var sensorThread: HandlerThread? = null

    // Store the last known sensor data
    private var lastAccelData: FloatArray? = null
    private var lastGyroData: FloatArray? = null
    private var lastMagData: FloatArray? = null
    private var lastLinearAccelData: FloatArray? = null
    private var lastRotationVectorData: FloatArray? = null
    
    // Rotated World Acceleration
    private var worldAccel = FloatArray(3)

    private var lastAccelTimestamp: Long = 0

    private val _imuUpdates = MutableSharedFlow<ImuSample>(replay = 1, extraBufferCapacity = 64)
    val imuUpdates: SharedFlow<ImuSample> = _imuUpdates.asSharedFlow()
    
    // Bearing (compass heading) in degrees (0-360, where 0 is north)
    private val _bearingUpdates = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val bearingUpdates: kotlinx.coroutines.flow.StateFlow<Float> = _bearingUpdates
    
    // Rotation matrices
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val tempRotationMatrix = FloatArray(16) // 4x4 for Rotation Vector support


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
                            updateBearing()
                        }
                        Sensor.TYPE_LINEAR_ACCELERATION -> {
                            lastLinearAccelData = it.values.clone()
                            computeWorldAcceleration()
                        }
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            lastRotationVectorData = it.values.clone()
                            computeWorldAcceleration()
                        }
                        Sensor.TYPE_GYROSCOPE -> {
                            lastGyroData = it.values.clone()

                            // Emit IMU Sample
                            // We prefer Linear Acceleration for worldAcc fields
                            // Fallback to regular Accel if Linear not available (though less useful for prediction)
                            val lx = lastLinearAccelData?.get(0)?.toDouble() ?: 0.0
                            val ly = lastLinearAccelData?.get(1)?.toDouble() ?: 0.0
                            val lz = lastLinearAccelData?.get(2)?.toDouble() ?: 0.0
                            
                            val wx = worldAccel[0].toDouble()
                            val wy = worldAccel[1].toDouble()
                            val wz = worldAccel[2].toDouble()

                            val accToUse = lastAccelData ?: floatArrayOf(0f, 0f, 0f)

                            val sample = ImuSample(
                                accX = accToUse[0].toDouble(),
                                accY = accToUse[1].toDouble(),
                                accZ = accToUse[2].toDouble(),
                                gyroX = lastGyroData!![0].toDouble(),
                                gyroY = lastGyroData!![1].toDouble(),
                                gyroZ = lastGyroData!![2].toDouble(),
                                worldAccX = wx,
                                worldAccY = wy,
                                worldAccZ = wz,
                                timestamp = timestamp
                            )
                            _imuUpdates.tryEmit(sample)
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            lastMagData = it.values.clone()
                            updateBearing()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Handle accuracy changes if needed
            }
        }

        // Register Sensors
        accelerometer?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        gyroscope?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        magnetometer?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        linearAcceleration?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        rotationVector?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        
        if (accelerometer == null) android.util.Log.w("SensorDataProvider", "Accelerometer not found")
        if (gyroscope == null) android.util.Log.w("SensorDataProvider", "Gyroscope not found")
    }

    private fun computeWorldAcceleration() {
        val linAcc = lastLinearAccelData
        val rotVec = lastRotationVectorData
        
        if (linAcc != null && rotVec != null) {
            // Convert Rotation Vector to Rotation Matrix
            SensorManager.getRotationMatrixFromVector(tempRotationMatrix, rotVec)
            
            // Transform Device Linear Acceleration to World Frame
            // World = R * Device
            // R (from getRotationMatrixFromVector) transforms from Device to World.
            // Wait, Android docs say: "It transforms a vector from the device coordinate system to the world coordinate system."
            // So: V_world = R * V_device
            
            // tempRotationMatrix is 4x4 or 3x3 depending on implementation, but usually flat 9 or 16.
            // getRotationMatrixFromVector returns 4x4 in a float[16].
            
            val r = tempRotationMatrix
            // x_world = R[0]*x + R[1]*y + R[2]*z
            // y_world = R[4]*x + R[5]*y + R[6]*z
            // z_world = R[8]*x + R[9]*y + R[10]*z
            
            val x = linAcc[0]
            val y = linAcc[1]
            val z = linAcc[2]
            
            worldAccel[0] = r[0] * x + r[1] * y + r[2] * z
            worldAccel[1] = r[4] * x + r[5] * y + r[6] * z
            worldAccel[2] = r[8] * x + r[9] * y + r[10] * z
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