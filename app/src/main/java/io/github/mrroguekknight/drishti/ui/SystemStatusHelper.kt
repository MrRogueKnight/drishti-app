package io.github.mrroguekknight.drishti.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SystemStatusHelper(private val context: Context) {

    data class SystemStatus(
        val batteryLevel: Int = 0,
        val isCharging: Boolean = false,
        val memoryUsagePercent: Int = 0,
        val availableMemoryMb: Long = 0,
        val totalMemoryMb: Long = 0
    )

    fun getSystemStatusFlow(): Flow<SystemStatus> = flow {
        while (true) {
            emit(getSystemStatus())
            delay(2000) // Update every 2 seconds
        }
    }

    private fun getSystemStatus(): SystemStatus {
        // Battery
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Memory
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalMem = memInfo.totalMem / (1024 * 1024)
        val availMem = memInfo.availMem / (1024 * 1024)
        val usedMem = totalMem - availMem
        val memPct = if (totalMem > 0) ((usedMem.toFloat() / totalMem.toFloat()) * 100).toInt() else 0

        return SystemStatus(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            memoryUsagePercent = memPct,
            availableMemoryMb = availMem,
            totalMemoryMb = totalMem
        )
    }
}
