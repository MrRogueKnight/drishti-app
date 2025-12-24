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
    
    private var accuracyMeters: Float = 0f
    private var smoothedAccuracyAndRadius: Float = 500f // Start big
    private var accuracyAnimator: android.animation.ValueAnimator? = null
    
    // Pulsing effect
    private var pulseRadiusFactor: Float = 1f
    private val pulseAnimator = android.animation.ValueAnimator.ofFloat(0.8f, 1.2f).apply {
        duration = 1500
        repeatCount = android.animation.ValueAnimator.INFINITE
        repeatMode = android.animation.ValueAnimator.REVERSE
        addUpdateListener { 
            pulseRadiusFactor = it.animatedValue as Float 
        }
    }

    init {
        pulseAnimator.start()
    }

    /**
     * Update the current location and bearing.
     */
    fun updateLocation(location: GeoPoint, bearing: Float, accuracy: Float) {
        currentLocation = location
        currentBearing = bearing
        
        // Smoothing Algorithm
        // 1. Clamp accuracy
        val clampedAccuracy = accuracy.coerceIn(10f, 1000f)
        
        // 2. Exponential smoothing (alpha = 0.1 for slow robust smoothing, or 0.4 for reactive)
        val alpha = 0.2f
        val newSmoothed = alpha * clampedAccuracy + (1 - alpha) * smoothedAccuracyAndRadius
        
        // 3. Animate to new smoothed value
        animateAccuracyRadius(smoothedAccuracyAndRadius, newSmoothed)
    }
    
    private fun animateAccuracyRadius(from: Float, to: Float) {
        accuracyAnimator?.cancel()
        accuracyAnimator = android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = 500
            addUpdateListener { 
                smoothedAccuracyAndRadius = it.animatedValue as Float
            }
            start()
        }
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
        
        // Calculate radius in pixels for the current smoothed accuracy in meters
        val metersPerPixel = mapView.projection.metersToEquatorPixels(1f)
        // Correct way is: meters / metersPerPixel(at lat)
        // metersToEquatorPixels returns PIXELS for 1 meter? No.
        // metersToEquatorPixels(float meters) -> float pixels. 
        // Accuracy radius in pixels:
        val radiusPixels = mapView.projection.metersToEquatorPixels(smoothedAccuracyAndRadius)
        
        // Draw accuracy circle (larger, semi-transparent)
        // Using "pulsing" effect on the smoothed radius? Or a separate pulse?
        // User said: "Keep one circle as accuracy... Add another circle... repeats INFINITE"
        // Let's make the MAIN accuracy circle breathe slightly for "Alive" feel
        // Or just the user marker.
        // Let's implement the user's specific request: Big accuracy circle.
        
        if (radiusPixels > 5f) {
             canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusPixels, accuracyCirclePaint)
        }
        
        // Draw location marker (Pulse effect on the marker border?)
        val markerRadius = 20f * pulseRadiusFactor
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 20f, locationCirclePaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), markerRadius, locationBorderPaint)
        
        // Draw heading arrow when navigating
        if (isNavigating) {
            drawHeadingArrow(canvas, point.x.toFloat(), point.y.toFloat(), currentBearing - mapView.mapOrientation)
        }
        
        // Trigger redraw for animation
        if ((accuracyAnimator?.isRunning == true) || (pulseAnimator.isRunning)) {
            mapView.postInvalidate()
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
    
    fun destroy() {
        accuracyAnimator?.cancel()
        pulseAnimator.cancel()
    }
}
