package io.github.mrroguekknight.drishti.fusion

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A simple data class for position.
 */
data class Position(val latitude: Double, val longitude: Double)

/**
 * Repository for providing fused location data.
 */
interface FusionRepository {
    val currentPosition: StateFlow<Position>
}

/**
 * Placeholder implementation of the FusionRepository.
 */
class FusionRepositoryImpl(initialPosition: Position) : FusionRepository {
    override val currentPosition = MutableStateFlow(initialPosition)
}