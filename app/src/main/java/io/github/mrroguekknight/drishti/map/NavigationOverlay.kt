package io.github.mrroguekknight.drishti.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Custom overlay for navigation elements including location marker with heading indicator.
 */
class NavigationOverlay : Overlay() {
    
    private var currentLocation: GeoPoint? = null
    private var currentBearing: Float = 0f
    private var isNavigating: Boolean = false
    
    // Paint objects for drawing
    private val locationCirclePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#4285F4") // Google blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val locationBorderPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    
    private val headingArrowPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#4285F4")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val accuracyCirclePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#4285F4")
        alpha = 50 // Semi-transparent
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    /**
     * Update the current location and bearing.
     */
    fun updateLocation(location: GeoPoint, bearing: Float) {
        currentLocation = location
        currentBearing = bearing
    }
    
    /**
     * Set navigation mode.
     */
    fun setNavigating(navigating: Boolean) {
        isNavigating = navigating
    }
    
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        
        val location = currentLocation ?: return
        
        // Convert GeoPoint to screen coordinates
        val point = mapView.projection.toPixels(location, null)
        
        // Draw accuracy circle (larger, semi-transparent)
        if (isNavigating) {
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 60f, accuracyCirclePaint)
        }
        
        // Draw location marker
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 20f, locationCirclePaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 20f, locationBorderPaint)
        
        // Draw heading arrow when navigating
        if (isNavigating) {
            drawHeadingArrow(canvas, point.x.toFloat(), point.y.toFloat(), currentBearing - mapView.mapOrientation)
        }
    }
    
    private fun drawHeadingArrow(canvas: Canvas, x: Float, y: Float, bearing: Float) {
        canvas.save()
        canvas.rotate(bearing, x, y)
        
        // Create arrow path
        val path = Path().apply {
            moveTo(x, y - 35f) // Arrow tip
            lineTo(x - 12f, y - 10f) // Left wing
            lineTo(x, y - 15f) // Center notch
            lineTo(x + 12f, y - 10f) // Right wing
            close()
        }
        
        canvas.drawPath(path, headingArrowPaint)
        canvas.restore()
    }
}
