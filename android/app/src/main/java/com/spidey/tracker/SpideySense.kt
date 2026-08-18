package com.spidey.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Spidey-sense: the buzz when something hot is close.
 *
 * Fires at most once per sighting, and only while the app is on screen — there
 * is no background service, so this cannot wake you from a pocket.
 */
class SpideySense(private val context: Context) {

    /** Sightings already announced, so a pin does not buzz on every fix. */
    private val announced = mutableSetOf<String>()

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Spidey-sense", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Buzzes when a hot sighting is close by." },
        )
    }

    /**
     * Checks the nearest hot sighting and alerts if it is inside the radius.
     * Returns the sighting alerted on, or null if nothing fired.
     */
    fun check(
        from: SpideyCore.LatLng,
        sightings: List<SpideyCore.Sighting>,
        now: Long,
        radiusM: Double = RADIUS_M,
    ): SpideyCore.Sighting? {
        val near = sightings
            .filter { SpideyCore.heatOf(it, now) == SpideyCore.Heat.HOT }
            .filter { it.id !in announced }
            .minByOrNull { SpideyCore.distanceM(from, it.position) }
            ?: return null

        val distance = SpideyCore.distanceM(from, near.position)
        if (distance > radiusM) return null

        announced.add(near.id)
        buzz()
        notify(near, distance)
        return near
    }

    /** Lets a pin buzz again once it has cooled and reheated. */
    fun forget(id: String) = announced.remove(id)

    private fun buzz() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return

            // Two short pulses: the tingle, not an alarm.
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 90, 60), -1))
        }
    }

    private fun notify(sighting: SpideyCore.Sighting, distanceM: Double) {
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return

            val note = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SPIDEY-SENSE")
                .setContentText(
                    "${SpideyCore.tagMeta(sighting.tag).label} · " +
                        "${SpideyCore.formatDistance(distanceM)} away",
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            manager.notify(sighting.id.hashCode(), note)
        }
    }

    companion object {
        const val CHANNEL = "spidey-sense"

        /** Close enough to be worth looking up from your phone. */
        const val RADIUS_M = 400.0
    }
}
