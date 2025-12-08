package io.github.mrroguekknight.drishti.fusion.models

/**
 * Represents the state vector for the EKF.
 *
 * The state vector typically includes:
 * - Position (px, py, pz)
 * - Velocity (vx, vy, vz)
 * - Orientation (e.g., yaw)
 * - Sensor biases (accelerometer, gyroscope)
 *
 * This data class also holds the covariance matrix `P`.
 */
data class State(
    // State Vector components
    val px: Double,
    val py: Double,
    val pz: Double,
    val vx: Double,
    val vy: Double,
    val vz: Double,
    val yaw: Double, // Or full quaternion for 3D orientation
    val biasAx: Double,
    val biasAy: Double,
    val biasAz: Double,
    val biasGx: Double,
    val biasGy: Double,
    val biasGz: Double,

    // Covariance matrix for the state estimate (P)
    // For a 13x13 state, this would be a 13x13 matrix, flattened here.
    val covariance: DoubleArray,
    
    val timestamp: Long
) {
    // Add equals and hashCode because of DoubleArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as State

        if (px != other.px) return false
        if (py != other.py) return false
        if (pz != other.pz) return false
        if (vx != other.vx) return false
        if (vy != other.vy) return false
        if (vz != other.vz) return false
        if (yaw != other.yaw) return false
        if (biasAx != other.biasAx) return false
        if (biasAy != other.biasAy) return false
        if (biasAz != other.biasAz) return false
        if (biasGx != other.biasGx) return false
        if (biasGy != other.biasGy) return false
        if (biasGz != other.biasGz) return false
        if (!covariance.contentEquals(other.covariance)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = px.hashCode()
        result = 31 * result + py.hashCode()
        result = 31 * result + pz.hashCode()
        result = 31 * result + vx.hashCode()
        result = 31 * result + vy.hashCode()
        result = 31 * result + vz.hashCode()
        result = 31 * result + yaw.hashCode()
        result = 31 * result + biasAx.hashCode()
        result = 31 * result + biasAy.hashCode()
        result = 31 * result + biasAz.hashCode()
        result = 31 * result + biasGx.hashCode()
        result = 31 * result + biasGy.hashCode()
        result = 31 * result + biasGz.hashCode()
        result = 31 * result + covariance.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
