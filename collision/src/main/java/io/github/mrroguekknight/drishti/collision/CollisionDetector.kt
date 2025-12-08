package io.github.mrroguekknight.drishti.collision

/**
 * A simple 2D vector class.
 */
data class Vector2D(val x: Float, val y: Float) {
    operator fun minus(other: Vector2D): Vector2D = Vector2D(this.x - other.x, this.y - other.y)
    fun dot(other: Vector2D): Float = this.x * other.x + this.y * other.y
}

/**
 * Represents the state (position and velocity) of an object.
 */
data class ObjectState(val position: Vector2D, val velocity: Vector2D)

/**
 * Calculates Time-to-Collision (TTC) and predicted separation.
 */
class CollisionDetector(private val ttcThreshold: Float, private val safetyMargin: Float) {

    /**
     * Calculates the Time-to-Collision (TTC) between two objects.
     * @return The TTC in seconds, or null if the objects are not on a collision course.
     */
    fun calculateTtc(egoState: ObjectState, obstacleState: ObjectState): Float? {
        val relPos = obstacleState.position - egoState.position
        val relVel = obstacleState.velocity - egoState.velocity

        val relVelDotProduct = relVel.dot(relVel)

        // Avoid division by zero if there's no relative velocity.
        if (relVelDotProduct < 0.0001f) {
            return null
        }

        val relPosDotVel = relPos.dot(relVel)

        // If the dot product is non-negative, the objects are moving apart or parallel, not converging.
        if (relPosDotVel >= 0) {
            return null
        }

        return -relPosDotVel / relVelDotProduct
    }
    
    /**
     * Predicts the minimum separation distance between two objects.
     * For simplicity, this currently returns the current distance.
     * A more advanced implementation would project positions forward.
     */
    fun getSeparation(egoState: ObjectState, obstacleState: ObjectState): Float {
        val relPos = obstacleState.position - egoState.position
        return kotlin.math.sqrt(relPos.dot(relPos))
    }

    /**
     * Checks for a potential collision based on TTC and safety margins.
     * @return True if an alert should be raised, false otherwise.
     */
    fun shouldAlert(egoState: ObjectState, obstacleState: ObjectState): Boolean {
        val ttc = calculateTtc(egoState, obstacleState)
        if (ttc != null && ttc < ttcThreshold) {
            return true
        }

        if (getSeparation(egoState, obstacleState) < safetyMargin) {
            return true
        }

        return false
    }
}