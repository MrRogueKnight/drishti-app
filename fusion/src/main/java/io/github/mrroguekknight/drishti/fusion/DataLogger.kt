package io.github.mrroguekknight.drishti.fusion

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataLogger(private val context: Context) {

    private var fileWriter: FileWriter? = null
    private var isLogging = false
    private var currentFileStartTime: Long = 0
    private val ROTATION_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
    private val RETENTION_PERIOD_MS = 30 * 60 * 1000L // 30 minutes

    fun startLogging() {
        if (isLogging) return
        isLogging = true
        rotateFile()
        pruneLogs()
        Log.d("DataLogger", "Started auto-logging")
    }

    fun logData(
        timestamp: Long,
        speed: Float,
        lat: Double,
        lon: Double,
        accX: Double,
        accY: Double,
        accZ: Double,
        gyroX: Double,
        gyroY: Double,
        gyroZ: Double
    ) {
        if (!isLogging) return

        // Check for rotation
        if (System.currentTimeMillis() - currentFileStartTime > ROTATION_INTERVAL_MS) {
            rotateFile()
            pruneLogs()
        }

        if (fileWriter == null) return

        try {
            val line = "$timestamp,$speed,$lat,$lon,$accX,$accY,$accZ,$gyroX,$gyroY,$gyroZ\n"
            fileWriter?.append(line)
        } catch (e: IOException) {
            Log.e("DataLogger", "Error writing log", e)
        }
    }

    private fun rotateFile() {
        try {
            fileWriter?.flush()
            fileWriter?.close()
        } catch (e: IOException) {
            Log.e("DataLogger", "Error closing previous log file", e)
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "drishti_log_$timestamp.csv"
            val logDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs()
            }
            val file = File(logDir, fileName)
            
            fileWriter = FileWriter(file, true)
            fileWriter?.append("Timestamp,Speed(m/s),Lat,Lon,AccX,AccY,AccZ,GyroX,GyroY,GyroZ\n")
            fileWriter?.flush()
            
            currentFileStartTime = System.currentTimeMillis()
            Log.d("DataLogger", "Rotated to new log file: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e("DataLogger", "Error creating new log file", e)
            fileWriter = null
        }
    }

    private fun pruneLogs() {
        val logDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val files = logDir.listFiles { _, name -> name.startsWith("drishti_log_") && name.endsWith(".csv") } ?: return
        
        val currentTime = System.currentTimeMillis()
        var deletedCount = 0
        
        files.forEach { file ->
            if (currentTime - file.lastModified() > RETENTION_PERIOD_MS) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        if (deletedCount > 0) {
            Log.d("DataLogger", "Pruned $deletedCount old log files")
        }
    }

    fun stopLogging() {
        try {
            fileWriter?.flush()
            fileWriter?.close()
            fileWriter = null
            isLogging = false
            Log.d("DataLogger", "Stopped logging")
        } catch (e: IOException) {
            Log.e("DataLogger", "Error stopping logger", e)
        }
    }
}
