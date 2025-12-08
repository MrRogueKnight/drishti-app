package io.github.mrroguekknight.drishti.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

// Simple Google Map Compose screen. Requires API key and location permissions at runtime.
// Replace marker positions with real peer vehicle coordinates from your P2P or backend.
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val singapore = LatLng(1.3521, 103.8198) // placeholder
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 15f)
    }
    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(cameraPositionState = cameraPositionState) {
            Marker(state = rememberMarkerState(position = singapore), title = "You")
        }
    }
}
