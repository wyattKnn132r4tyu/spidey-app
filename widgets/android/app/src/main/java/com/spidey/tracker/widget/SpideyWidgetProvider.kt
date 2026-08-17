package com.spidey.tracker.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * Home screen widget: hot/warm counts and the sighting most worth chasing.
 *
 * Widgets cannot ask for permissions and get a very short window to run, so
 * location comes from the last known fix, cached in preferences. The launcher
 * activity is what actually requests the permission.
 */
class SpideyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, SpideyWidgetProvider::class.java),
            )
            for (id in ids) manager.updateAppWidget(id, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_spidey)
        val home = resolveLocation(context)
        val summary = SpideyCore.summarise(home.position, System.currentTimeMillis())

        views.setTextViewText(R.id.widget_hot_count, summary.hot.toString())
        views.setTextColor(
            R.id.widget_hot_count,
            if (summary.hot > 0) COLOR_HOT else COLOR_MUTED,
        )
        views.setTextViewText(
            R.id.widget_hot_label,
            if (summary.hot == 1) "HOT SIGHTING" else "HOT SIGHTINGS",
        )
        views.setTextViewText(
            R.id.widget_counts,
            "${summary.warm} warm · ${summary.cold} cold",
        )

        val nearest = if (summary.nearestDistanceM != null) {
            "nearest ${SpideyCore.formatDistance(summary.nearestDistanceM)} ${summary.nearestWhere}"
        } else {
            "nothing on the wire"
        }
        views.setTextViewText(R.id.widget_nearest, nearest)

        val fade = summary.fadeMs
        val fadeText = when {
            fade == null -> ""
            summary.nearestHeat == SpideyCore.Heat.HOT -> "cools in ${SpideyCore.formatMinutes(fade)}"
            else -> "cold in ${SpideyCore.formatMinutes(fade)}"
        }

        // The staleness warning has to survive alongside the countdown — showing it
        // only when there is no countdown hid it exactly when the pins were most
        // likely to be wrong.
        views.setTextViewText(
            R.id.widget_fade,
            listOf(fadeText, if (home.stale) "last known location" else "")
                .filter { it.isNotEmpty() }
                .joinToString(" · "),
        )

        // Tapping anywhere opens the web app.
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(APP_URL))
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

        return views
    }

    private data class Fix(val position: SpideyCore.LatLng, val stale: Boolean)

    private fun resolveLocation(context: Context): Fix {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            lastKnown(context)?.let { location ->
                prefs.edit()
                    .putFloat(KEY_LAT, location.latitude.toFloat())
                    .putFloat(KEY_LNG, location.longitude.toFloat())
                    .apply()

                // "Last known" can mean last week, in another city. Still the best
                // guess available, but say so rather than presenting it as current.
                val age = System.currentTimeMillis() - location.time
                return Fix(
                    SpideyCore.LatLng(location.latitude, location.longitude),
                    stale = age > MAX_FIX_AGE_MS,
                )
            }
        }

        return cached(prefs) ?: Fix(FALLBACK, true)
    }

    private fun cached(prefs: SharedPreferences): Fix? {
        if (!prefs.contains(KEY_LAT)) return null
        return Fix(
            SpideyCore.LatLng(
                prefs.getFloat(KEY_LAT, 0f).toDouble(),
                prefs.getFloat(KEY_LNG, 0f).toDouble(),
            ),
            true,
        )
    }

    private fun lastKnown(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        return try {
            // Newest of whatever the providers already have; a widget has no time
            // to request a fresh fix.
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.spidey.tracker.widget.REFRESH"
        const val APP_URL = "https://wyattknn132r4tyu.github.io/spidey-app/"

        /** Beyond this, a cached fix is reported as stale rather than current. */
        private const val MAX_FIX_AGE_MS = 6 * 60 * 60 * 1000L

        private const val PREFS = "spidey-widget"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"

        /** Midtown, used only when there is no fix and nothing cached. */
        private val FALLBACK = SpideyCore.LatLng(40.7484, -73.9857)

        private const val COLOR_HOT = 0xFFFF5C3C.toInt()
        private const val COLOR_MUTED = 0xFF8D93AD.toInt()
    }
}
