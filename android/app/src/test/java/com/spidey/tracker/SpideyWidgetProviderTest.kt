package com.spidey.tracker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.File

/**
 * Renders the widget for real: inflates the RemoteViews through AppWidgetManager
 * and reads the resulting view tree. Catches layout and id mistakes that would
 * otherwise only surface on a home screen.
 */
@RunWith(RobolectricTestRunner::class)
class SpideyWidgetProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val home = SpideyCore.LatLng(40.7484, -73.9857)

    @Before
    fun setUp() {
        context.filesDir.listFiles()?.forEach(File::delete)
    }

    private fun render(): Pair<Map<Int, String>, android.view.View> {
        val manager = AppWidgetManager.getInstance(context)
        val shadow = Shadows.shadowOf(manager)

        // createWidget registers the provider and drives its onUpdate, which is
        // what the launcher does when the widget is placed.
        val widgetId = shadow.createWidget(SpideyWidgetProvider::class.java, R.layout.widget_spidey)
        val root = shadow.getViewFor(widgetId)

        val texts = mutableMapOf<Int, String>()
        fun walk(view: android.view.View) {
            if (view is TextView) texts[view.id] = view.text.toString()
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
        return texts to root
    }

    @Test
    fun `renders every field before the app has ever run`() {
        val (texts, _) = render()

        assertEquals("SPIDEY TRACKER", texts[R.id.widget_title])
        assertTrue(texts[R.id.widget_hot_count]!!.toInt() in 0..30)
        assertTrue(texts[R.id.widget_counts]!!.matches(Regex("""\d+ warm · \d+ cold""")))
        assertTrue(texts[R.id.widget_nearest]!!.startsWith("nearest "))
        assertTrue(texts[R.id.widget_hot_label] in setOf("HOT SIGHTING", "HOT SIGHTINGS"))
    }

    @Test
    fun `shows the city the app saved, including the user's own pin`() {
        // A pin the user dropped, confirmed enough times to be unmistakably hot.
        val repository = SpideyRepository(context)
        val now = System.currentTimeMillis()
        var state = repository.loadOrSeed(home, now)
        val before = state.sightings.count { SpideyCore.heatOf(it, now) == SpideyCore.Heat.HOT }

        val (withPin, sighting) = repository.report(state, home, "heavy", "mine", true, now)
        state = withPin
        repeat(12) { i ->
            state = repository.save(
                state.copy(
                    sightings = state.sightings.map {
                        if (it.id != sighting.id) it
                        else it.copy(
                            confirms = it.confirms + SpideyCore.Vote("voter-$i", 20.0, true, now.toDouble()),
                        )
                    },
                ),
            )
        }

        val (texts, _) = render()

        // The widget counted the app's pin, not a parallel city of its own.
        assertEquals(before + 1, texts[R.id.widget_hot_count]!!.toInt())
    }

    @Test
    fun `tapping the widget opens the app and never a browser`() {
        val (_, root) = render()

        root.performClick()

        val started = Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).nextStartedActivity

        assertNotNull("tapping the widget should start an activity", started)
        assertEquals(MainActivity::class.java.name, started!!.component?.className)
        // A browser would need a data URI; the app intent has none.
        assertNull("the widget must not hand a URL to anything", started.data)
    }
}
