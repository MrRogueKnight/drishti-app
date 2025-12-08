package io.github.mrroguekknight.drishti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mrroguekknight.drishti.fusion.FusionRepository
import io.github.mrroguekknight.drishti.fusion.Position
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MapViewModel(fusionRepository: FusionRepository) : ViewModel() {

    val currentPosition: StateFlow<Position> = fusionRepository.currentPosition
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Position(0.0, 0.0) // Default initial position
        )
}