package io.github.mrroguekknight.drishti.fusion.models

data class ImuSample(
    val accX: Double,
    val accY: Double,
    val accZ: Double,
    val gyroX: Double,
    val gyroY: Double,
    val gyroZ: Double,
    val timestamp: Long
)
