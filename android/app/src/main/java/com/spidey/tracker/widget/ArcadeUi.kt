package com.spidey.tracker.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The chunky plastic furniture: bezels, stepped borders, amber buttons and the
 * web compass. Nothing here uses rounded corners — the whole look depends on
 * hard edges and layered rectangles.
 */

/** A stepped border: light top-left, dark bottom-right, like moulded plastic. */
@Composable
fun PixelFrame(
    modifier: Modifier = Modifier,
    fill: Color = Ink.panel,
    border: Color = Ink.bezelLight,
    borderDark: Color = Ink.bezelDark,
    thickness: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .background(borderDark)
            .padding(bottom = thickness, end = thickness)
            .background(border)
            .padding(top = thickness, start = thickness)
            .background(fill),
        content = content,
    )
}

/** The amber pill used for anything meant to be pressed. */
@Composable
fun AmberButton(
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PixelType.small,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .background(Ink.amberDark)
            .padding(bottom = 3.dp, end = 3.dp)
            .background(if (enabled) Ink.amber else Ink.muted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            label.uppercase(),
            style = style,
            color = Ink.amberInk,
            textAlign = TextAlign.Center,
        )
    }
}

/** Dark panel button, for secondary actions inside the bezel. */
@Composable
fun PanelButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Ink.bezelLight,
    style: TextStyle = PixelType.small,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .background(accent)
            .padding(2.dp)
            .background(Ink.navyDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(label.uppercase(), style = style, color = accent)
    }
}

/**
 * The title plate. The film's version has a mask between the two words; this
 * draws an original pixel spider in the same slot rather than copying the mark.
 */
@Composable
fun TitlePlate(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Ink.bezelLight)
            .padding(2.dp)
            .background(Ink.navyDeep)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text("SPIDEY", style = PixelType.label, color = Ink.text)
            Box(Modifier.padding(horizontal = 6.dp)) {
                PixelSpider(size = 12.dp, body = Ink.pinRed, legs = Ink.white)
            }
            androidx.compose.material3.Text("TRACKER", style = PixelType.label, color = Ink.text)
        }
    }
}

/** Original pixel spider, drawn from a grid rather than a bundled image. */
@Composable
fun PixelSpider(
    size: Dp,
    body: Color = Ink.navyDeep,
    legs: Color = Ink.navyDeep,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val cell = this.size.minDimension / 7f
        fun px(col: Int, row: Int, color: Color) {
            drawRect(color, Offset(col * cell, row * cell), Size(cell, cell))
        }

        val sprite = listOf(
            "L.....L",
            ".L.L.L.",
            "..BBB..",
            "LBBBBBL",
            "..BBB..",
            ".L.L.L.",
            "L.....L",
        )
        sprite.forEachIndexed { row, line ->
            line.forEachIndexed { col, char ->
                when (char) {
                    'B' -> px(col, row, body)
                    'L' -> px(col, row, legs)
                }
            }
        }
    }
}

/** Ruler ticks along an edge, like the measuring scale on the film's frame. */
@Composable
fun RulerEdge(vertical: Boolean, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(Ink.navyDeep)
            .then(if (vertical) Modifier.width(10.dp).fillMaxHeight() else Modifier.height(10.dp).fillMaxWidth()),
    ) {
        val span = if (vertical) size.height else size.width
        val step = 10.dp.toPx()
        var i = 0
        var at = 0f
        while (at < span) {
            // Every fifth tick is long, the rest short.
            val length = if (i % 5 == 0) size.minDimension * 0.75f else size.minDimension * 0.35f
            if (vertical) {
                drawRect(Ink.bezelLight, Offset(0f, at), Size(length, 1.5.dp.toPx()))
            } else {
                drawRect(Ink.bezelLight, Offset(at, 0f), Size(1.5.dp.toPx(), length))
            }
            at += step
            i++
        }
    }
}

/**
 * The web compass in the bottom corner. Radial threads with spiral rings, a
 * globe dot on one spoke and a target on another.
 */
@Composable
fun WebCompass(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val centre = Offset(this.size.width * 0.45f, this.size.height * 0.55f)
        val radius = this.size.minDimension * 0.42f
        val spokes = 8
        val stroke = Stroke(width = 1.5.dp.toPx())

        for (i in 0 until spokes) {
            val angle = (i * 2.0 * PI / spokes).toFloat()
            drawLine(
                Ink.bezelLight.copy(alpha = 0.75f),
                centre,
                Offset(centre.x + cos(angle) * radius, centre.y + sin(angle) * radius),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        // Rings drawn as straight chords between spokes, which is what gives a
        // web its angular look rather than a smooth circle.
        for (ring in 1..3) {
            val r = radius * ring / 3f
            for (i in 0 until spokes) {
                val a = (i * 2.0 * PI / spokes).toFloat()
                val b = ((i + 1) * 2.0 * PI / spokes).toFloat()
                drawLine(
                    Ink.bezelLight.copy(alpha = 0.5f),
                    Offset(centre.x + cos(a) * r, centre.y + sin(a) * r),
                    Offset(centre.x + cos(b) * r, centre.y + sin(b) * r),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        drawCircle(Ink.bezelLight, radius = 4.dp.toPx(), center = centre)

        val globe = Offset(centre.x + radius * 0.95f, centre.y - radius * 0.35f)
        drawCircle(Ink.navyDeep, radius = 7.dp.toPx(), center = globe)
        drawCircle(Ink.bezelLight, radius = 7.dp.toPx(), center = globe, style = stroke)
        drawLine(
            Ink.bezelLight,
            Offset(globe.x - 7.dp.toPx(), globe.y),
            Offset(globe.x + 7.dp.toPx(), globe.y),
            strokeWidth = 1.dp.toPx(),
        )

        val target = Offset(centre.x + radius * 0.55f, centre.y + radius * 0.85f)
        drawCircle(Ink.bezelLight, radius = 6.dp.toPx(), center = target, style = stroke)
        drawCircle(Ink.bezelLight, radius = 2.dp.toPx(), center = target)
    }
}

/**
 * A small pixel figure crouched in the corner of the frame, in the same slot the
 * film's site puts its mascot. Original art: a hooded watcher, not a character.
 */
@Composable
fun WatcherSprite(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val cell = this.size.minDimension / 12f
        val red = Ink.pinRed
        val dark = Ink.pinRedDark
        val eye = Ink.white

        val sprite = listOf(
            "....RRRR....",
            "...RRRRRR...",
            "..RRRRRRRR..",
            "..REERREER..",
            "..RRRRRRRR..",
            "...RRRRRR...",
            "..DRRRRRRD..",
            ".D.RRRRRR.D.",
            "...RR..RR...",
            "...RR..RR...",
            "..DD....DD..",
            "..DD....DD..",
        )

        sprite.forEachIndexed { row, line ->
            line.forEachIndexed { col, char ->
                val color = when (char) {
                    'R' -> red
                    'D' -> dark
                    'E' -> eye
                    else -> null
                }
                if (color != null) {
                    drawRect(color, Offset(col * cell, row * cell), Size(cell, cell))
                }
            }
        }
    }
}

/** Small stacked badges down the left edge, used as the heat legend. */
@Composable
fun LegendBadge(color: Color, dark: Color, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(dark)
            .padding(2.dp)
            .background(color)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelSpider(size = 11.dp, body = dark, legs = dark)
        androidx.compose.material3.Text(
            count.toString(),
            style = PixelType.tiny,
            color = dark,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Draws a hard-edged inset rectangle, used for readouts. */
fun DrawScope.pixelInset(color: Color, thickness: Float) {
    drawRect(color, Offset.Zero, Size(size.width, thickness))
    drawRect(color, Offset(0f, size.height - thickness), Size(size.width, thickness))
    drawRect(color, Offset.Zero, Size(thickness, size.height))
    drawRect(color, Offset(size.width - thickness, 0f), Size(thickness, size.height))
}
