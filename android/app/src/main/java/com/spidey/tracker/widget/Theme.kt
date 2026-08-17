package com.spidey.tracker.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * The arcade palette: a blue plastic bezel around a navy map, amber for anything
 * that wants pressing, and chunky pixel badges for the pins.
 */
object Ink {
    // Bezel / chrome
    val bezel = Color(0xFF3A87B5)
    val bezelLight = Color(0xFF6BB8DC)
    val bezelDark = Color(0xFF23698F)

    // Map
    val navy = Color(0xFF0A2145)
    val navyDeep = Color(0xFF071734)
    val panel = Color(0xFF0B1E3B)

    // Ink
    val text = Color(0xFFCFEBFF)
    val muted = Color(0xFF7FA6C8)
    val white = Color(0xFFF2FAFF)

    // Amber controls
    val amber = Color(0xFFF2A93B)
    val amberDark = Color(0xFFA85F14)
    val amberInk = Color(0xFF3A2408)

    // Pin badges
    val pinRed = Color(0xFFE23B3B)
    val pinRedDark = Color(0xFF6B1414)
    val pinGreen = Color(0xFF6ABE4F)
    val pinGreenDark = Color(0xFF1E4620)
    val pinBlue = Color(0xFF4EA9E8)
    val pinBlueDark = Color(0xFF123A5C)
    val pinGrey = Color(0xFFC9D4DC)
    val pinGreyDark = Color(0xFF4A5A66)

    val danger = Color(0xFFE23B3B)
}

val Pixel = FontFamily(Font(R.font.press_start_2p))

/**
 * Press Start 2P is very wide and has no bold weight, so sizes run small and
 * emphasis comes from colour and the chunky borders instead.
 */
object PixelType {
    val tiny = TextStyle(fontFamily = Pixel, fontSize = 6.sp, lineHeight = 11.sp)
    val small = TextStyle(fontFamily = Pixel, fontSize = 7.sp, lineHeight = 13.sp)
    val body = TextStyle(fontFamily = Pixel, fontSize = 8.sp, lineHeight = 15.sp)
    val label = TextStyle(fontFamily = Pixel, fontSize = 9.sp, lineHeight = 16.sp)
    val title = TextStyle(fontFamily = Pixel, fontSize = 12.sp, lineHeight = 20.sp)
    val huge = TextStyle(fontFamily = Pixel, fontSize = 20.sp, lineHeight = 26.sp)
}

fun heatColor(heat: SpideyCore.Heat) = when (heat) {
    SpideyCore.Heat.HOT -> Ink.pinRed
    SpideyCore.Heat.WARM -> Ink.pinGreen
    SpideyCore.Heat.COLD -> Ink.pinGrey
}

private val scheme = darkColorScheme(
    primary = Ink.amber,
    onPrimary = Ink.amberInk,
    secondary = Ink.bezelLight,
    background = Ink.navy,
    onBackground = Ink.text,
    surface = Ink.panel,
    onSurface = Ink.text,
    surfaceVariant = Ink.navyDeep,
    onSurfaceVariant = Ink.muted,
    outline = Ink.bezelDark,
)

@Composable
fun SpideyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            bodyLarge = PixelType.body,
            bodyMedium = PixelType.small,
            labelLarge = PixelType.label,
        ),
        content = content,
    )
}
