package com.spidey.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Renders the widget for real: inflates the RemoteViews and reads the resulting
 * view tree. Catches layout and id mistakes that only surface on a home screen.
 */
@RunWith(RobolectricTestRunner::class)
class SpideyWidgetProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(): Map<Int, String> {
        val manager = AppWidgetManager.getInstance(context)
        val shadow = org.robolectric.Shadows.shadowOf(manager)

        // createWidget registers the provider and drives its onUpdate, which is
        // what the launcher does when the widget is placed.
        val widgetId = shadow.createWidget(
            SpideyWidgetProvider::class.java,
            R.layout.widget_spidey,
        )
        val root = shadow.getViewFor(widgetId)

        val texts = mutableMapOf<Int, String>()
        fun walk(view: android.view.View) {
            if (view is TextView) texts[view.id] = view.text.toString()
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
        return texts
    }

    @Test
    fun `renders every field with no location permission`() {
        val texts = render()

        assertEquals("🕸 SPIDEY", texts[R.id.widget_title])

        val hot = texts[R.id.widget_hot_count]!!.toInt()
        assertTrue("hot count should be a sane number", hot in 0..17)

        assertTrue(
            "counts line should read like '11 warm · 3 cold'",
            texts[R.id.widget_counts]!!.matches(Regex("""\d+ warm · \d+ cold""")),
        )
        assertTrue(
            "nearest line should carry a distance and a direction",
            texts[R.id.widget_nearest]!!.startsWith("nearest "),
        )
        assertTrue(
            "hot label should be pluralised",
            texts[R.id.widget_hot_label] in setOf("HOT SIGHTING", "HOT SIGHTINGS"),
        )
    }

    @Test
    fun `agrees with the core summary`() {
        val texts = render()
        // No permission and no cached fix, so the provider uses the midtown fallback.
        val summary = SpideyCore.summarise(
            SpideyCore.LatLng(40.7484, -73.9857),
            System.currentTimeMillis(),
        )
        assertEquals(summary.hot.toString(), texts[R.id.widget_hot_count])
        assertEquals("${summary.warm} warm · ${summary.cold} cold", texts[R.id.widget_counts])
    }
}
