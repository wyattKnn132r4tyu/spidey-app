package com.spidey.tracker.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The chunky plastic furniture: bezels, round and square hardware buttons, pixel
 * sprites and the web compass. Nothing here uses a soft corner or a gradient —
 * the look depends on hard edges and flat, layered rectangles.
 *
 * All sprites are pixel grids defined here rather than imported artwork.
 */

/** The round amber hardware button, dark ring and all. */
@Composable
fun RoundButton(
    size: Dp = 42.dp,
    fill: Color = Ink.amber,
    ring: Color = Ink.amberInk,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(ring)
            .padding(3.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** The square white hardware button on the other side of the title. */
@Composable
fun SquareButton(
    size: Dp = 42.dp,
    fill: Color = Ink.white,
    ring: Color = Ink.navyDeep,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .background(ring)
            .padding(3.dp)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
fun HamburgerIcon(color: Color = Ink.amberInk, width: Dp = 18.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { Box(Modifier.width(width).height(3.dp).background(color)) }
    }
}

/** Speaker glyph for the sound button, drawn as pixels. */
@Composable
fun SpeakerIcon(size: Dp = 20.dp, color: Color = Ink.navyDeep) {
    Canvas(Modifier.size(size)) {
        val cell = this.size.minDimension / 9f
        val sprite = listOf(
            "...XX...W",
            "..XXX..W.",
            "XXXXX.W.W",
            "XXXXXW.W.",
            "XXXXXW.W.",
            "XXXXX.W.W",
            "..XXX..W.",
            "...XX...W",
            ".........",
        )
        sprite.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                if (ch == 'X' || ch == 'W') {
                    drawRect(color, Offset(col * cell, row * cell), Size(cell, cell))
                }
            }
        }
    }
}

/**
 * The mask that sits between the two words of the title: a red face plate with
 * two white eyes. Drawn from a grid, not traced from anyone's artwork.
 */
@Composable
fun MaskIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val cell = this.size.minDimension / 7f
        val red = Ink.pinRed
        val dark = Ink.pinRedDark
        val eye = Ink.white

        val sprite = listOf(
            ".DDDDD.",
            "DRRRRRD",
            "REERREE",
            "REERREE",
            "DRRRRRD",
            ".DRRRD.",
            "..DDD..",
        )
        sprite.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                val color = when (ch) {
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
            line.forEachIndexed { col, ch ->
                val color = when (ch) {
                    'B' -> body
                    'L' -> legs
                    else -> null
                }
                if (color != null) {
                    drawRect(color, Offset(col * cell, row * cell), Size(cell, cell))
                }
            }
        }
    }
}

/** The title plate: dark panel, light border, mask between the two words. */
@Composable
fun TitlePlate(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Ink.navyDeep)
            .padding(2.dp)
            .background(Ink.bezelLight)
            .padding(2.dp)
            .background(Ink.navyDeep)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SPIDEY", style = PixelType.label, color = Ink.white)
            Box(Modifier.padding(horizontal = 7.dp)) { MaskIcon(size = 15.dp) }
            Text("TRACKER", style = PixelType.label, color = Ink.white)
        }
    }
}

/**
 * A tab clinging to the left edge of the screen, the way the film's version
 * hangs its filter badges half outside the frame.
 */
@Composable
fun EdgeTab(fill: Color, dark: Color, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(dark)
            .padding(2.dp)
            .background(fill)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelSpider(size = 13.dp, body = dark, legs = dark)
        Text(
            count.toString(),
            style = PixelType.tiny,
            color = dark,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * The counter strip above the callout: two readouts either side of a mask, in
 * the film's "00.00" style. Left is everything on the map, right is what you
 * have not looked at yet.
 */
@Composable
fun CounterStrip(total: Int, unexplored: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CounterPlate(total)
        Box(Modifier.padding(horizontal = 6.dp)) { MaskIcon(size = 22.dp) }
        CounterPlate(unexplored)
    }
}

@Composable
private fun CounterPlate(value: Int) {
    Box(
        Modifier
            .background(Ink.navyDeep)
            .padding(2.dp)
            .background(Ink.white)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    ) {
        Text(
            "%02d.%02d".format(value / 100, value % 100),
            style = PixelType.small,
            color = Ink.navyDeep,
        )
    }
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
            .background(Ink.amberInk)
            .padding(2.dp)
            .background(Ink.amberDark)
            .padding(bottom = 3.dp)
            .background(if (enabled) Ink.amber else Ink.muted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), style = style, color = Ink.amberInk, textAlign = TextAlign.Center)
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
        Text(label.uppercase(), style = style, color = accent, textAlign = TextAlign.Center)
    }
}

/** Ruler ticks along an edge, matching the measuring scale on the film's frame. */
@Composable
fun RulerEdge(vertical: Boolean, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(Ink.navyDeep)
            .then(
                if (vertical) Modifier.width(11.dp).fillMaxHeight()
                else Modifier.height(11.dp).fillMaxWidth(),
            ),
    ) {
        val span = if (vertical) size.height else size.width
        val step = 9.dp.toPx()
        val thickness = 1.5.dp.toPx()
        var i = 0
        var at = 0f
        while (at < span) {
            // Every fourth tick runs long, the rest short.
            val length = if (i % 4 == 0) size.minDimension * 0.8f else size.minDimension * 0.35f
            if (vertical) {
                drawRect(Ink.bezelLight, Offset(0f, at), Size(length, thickness))
            } else {
                drawRect(Ink.bezelLight, Offset(at, 0f), Size(thickness, length))
            }
            at += step
            i++
        }
    }
}

/**
 * The web compass in the bottom corner: radial threads, angular rings, a globe
 * on one spoke and a target on another.
 */
@Composable
fun WebCompass(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val centre = Offset(this.size.width * 0.46f, this.size.height * 0.54f)
        val radius = this.size.minDimension * 0.44f
        val spokes = 8
        val thread = Ink.bezelLight.copy(alpha = 0.85f)

        for (i in 0 until spokes) {
            val angle = (i * 2.0 * PI / spokes).toFloat()
            drawLine(
                thread,
                centre,
                Offset(centre.x + cos(angle) * radius, centre.y + sin(angle) * radius),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        // Rings as straight chords between spokes: that angular look is what
        // reads as a web rather than a dartboard.
        for (ring in 1..3) {
            val r = radius * ring / 3f
            for (i in 0 until spokes) {
                val a = (i * 2.0 * PI / spokes).toFloat()
                val b = ((i + 1) * 2.0 * PI / spokes).toFloat()
                drawLine(
                    thread.copy(alpha = 0.6f),
                    Offset(centre.x + cos(a) * r, centre.y + sin(a) * r),
                    Offset(centre.x + cos(b) * r, centre.y + sin(b) * r),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        drawCircle(Ink.bezelLight, radius = 3.5.dp.toPx(), center = centre)

        val globe = Offset(centre.x + radius * 0.92f, centre.y - radius * 0.42f)
        drawCircle(Ink.navyDeep, radius = 8.dp.toPx(), center = globe)
        drawCircle(
            Ink.bezelLight,
            radius = 8.dp.toPx(),
            center = globe,
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
        )
        drawLine(
            Ink.bezelLight,
            Offset(globe.x - 8.dp.toPx(), globe.y),
            Offset(globe.x + 8.dp.toPx(), globe.y),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            Ink.bezelLight,
            Offset(globe.x, globe.y - 8.dp.toPx()),
            Offset(globe.x, globe.y + 8.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )

        val target = Offset(centre.x + radius * 0.5f, centre.y + radius * 0.9f)
        drawCircle(Ink.navyDeep, radius = 7.dp.toPx(), center = target)
        drawCircle(
            Ink.bezelLight,
            radius = 7.dp.toPx(),
            center = target,
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
        )
        drawCircle(Ink.bezelLight, radius = 2.dp.toPx(), center = target)
    }
}

/**
 * The figure perched in the corner of the frame, where the film's site puts its
 * mascot. Deliberately its own character — a masked watcher in a hooded jacket,
 * not a likeness of anyone's.
 */
@Composable
fun WatcherSprite(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val cell = this.size.minDimension / 12f
        val red = Ink.pinRed
        val dark = Ink.pinRedDark
        val navy = Ink.panel
        val eye = Ink.white

        val sprite = listOf(
            "....DDDD....",
            "...DRRRRD...",
            "..DRRRRRRD..",
            "..REERREER..",
            "..DRRRRRRD..",
            "...DRRRRD...",
            "..DRRRRRRD..",
            ".DRRRRRRRRD.",
            ".DR.RRRR.RD.",
            "....NNNN....",
            "...NN..NN...",
            "..DD....DD..",
        )

        sprite.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                val color = when (ch) {
                    'R' -> red
                    'D' -> dark
                    'N' -> navy
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

/** The little downward pointer under the sighting card, stepped like the frame. */
@Composable
fun CardTail(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 26.dp, height = 13.dp)) {
        val rows = 6
        val cell = size.height / rows
        val centre = size.width / 2f

        for (row in 0 until rows) {
            val half = (rows - row) * cell
            // Light border first, then navy inset over it, leaving a stepped edge.
            drawRect(Ink.bezelLight, Offset(centre - half, row * cell), Size(half * 2, cell))
            if (half > cell) {
                drawRect(
                    Ink.navyDeep,
                    Offset(centre - half + cell, row * cell),
                    Size((half - cell) * 2, cell),
                )
            }
        }
    }
}
