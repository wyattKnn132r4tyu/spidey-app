package com.spidey.tracker.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * Draws the pins and handles taps on them.
 *
 * Pins are pixel badges: a chunky dark-outlined disc with a sprite stamped in the
 * middle, drawn without antialiasing so the edges stay hard. Everything is
 * quantised to a pixel grid derived from the badge size, so it scales without
 * ever going smooth.
 *
 * One overlay rather than a marker each, so they can be drawn in a known order —
 * cold, warm, hot, then the selected one. In a dense cluster that puts the pin
 * you are reaching for on top, and it wins the tap.
 */
class SightingOverlay(
    private var sightings: List<SpideyCore.Sighting> = emptyList(),
    private var now: Long = System.currentTimeMillis(),
    private var selectedId: String? = null,
    private var myId: String? = null,
    private var onSelect: (String) -> Unit = {},
) : Overlay() {

    private val paint = Paint().apply { isAntiAlias = false }

    /** Screen positions from the last draw, used for hit testing. */
    private val hitBoxes = mutableListOf<Triple<String, Float, Float>>()
    private var density = 1f

    fun update(
        sightings: List<SpideyCore.Sighting>,
        now: Long,
        selectedId: String?,
        myId: String?,
        onSelect: (String) -> Unit,
    ) {
        this.sightings = sightings
        this.now = now
        this.selectedId = selectedId
        this.myId = myId
        this.onSelect = onSelect
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        density = mapView.context.resources.displayMetrics.density
        val projection = mapView.projection
        hitBoxes.clear()

        val point = Point()
        val badge = 15f * density

        // Cold first, hot last, selected on top.
        val ordered = sightings.sortedBy { sighting ->
            val rank = when (SpideyCore.heatOf(sighting, now)) {
                SpideyCore.Heat.COLD -> 0
                SpideyCore.Heat.WARM -> 1
                SpideyCore.Heat.HOT -> 2
            }
            if (sighting.id == selectedId) 3 else rank
        }

        for (sighting in ordered) {
            projection.toPixels(org.osmdroid.util.GeoPoint(sighting.lat, sighting.lng), point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()

            // Your own reports read as a different kind of mark: a star, in blue.
            val mine = myId != null && sighting.reporterHandleIsMine(myId!!)
            val (fill, outline) = when {
                mine -> Ink.pinBlue.toArgb() to Ink.pinBlueDark.toArgb()
                else -> when (SpideyCore.heatOf(sighting, now)) {
                    SpideyCore.Heat.HOT -> Ink.pinRed.toArgb() to Ink.pinRedDark.toArgb()
                    SpideyCore.Heat.WARM -> Ink.pinGreen.toArgb() to Ink.pinGreenDark.toArgb()
                    SpideyCore.Heat.COLD -> Ink.pinGrey.toArgb() to Ink.pinGreyDark.toArgb()
                }
            }

            drawBadge(canvas, x, y, badge, fill, outline, if (mine) STAR else SPIDER)

            if (sighting.id == selectedId) {
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                val ring = badge + 4f * density
                canvas.drawRect(x - ring, y - ring, x + ring, y + ring, paint)
                paint.style = Paint.Style.FILL
            }

            hitBoxes.add(Triple(sighting.id, x, y))
        }
    }

    /**
     * A pixel disc: rows of rectangles whose widths approximate a circle, so the
     * silhouette is round but the edges are steps rather than a smooth curve.
     */
    private fun drawBadge(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        fill: Int,
        outline: Int,
        sprite: Array<String>,
    ) {
        val unit = radius / 7f
        // Half-widths in pixel units for each of the 14 rows, top to bottom.
        val widths = intArrayOf(3, 5, 6, 6, 7, 7, 7, 7, 7, 7, 6, 6, 5, 3)

        paint.style = Paint.Style.FILL

        widths.forEachIndexed { row, half ->
            val top = cy - radius + row * unit
            val left = cx - half * unit
            val right = cx + half * unit

            paint.color = outline
            canvas.drawRect(left, top, right, top + unit, paint)
        }

        // Inset by one pixel unit for the fill, leaving the outline as a border.
        widths.forEachIndexed { row, half ->
            if (row == 0 || row == widths.lastIndex) return@forEachIndexed
            val top = cy - radius + row * unit
            val inset = if (half >= 7) 1 else 1
            val left = cx - (half - inset) * unit
            val right = cx + (half - inset) * unit

            paint.color = fill
            canvas.drawRect(left, top, right, top + unit, paint)
        }

        // Sprite, stamped in the middle in the outline colour.
        val cell = unit
        val spriteWidth = sprite[0].length
        val originX = cx - spriteWidth / 2f * cell
        val originY = cy - sprite.size / 2f * cell

        paint.color = outline
        sprite.forEachIndexed { row, line ->
            line.forEachIndexed { col, char ->
                if (char != 'X') return@forEachIndexed
                val left = originX + col * cell
                val top = originY + row * cell
                canvas.drawRect(left, top, left + cell, top + cell, paint)
            }
        }
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val slop = 22f * density
        // Reverse order so the badge drawn on top gets the tap.
        for ((id, x, y) in hitBoxes.asReversed()) {
            if (hypot(event.x - x, event.y - y) <= slop) {
                onSelect(id)
                return true
            }
        }
        return false
    }

    private fun SpideyCore.Sighting.reporterHandleIsMine(myId: String) = id.startsWith("local-")

    companion object {
        /** 7x7 pixel spider: body, and legs reaching out either side. */
        private val SPIDER = arrayOf(
            "X.....X",
            ".X.X.X.",
            "..XXX..",
            "XXXXXXX",
            "..XXX..",
            ".X.X.X.",
            "X.....X",
        )

        /** 7x7 pixel star, used for the user's own reports. */
        private val STAR = arrayOf(
            "...X...",
            "...X...",
            "XXXXXXX",
            ".XXXXX.",
            "..XXX..",
            ".XX.XX.",
            "X.....X",
        )
    }
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
