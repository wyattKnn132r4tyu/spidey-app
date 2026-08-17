package com.spidey.tracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Renders each screen and writes a PNG, so the UI can be reviewed without a
 * device — there is no KVM here, so no emulator.
 *
 * It doubles as a crash test: a composable that throws, or a layout that cannot
 * measure, fails here rather than on someone's phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    private val out = File("build/screenshots").apply { mkdirs() }

    private fun sampleState(): UiState {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val now = System.currentTimeMillis()
        val home = SpideyCore.LatLng(40.7484, -73.9857)
        val state = SpideyRepository(context).loadOrSeed(home, now)

        return UiState(
            ready = true,
            home = home,
            position = home,
            sightings = state.sightings,
            patrols = listOf(
                SpideyRepository.Patrol(
                    "p1", now - 3_600_000, now - 1_800_000,
                    listOf(home), 4200.0, listOf("a", "b"),
                ),
            ),
            profile = state.profile.copy(streakDays = 3),
            selectedId = state.sightings.maxByOrNull { SpideyCore.confidenceOf(it, now) }?.id,
            clock = now,
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()

        controller.get().setContent {
            SpideyTheme { Box(Modifier.fillMaxSize().background(Ink.navyDeep)) { content() } }
        }

        ShadowLooper.idleMainLooper()

        val root: View = controller.get().findViewById(android.R.id.content)
        // Robolectric does not lay out automatically the way a device does.
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 2340)
        ShadowLooper.idleMainLooper()

        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        File(out, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    /** A screenshot of a blank screen proves nothing; check something was drawn. */
    private fun assertNotBlank(bitmap: Bitmap) {
        val colors = mutableSetOf<Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                colors.add(bitmap.getPixel(x, y))
                x += 13
            }
            y += 13
        }
        assertTrue("expected more than one colour on screen, got ${colors.size}", colors.size > 5)
    }

    private fun model() = SpideyViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun map() {
        val state = sampleState()
        assertNotBlank(capture("map") { MapScreen(state, model()) })
    }

    @Test
    fun `map with nothing selected shows the counter and callout`() {
        val state = sampleState().copy(selectedId = null)
        assertNotBlank(capture("map-callout") { MapScreen(state, model()) })
    }

    @Test
    fun bugle() {
        val state = sampleState()
        assertNotBlank(capture("bugle") { BugleScreen(state, model()) })
    }

    @Test
    fun patrol() {
        val state = sampleState()
        assertNotBlank(capture("patrol") { PatrolScreen(state, model()) })
    }

    @Test
    fun reportSheet() {
        val state = sampleState()
        assertNotBlank(
            capture("report") {
                ReportSheetContent(state, model(), "acting-strange", "", {}, {})
            },
        )
    }
}
