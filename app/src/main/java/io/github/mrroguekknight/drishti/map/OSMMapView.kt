package io.github.mrroguekknight.drishti.map

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Jetpack Compose wrapper for OSMDroid MapView with OpenStreetMap tiles.
 * Supports navigation features including auto-zoom, bearing rotation, and route display.
 * 
 * @param modifier Compose modifier
 * @param currentLocation Current GPS location to center map on
 * @param driveMode Enable navigation-style drive mode with rotation and auto-zoom
 * @param bearing Current heading/bearing in degrees (0-360, where 0 is north)
 * @param speed Current speed in m/s for auto-zoom calculation
 * @param routeWaypoints List of waypoints for route display
 * @param mapPadding Padding in pixels [left, top, right, bottom] to shift map center/copyright
 * @param onMapReady Callback when map is initialized
 * @param onMapControllerReady Callback when MapController is ready
 */
@Composable
fun OSMMapView(
    modifier: Modifier = Modifier,
    currentLocation: GeoPoint? = null,
    driveMode: Boolean = false,
    bearing: Float = 0f,
    speed: Float = 0f,
    routeWaypoints: List<GeoPoint> = emptyList(),
    mapPadding: PaddingValues = PaddingValues(0.dp),
    onMapReady: (MapView) -> Unit = {},
    onMapControllerReady: (MapController) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Initialize OSMDroid configuration
    LaunchedEffect(Unit) {
        android.util.Log.d("OSMMapView", "Configuring OSMDroid")
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            // Use internal storage to avoid permission issues
            osmdroidBasePath = context.filesDir
            osmdroidTileCache = java.io.File(context.filesDir, "osmdroid/tiles")
            
            // Optimization: Disk cache is safe (100MB), but let memory cache be auto-calculated
            tileFileSystemCacheMaxBytes = 100L * 1024 * 1024 
            
            // Memory Optimization: Use RGB_565 to reduce RAM usage by 50% per tile (good for low-end devices)
            // Note: tilePixelConfig not available in this version, skipping.
            
        }
        android.util.Log.d("OSMMapView", "OSMDroid configured: basePath=${context.filesDir}, RGB_565")
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var mapController by remember { mutableStateOf<MapController?>(null) }
    var navigationOverlay by remember { mutableStateOf<NavigationOverlay?>(null) }


    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.util.Log.d("OSMMapView", "Initializing MapView with OpenStreetMap tiles")
            MapView(ctx).apply {
                try {
                    // Optimization: HW Acceleration
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    // Set OpenStreetMap tile source
                    setTileSource(TileSourceFactory.MAPNIK)
                    android.util.Log.d("OSMMapView", "OpenStreetMap Mapnik tile source set")
                    
                    // Enable multi-touch controls
                    setMultiTouchControls(true)
                    
                    // Disable built-in zoom controls
                    setBuiltInZoomControls(false)
                    
                    // Set initial position (India center)
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(20.5937, 78.9629))
                    android.util.Log.d("OSMMapView", "Map centered at India (20.5937, 78.9629) with zoom 6")
                    
                    // Add location overlay
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    locationOverlay.enableMyLocation()
                    locationOverlay.enableFollowLocation()
                    overlays.add(locationOverlay)
                    android.util.Log.d("OSMMapView", "Location overlay added")
                    
                    // Add navigation overlay for custom location marker with heading
                    val navOverlay = NavigationOverlay()
                    overlays.add(navOverlay)
                    navigationOverlay = navOverlay
                    android.util.Log.d("OSMMapView", "Navigation overlay added")
                    
                    // Add rotation gesture overlay for two-finger rotation
                    val rotationOverlay = org.osmdroid.views.overlay.gestures.RotationGestureOverlay(this)
                    rotationOverlay.isEnabled = true
                    overlays.add(rotationOverlay)
                    android.util.Log.d("OSMMapView", "Rotation gesture overlay added")
                    
                    // Add compass overlay
                    val compassOverlay = org.osmdroid.views.overlay.compass.CompassOverlay(ctx, this)
                    compassOverlay.enableCompass()
                    overlays.add(compassOverlay)
                    android.util.Log.d("OSMMapView", "Compass overlay added")
                    
                    // Set min/max zoom
                    minZoomLevel = 5.0
                    maxZoomLevel = 19.0
                    
                    // Enable tile scaling
                    setTilesScaledToDpi(true)
                    
                    // CRITICAL: Call onResume to start tile loading!
                    onResume()
                    android.util.Log.d("OSMMapView", "MapView.onResume() called - tiles should start loading")
                    
                    // Create MapController
                    val controller = MapController(this)
                    mapController = controller
                    onMapControllerReady(controller)
                    
                    mapViewInstance = this
                    onMapReady(this)
                    android.util.Log.d("OSMMapView", "MapView initialization complete")
                } catch (e: Exception) {
                    android.util.Log.e("OSMMapView", "Error initializing map", e)
                }
            }
        },
        update = { mapView ->
            // Update Padding
            val leftPx = with(density) { mapPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr).roundToPx() }
            val topPx = with(density) { mapPadding.calculateTopPadding().roundToPx() }
            val rightPx = with(density) { mapPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr).roundToPx() }
            val bottomPx = with(density) { mapPadding.calculateBottomPadding().roundToPx() }
            
            // MapView padding shifts the logical center and UI elements (copyright, etc)
            if (mapView.paddingTop != topPx || mapView.paddingBottom != bottomPx) {
               mapView.setPadding(leftPx, topPx, rightPx, bottomPx)
            }

            // Update MapController with drive mode, bearing, and speed
            mapController?.let { controller ->
                // Update drive mode
                controller.setDriveMode(driveMode, bearing, speed)
                
                // Update bearing if in drive mode
                if (driveMode) {
                    controller.updateBearing(bearing)
                    controller.updateSpeed(speed)
                }
                
                // Update route if waypoints changed
                if (routeWaypoints.isNotEmpty()) {
                    controller.addRoute(routeWaypoints)
                } else {
                    controller.clearRoute()
                }
            }
            
            // Update navigation overlay
            currentLocation?.let { location ->
                navigationOverlay?.updateLocation(location, bearing)
                navigationOverlay?.setNavigating(driveMode)
                
                // Center on location if in drive mode
                if (driveMode) {
                    mapController?.centerOnLocation(location, animate = true)
                }
            }
            
            mapView.invalidate()
        }
    )
    
    // Handle lifecycle
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("OSMMapView", "Disposing MapView - calling onPause()")
            mapController?.dispose()
            mapViewInstance?.onPause()
        }
    }
}

/**
 * Extension function to create GeoPoint from lat/lon
 */
fun createGeoPoint(latitude: Double, longitude: Double): GeoPoint {
    return GeoPoint(latitude, longitude)
}
