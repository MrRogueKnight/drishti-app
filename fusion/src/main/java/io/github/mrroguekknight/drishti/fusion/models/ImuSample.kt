package io.github.mrroguekknight.drishti.fusion.models

data class ImuSample(
    val accX: Double,
    val accY: Double,
    val accZ: Double,
    val gyroX: Double,
    val gyroY: Double,
    val gyroZ: Double,
    val worldAccX: Double = 0.0,
    val worldAccY: Double = 0.0,
    val worldAccZ: Double = 0.0,
    val timestamp: Long
)
