package io.github.mrroguekknight.drishti.fusion.models

/**
 * Represents a generic measurement from a sensor used for the EKF update step.
 * This could be from GNSS, Wi-Fi RTT, Vision, etc.
 */
sealed interface Measurement {
    val timestamp: Long

    /**
     * A position measurement, typically from GNSS.
     * In a real system, you would convert Lat/Lon to a local coordinate frame (e.g., ENU).
     */
    data class Position(
        val x: Double,
        val y: Double,
        val z: Double,
        val covariance: DoubleArray, // Represents the measurement noise matrix R
        override val timestamp: Long
    ) : Measurement {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Position

            if (x != other.x) return false
            if (y != other.y) return false
            if (z != other.z) return false
            if (!covariance.contentEquals(other.covariance)) return false
            if (timestamp != other.timestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = x.hashCode()
            result = 31 * result + y.hashCode()
            result = 31 * result + z.hashCode()
            result = 31 * result + covariance.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
}
