package io.github.mrroguekknight.drishti.map

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

/**
 * Controller for advanced map features including auto-zoom, rotation, and navigation.
 * Provides Google Maps-like navigation experience with OSMDroid.
 */
class MapController(private val mapView: MapView) {
    
    private var isDriveMode = false
    private var currentBearing = 0f
    private var currentSpeed = 0f
    private var routePolyline: Polyline? = null
    
    // Animation for smooth transitions
    private var bearingAnimator: ValueAnimator? = null
    private var zoomAnimator: ValueAnimator? = null
    
    companion object {
        // Auto-zoom levels based on speed (km/h)
        private const val ZOOM_WALKING = 18.5  // 0-10 km/h
        private const val ZOOM_CITY = 17.0     // 10-30 km/h
        private const val ZOOM_SUBURBAN = 16.0 // 30-60 km/h
        private const val ZOOM_HIGHWAY = 15.0  // 60-100 km/h
        private const val ZOOM_FAST = 14.0     // 100+ km/h
        
        // Bearing change threshold to trigger rotation (degrees)
        private const val BEARING_THRESHOLD = 5f
        
        // Animation durations
        private const val BEARING_ANIMATION_DURATION = 300L
        private const val ZOOM_ANIMATION_DURATION = 500L
    }
    
    /**
     * Enable or disable drive mode with navigation-style view.
     * @param enabled True to enable drive mode
     * @param bearing Initial bearing in degrees (0-360)
     * @param speed Initial speed in m/s
     */
    fun setDriveMode(enabled: Boolean, bearing: Float = 0f, speed: Float = 0f) {
        isDriveMode = enabled
        
        if (enabled) {
            // Enable map rotation
            mapView.setMultiTouchControls(true)
            
            // Set initial bearing and zoom
            updateBearing(bearing, animate = false)
            updateSpeed(speed, animate = false)
            
            android.util.Log.d("MapController", "Drive mode enabled - bearing: $bearing°, speed: ${speed * 3.6} km/h")
        } else {
            // Reset to north-up view
            animateMapOrientation(0f)
            android.util.Log.d("MapController", "Drive mode disabled - resetting to north-up")
        }
    }
    
    /**
     * Update map bearing (rotation) based on device heading.
     * @param bearing Bearing in degrees (0-360, where 0 is north)
     * @param animate Whether to animate the rotation
     */
    fun updateBearing(bearing: Float, animate: Boolean = true) {
        if (!isDriveMode) return
        
        // Normalize bearing to 0-360
        val normalizedBearing = (bearing % 360 + 360) % 360
        
        // Only update if bearing changed significantly
        if (abs(normalizedBearing - currentBearing) > BEARING_THRESHOLD) {
            currentBearing = normalizedBearing
            
            if (animate) {
                animateMapOrientation(normalizedBearing)
            } else {
                mapView.mapOrientation = normalizedBearing
            }
            
            android.util.Log.d("MapController", "Bearing updated: $normalizedBearing°")
        }
    }
    
    /**
     * Update map zoom based on current speed.
     * @param speed Speed in m/s
     * @param animate Whether to animate the zoom
     */
    fun updateSpeed(speed: Float, animate: Boolean = true) {
        if (!isDriveMode) return
        
        currentSpeed = speed
        val speedKmh = speed * 3.6f
        
        // Calculate target zoom level based on speed
        val targetZoom = when {
            speedKmh < 10 -> ZOOM_WALKING
            speedKmh < 30 -> ZOOM_CITY
            speedKmh < 60 -> ZOOM_SUBURBAN
            speedKmh < 100 -> ZOOM_HIGHWAY
            else -> ZOOM_FAST
        }
        
        // Only update if zoom changed significantly
        if (abs(mapView.zoomLevelDouble - targetZoom) > 0.3) {
            if (animate) {
                animateZoom(targetZoom)
            } else {
                mapView.controller.setZoom(targetZoom)
            }
            
            android.util.Log.d("MapController", "Speed: ${speedKmh.toInt()} km/h -> Zoom: $targetZoom")
        }
    }
    
    /**
     * Center map on a specific location.
     * @param location GeoPoint to center on
     * @param animate Whether to animate the movement
     */
    fun centerOnLocation(location: GeoPoint, animate: Boolean = true) {
        if (animate) {
            mapView.controller.animateTo(location)
        } else {
            mapView.controller.setCenter(location)
        }
    }
    
    /**
     * Add a route to the map.
     * @param waypoints List of GeoPoints representing the route
     */
    fun addRoute(waypoints: List<GeoPoint>) {
        // Remove existing route
        routePolyline?.let { mapView.overlays.remove(it) }
        
        if (waypoints.size < 2) {
            android.util.Log.w("MapController", "Route requires at least 2 waypoints")
            return
        }
        
        // Create new route polyline
        routePolyline = Polyline().apply {
            setPoints(waypoints)
            outlinePaint.color = android.graphics.Color.parseColor("#4285F4") // Google blue
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        
        mapView.overlays.add(routePolyline)
        mapView.invalidate()
        
        android.util.Log.d("MapController", "Route added with ${waypoints.size} waypoints")
    }
    
    /**
     * Clear the current route from the map.
     */
    fun clearRoute() {
        routePolyline?.let {
            mapView.overlays.remove(it)
            routePolyline = null
            mapView.invalidate()
        }
    }
    
    /**
     * Reset map to north-up orientation.
     */
    fun resetToNorthUp() {
        animateMapOrientation(0f)
        currentBearing = 0f
        android.util.Log.d("MapController", "Map reset to north-up")
    }
    
    /**
     * Calculate distance between two points in meters.
     */
    fun calculateDistance(start: GeoPoint, end: GeoPoint): Double {
        return start.distanceToAsDouble(end)
    }
    
    /**
     * Calculate ETA based on distance and current speed.
     * @param distanceMeters Distance in meters
     * @return ETA in minutes, or null if speed is too low
     */
    fun calculateETA(distanceMeters: Double): Int? {
        if (currentSpeed < 0.5f) return null // Speed too low
        
        val timeSeconds = distanceMeters / currentSpeed
        return (timeSeconds / 60).toInt()
    }
    
    // Private helper methods
    
    private fun animateMapOrientation(targetBearing: Float) {
        bearingAnimator?.cancel()
        
        val currentOrientation = mapView.mapOrientation
        
        // Calculate shortest rotation path
        var delta = targetBearing - currentOrientation
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        
        bearingAnimator = ValueAnimator.ofFloat(currentOrientation, currentOrientation + delta).apply {
            duration = BEARING_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                mapView.mapOrientation = value
                mapView.invalidate()
            }
            start()
        }
    }
    
    private fun animateZoom(targetZoom: Double) {
        zoomAnimator?.cancel()
        
        val currentZoom = mapView.zoomLevelDouble
        
        zoomAnimator = ValueAnimator.ofFloat(currentZoom.toFloat(), targetZoom.toFloat()).apply {
            duration = ZOOM_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                mapView.controller.setZoom(value.toDouble())
            }
            start()
        }
    }
    
    /**
     * Clean up resources.
     */
    /**
     * Clean up resources.
     */
    fun dispose() {
        bearingAnimator?.cancel()
        zoomAnimator?.cancel()
        clearRoute()
        // Clear peer markers
        peerMarkers.values.forEach { mapView.overlays.remove(it) }
        peerMarkers.clear()
    }

    // --- PEER MARKER LOGIC ---
    private val peerMarkers = mutableMapOf<String, org.osmdroid.views.overlay.Marker>()
    
    fun updatePeerMarkers(peers: List<io.github.mrroguekknight.drishti.network.NetworkDataSync.PeerLocation>) {
        val currentIds = peers.map { it.id }.toSet()
        
        // 1. Remove markers for users who left
        val toRemove = peerMarkers.keys.filter { it !in currentIds }
        toRemove.forEach { id ->
            peerMarkers[id]?.let { marker ->
                mapView.overlays.remove(marker)
            }
            peerMarkers.remove(id)
        }
        
        // 2. Add or Update markers
        peers.forEach { peer ->
            val location = GeoPoint(peer.lat, peer.lon)
            
            if (peerMarkers.containsKey(peer.id)) {
                // Update existing
                val marker = peerMarkers[peer.id]!!
                // Animate to new position for smoothness (optional, just set for now)
                marker.position = location
                marker.snippet = "Speed: ${"%.1f".format(peer.speed * 3.6)} km/h"
                // Force redraw of info window if open
                if (marker.isInfoWindowShown) {
                    marker.showInfoWindow()
                }
            } else {
                // Create new
                val marker = org.osmdroid.views.overlay.Marker(mapView).apply {
                    position = location
                    title = "Peer: ${peer.id}"
                    snippet = "Speed: ${"%.1f".format(peer.speed * 3.6)} km/h"
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                    // Use a different icon or color if possible - default OSM marker is fine for now
                    // icon = ContextCompat.getDrawable(context, R.drawable.ic_peer_car) 
                }
                peerMarkers[peer.id] = marker
                mapView.overlays.add(marker)
            }
        }
        
        mapView.invalidate()
    }
}
