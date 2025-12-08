package io.github.mrroguekknight.drishti.ui

import android.content.Context
import io.github.mrroguekknight.drishti.fusion.DataLogger
import io.github.mrroguekknight.drishti.fusion.LocationDataProvider
import io.github.mrroguekknight.drishti.fusion.SensorDataProvider
import io.github.mrroguekknight.drishti.network.NetworkStatusHelper

/**
 * Holds shared instances of sensor providers and helpers to be passed down the UI tree.
 * This ensures sensors are initialized once and kept alive.
 */
data class SensorContainer(
    val locationProvider: LocationDataProvider,
    val sensorProvider: SensorDataProvider,
    val networkHelper: NetworkStatusHelper,
    val systemHelper: SystemStatusHelper,
    val dataLogger: DataLogger
)
