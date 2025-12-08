package io.github.mrroguekknight.drishti.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemInitializationManager(private val context: Context) {

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow("Starting initialization...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    suspend fun startInitialization() {
        _progress.value = 0f
        
        // 1. Check Permissions
        checkPermissions()
        
        // 2. Check Sensors
        checkSensors()
        
        // 3. Check GPS
        checkGps()
        
        // 4. Finalize
        _statusMessage.value = "Initialization Complete"
        _progress.value = 1f
        delay(500) // Short delay to let user see 100%
    }

    private suspend fun checkPermissions() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION to "Location",
            Manifest.permission.CAMERA to "Camera",
            Manifest.permission.BLUETOOTH to "Bluetooth" // Basic check, might need more granular for API 31+
        )

        val totalPermissions = permissions.size
        permissions.forEachIndexed { index, (permission, name) ->
            _statusMessage.value = "Checking $name permissions..."
            delay(300) // Artificial delay for UX
            
            val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                // In a real app, we might request permissions here or flag missing ones.
                // For splash screen, we just report status. 
                // We assume permissions are requested elsewhere or we just proceed.
                // But for "functional" loading, we acknowledge it.
            }
            
            // Update progress: 0% -> 30% range for permissions
            val stepProgress = 0.3f * ((index + 1).toFloat() / totalPermissions)
            _progress.value = stepProgress
        }
    }

    private suspend fun checkSensors() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorsToCheck = listOf(
            Sensor.TYPE_ACCELEROMETER to "Accelerometer",
            Sensor.TYPE_GYROSCOPE to "Gyroscope"
        )

        val startProgress = 0.3f
        val endProgress = 0.7f
        val totalSensors = sensorsToCheck.size

        sensorsToCheck.forEachIndexed { index, (type, name) ->
            _statusMessage.value = "Initializing $name..."
            delay(400) // Artificial delay simulating initialization/warmup

            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor == null) {
                 _statusMessage.value = "$name not found!"
                 delay(500)
            }

            // Update progress: 30% -> 70%
            val stepProgress = startProgress + (endProgress - startProgress) * ((index + 1).toFloat() / totalSensors)
            _progress.value = stepProgress
        }
    }

    private suspend fun checkGps() {
        _statusMessage.value = "Checking GPS status..."
        delay(500)
        
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        
        if (!isGpsEnabled) {
             _statusMessage.value = "GPS is disabled!"
             delay(500)
        }

        // Update progress: 70% -> 100%
        _progress.value = 0.9f // Jump to 90%
        delay(200)
    }
}
