package io.github.mrroguekknight.drishti.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import io.github.mrroguekknight.drishti.fusion.FusionRepositoryImpl
import io.github.mrroguekknight.drishti.fusion.Position
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MapViewFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var userMarker: Marker? = null
    private lateinit var viewModel: MapViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Note: A proper ViewModel factory should be used for dependency injection.
        val repository = FusionRepositoryImpl(Position(0.0, 0.0))
        viewModel = ViewModelProvider(this, MapViewModelFactory(repository))[MapViewModel::class.java]

        val view = inflater.inflate(R.layout.fragment_map_view, container, false)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return view
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        observePositionUpdates()
    }

    private fun observePositionUpdates() {
        viewModel.currentPosition.onEach { position ->
            updateMap(position)
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun updateMap(position: Position) {
        val latLng = LatLng(position.latitude, position.longitude)
        if (userMarker == null) {
            userMarker = map.addMarker(MarkerOptions().position(latLng).title("You are here"))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        } else {
            userMarker?.position = latLng
        }
    }
}
