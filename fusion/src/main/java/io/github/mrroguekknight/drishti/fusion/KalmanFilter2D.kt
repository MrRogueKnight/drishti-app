package io.github.mrroguekknight.drishti.fusion

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A simple 2D Kalman Filter using a Constant Velocity (CV) model.
 * State vector: [x, y, vx, vy]
 * Units: Meters (East, North) and Meters/second.
 */
class KalmanFilter2D {

    // State vector: x, y, vx, vy
    private val X = DoubleArray(4)

    // Covariance matrix P (4x4)
    // Initial uncertainty: high for position, moderate for velocity
    private val P = arrayOf(
        doubleArrayOf(100.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 100.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, 25.0, 0.0),
        doubleArrayOf(0.0, 0.0, 0.0, 25.0)
    )

    // Process noise Q (4x4)
    // "Unholy Precision" Tuning: 
    // Trust the model highly (low sigma) to smooth out path.
    // Allow acceleration to break this trust (dynamic).
    private val processNoiseSigma = 0.05 
    
    // Measurement noise R (2x2)
    // Default to trusting GPS moderately (variance 4.0 = 2m std dev)
    // Will be updated dynamically.
    private var R = arrayOf(
        doubleArrayOf(4.0, 0.0), 
        doubleArrayOf(0.0, 4.0)
    )

    // Reference point for Local ENU conversion
    // We set this on the first valid GPS fix.
    var referenceLat: Double? = null
    var referenceLon: Double? = null

    // For calculating dt
    private var lastTimestamp: Long = 0

    fun isInitialized(): Boolean = referenceLat != null

    /**
     * Initialize the filter with the first GPS reading.
     */
    fun initialize(lat: Double, lon: Double, timestamp: Long) {
        referenceLat = lat
        referenceLon = lon
        lastTimestamp = timestamp

        // Initial state: x=0, y=0 (relative to self), vx=0, vy=0
        X[0] = 0.0
        X[1] = 0.0
        X[2] = 0.0
        X[3] = 0.0
    }

    /**
     * Prediction step: X = F*X
     * Adaptive: Adjust Process Noise Q based on dynamic measurements (acceleration).
     */
    fun predict(timestamp: Long, ax: Double = 0.0, ay: Double = 0.0) {
        if (!isInitialized()) return

        val dt = (timestamp - lastTimestamp) / 1000.0 // Convert ms to seconds
        if (dt <= 0) return
        lastTimestamp = timestamp

        // Adaptive Process Noise ("Exceptional Setting")
        // If high acceleration, trust controls/IMU -> Increase Q significantly to allow turn.
        // If low acceleration (cruising/stopped), Trust Model -> Decrease Q to lock position.
        val accelMag = sqrt(ax * ax + ay * ay)
        
        // Base sigma 0.05.
        // If accel is 1 m/s^2, noise becomes 0.05 + 0.5 = 0.55 (Reactive)
        // If accel is 0, noise is 0.05 (Locked)
        val dynamicProcessNoise = processNoiseSigma + (accelMag * 0.5)

        // F matrix:
        // 1 0 dt 0
        // 0 1 0 dt
        // 0 0 1  0
        // 0 0 0  1
        
        // Update State X
        // x = x + vx*dt + 0.5*ax*dt^2
        // y = y + vy*dt + 0.5*ay*dt^2
        // vx = vx + ax*dt
        // vy = vy + ay*dt
        
        val newX = X[0] + X[2] * dt + 0.5 * ax * dt * dt
        val newY = X[1] + X[3] * dt + 0.5 * ay * dt * dt
        val newVx = X[2] + ax * dt
        val newVy = X[3] + ay * dt

        X[0] = newX
        X[1] = newY
        X[2] = newVx
        X[3] = newVy

        // Update Covariance P = F*P*F^T + Q
        // Simplified Matrix multiplication for this specific F structure
        // This is a manual expansion for performance/simplicity without matrix lib
        
        // Temp P after F*P
        val fp = Array(4) { DoubleArray(4) }
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                // F[i][k] * P[k][j]
                // F is sparse, so we can unroll
                // row 0: P[0][j] + dt*P[2][j]
                // row 1: P[1][j] + dt*P[3][j]
                // row 2: P[2][j]
                // row 3: P[3][j]
                fp[i][j] = when(i) {
                    0 -> P[0][j] + dt * P[2][j]
                    1 -> P[1][j] + dt * P[3][j]
                    else -> P[i][j]
                }
            }
        }

        // Now (F*P) * F^T
        // F^T is:
        // 1 0 0 0
        // 0 1 0 0
        // dt 0 1 0
        // 0 dt 0 1
        
        val pNew = Array(4) { DoubleArray(4) }
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                // Multiply row i of FP by col j of F^T (which is row j of F)
                 pNew[i][j] = when(j) {
                    0 -> fp[i][0] + fp[i][2] * dt
                    1 -> fp[i][1] + fp[i][3] * dt
                    else -> fp[i][j]
                }
            }
        }
        
        // Add Q (Process Noise)
        // Simple distinct noise for pos and vel, scaled by dynamicProcessNoise
        val posNoise = 0.5 * dynamicProcessNoise * dt * dt
        val velNoise = dynamicProcessNoise * dt
        
        // We add noise primarily to the velocity diagonal, which propagates to position
        pNew[2][2] += velNoise * velNoise
        pNew[3][3] += velNoise * velNoise
        pNew[0][0] += posNoise * posNoise
        pNew[1][1] += posNoise * posNoise
        
        // Copy back to P
        for (i in 0 until 4) {
            System.arraycopy(pNew[i], 0, P[i], 0, 4)
        }
    }

    /**
     * Update step: Z = H*X + R
     */
    fun update(lat: Double, lon: Double, accuracy: Float) {
        if (!isInitialized()) {
            return
        }

        // 1. Convert Measurement to Local ENU
        val (zX, zY) = latLonToLocalENU(lat, lon, referenceLat!!, referenceLon!!)
        
        // 2. Set R based on accuracy
        // variance = accuracy^2
        val varPos = (accuracy * accuracy).toDouble().coerceAtLeast(1.0)
        R[0][0] = varPos
        R[1][1] = varPos

        // 3. Innovation y = Z - H*X
        // H is
        // 1 0 0 0
        // 0 1 0 0
        val yX = zX - X[0]
        val yY = zY - X[1]

        // 4. Innovation Covariance S = H*P*H^T + R
        // H*P is just the first two rows of P
        // (H*P)*H^T is just the top-left 2x2 block of P
        val S00 = P[0][0] + R[0][0]
        val S01 = P[0][1] + R[0][1] // R[0][1] is 0
        val S10 = P[1][0] + R[1][0] // R[1][0] is 0
        val S11 = P[1][1] + R[1][1]

        // 5. Kalman Gain K = P*H^T * S^-1
        // Matrix inversion of 2x2 S
        val det = S00 * S11 - S01 * S10
        if (det == 0.0) return // Should not happen with positive R

        val invS00 = S11 / det
        val invS01 = -S01 / det
        val invS10 = -S10 / det
        val invS11 = S00 / det

        // K = (P * H^T) * inv(S)
        // P * H^T is the first two columns of P
        val K = Array(4) { DoubleArray(2) }
        
        for (i in 0 until 4) {
            // Row i of (P*HT) is [P[i][0], P[i][1]]
            // Multiply by invS
            K[i][0] = P[i][0] * invS00 + P[i][1] * invS10
            K[i][1] = P[i][0] * invS01 + P[i][1] * invS11
        }

        // 6. Update State X = X + K*y
        X[0] = X[0] + (K[0][0] * yX + K[0][1] * yY)
        X[1] = X[1] + (K[1][0] * yX + K[1][1] * yY)
        X[2] = X[2] + (K[2][0] * yX + K[2][1] * yY)
        X[3] = X[3] + (K[3][0] * yX + K[3][1] * yY)

        // 7. Update Covariance P = (I - K*H) * P
        // I - KH
        // KH is 4x4.
        // Let's compute P_new = P - K*(H*P)
        // H*P is just top 2 rows of P
        val HP = Array(2) { DoubleArray(4) }
        for (j in 0 until 4) {
            HP[0][j] = P[0][j]
            HP[1][j] = P[1][j]
        }
        
        val KHP = Array(4) { DoubleArray(4) }
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                KHP[i][j] = K[i][0] * HP[0][j] + K[i][1] * HP[1][j]
            }
        }
        
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                P[i][j] = P[i][j] - KHP[i][j]
            }
        }
    }

    /**
     * ZUPT (Zero Velocity Update): Forces velocity to zero and reduces velocity covariance.
     * Used when we are confident the device is stationary (e.g. from Doppler or Accelerometer).
     */
    fun forceStop() {
        if (!isInitialized()) return

        // Force Velocity State to 0
        X[2] = 0.0
        X[3] = 0.0

        // Reduce trust in old velocity (set Variance low? or high?)
        // If we KNOW we are stopped, we have high confidence velocity is 0.
        // So we set Variance to be very small.
        P[2][2] = 0.1
        P[3][3] = 0.1
        
        // Zero out correlations with position to stop "drag"
        P[0][2] = 0.0; P[2][0] = 0.0
        P[0][3] = 0.0; P[3][0] = 0.0
        P[1][2] = 0.0; P[2][1] = 0.0
        P[1][3] = 0.0; P[3][1] = 0.0
    }

    fun getState(): FilteredState {
        val (lat, lon) = localENUToLatLon(X[0], X[1], referenceLat ?: 0.0, referenceLon ?: 0.0)
        val speed = sqrt(X[2] * X[2] + X[3] * X[3])
        // Simple bearing from velocity vector
        var bearing = Math.toDegrees(kotlin.math.atan2(X[2], X[3])) // atan2(x, y) for typical bearing (0=North) ?
        // Actually atan2(y, x) is math angle. Navigation bearing:
        // North = 0, East = 90.
        // ENU: x=East, y=North.
        // Bearing = atan2(East, North)
        bearing = Math.toDegrees(kotlin.math.atan2(X[0], X[1])) 
        // Wait, Velocity bearing
        val vBearing = if (speed > 0.1) Math.toDegrees(kotlin.math.atan2(X[2], X[3])) else 0.0
        
        // Normalize bearing 0-360
        val normBearing = (vBearing + 360) % 360

        return FilteredState(lat, lon, speed, normBearing.toFloat())
    }

    /**
     * Converts Lat/Lon to Local East-North-Up (ENU) coordinates in meters.
     * Approximates Earth as a sphere for short distances, or use WGS84 projection.
     * Using simple Equirectangular projection for small local areas is efficient.
     */
    private fun latLonToLocalENU(lat: Double, lon: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val rEarth = 6378137.0 // Meters
        val dLat = Math.toRadians(lat - refLat)
        val dLon = Math.toRadians(lon - refLon)
        val latRad = Math.toRadians(refLat)

        val x = rEarth * dLon * cos(latRad) // East
        val y = rEarth * dLat // North
        return Pair(x, y)
    }

    private fun localENUToLatLon(x: Double, y: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val rEarth = 6378137.0
        val latRad = Math.toRadians(refLat)

        val dLat = y / rEarth
        val dLon = x / (rEarth * cos(latRad))

        val newLat = refLat + Math.toDegrees(dLat)
        val newLon = refLon + Math.toDegrees(dLon)
        return Pair(newLat, newLon)
    }

    data class FilteredState(
        val latitude: Double,
        val longitude: Double,
        val speed: Double,
        val bearing: Float
    )
}
