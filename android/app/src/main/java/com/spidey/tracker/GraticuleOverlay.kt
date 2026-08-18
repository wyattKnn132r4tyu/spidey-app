package com.spidey.tracker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The faint lat/long grid ruled across the map.
 *
 * Spacing steps with zoom so the lines stay roughly a screen-inch apart instead
 * of collapsing into a solid wash when you zoom out.
 */
class GraticuleOverlay : Overlay() {

    private val paint = Paint().apply {
        isAntiAlias = false
        color = Color.argb(52, 120, 190, 235)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    /** Degrees between lines, chosen by zoom level. */
    private fun spacingFor(zoom: Double): Double = when {
        zoom >= 15 -> 0.005
        zoom >= 13 -> 0.01
        zoom >= 11 -> 0.05
        zoom >= 9 -> 0.1
        zoom >= 7 -> 0.5
        zoom >= 5 -> 1.0
        zoom >= 3 -> 5.0
        else -> 10.0
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val box = mapView.boundingBox
        val step = spacingFor(mapView.zoomLevelDouble)
        val projection = mapView.projection
        val point = Point()

        // Guard against a degenerate viewport producing an unbounded loop.
        if (step <= 0) return

        var lat = floor(box.latSouth / step) * step
        val latEnd = ceil(box.latNorth / step) * step
        var guard = 0
        while (lat <= latEnd && guard < MAX_LINES) {
            projection.toPixels(GeoPoint(lat, box.lonWest), point)
            val y = point.y.toFloat()
            canvas.drawLine(0f, y, mapView.width.toFloat(), y, paint)
            lat += step
            guard++
        }

        var lon = floor(box.lonWest / step) * step
        val lonEnd = ceil(box.lonEast / step) * step
        guard = 0
        while (lon <= lonEnd && guard < MAX_LINES) {
            projection.toPixels(GeoPoint(box.latSouth, lon), point)
            val x = point.x.toFloat()
            canvas.drawLine(x, 0f, x, mapView.height.toFloat(), paint)
            lon += step
            guard++
        }
    }

    private companion object {
        /** Enough for any sane viewport; stops a bad bounding box hanging the draw. */
        const val MAX_LINES = 400
    }
}
