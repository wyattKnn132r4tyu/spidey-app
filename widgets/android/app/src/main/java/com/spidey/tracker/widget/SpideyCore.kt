package com.spidey.tracker.widget

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kotlin port of the app's seeding and confidence maths (src/lib/seed.ts,
 * src/lib/confidence.ts, src/lib/geo.ts).
 *
 * Android widgets render through RemoteViews and cannot run the web app's
 * JavaScript, so this is a deliberate second implementation. It reproduces the
 * same pseudo-random sequence — same LCG, same constants, same order of draws —
 * so the widget and the app agree about what is hot at any given moment.
 *
 * If you change the model in src/lib, change it here too. The parity test in
 * app/src/test/java/.../SpideyCoreTest.kt pins the values that must match.
 */
object SpideyCore {

    // ---- geo ---------------------------------------------------------------

    private const val EARTH_RADIUS_M = 6_371_000.0

    data class LatLng(val lat: Double, val lng: Double)

    private fun toRad(deg: Double) = deg * Math.PI / 180.0

    fun distanceM(a: LatLng, b: LatLng): Double {
        val dLat = toRad(b.lat - a.lat)
        val dLng = toRad(b.lng - a.lng)
        val lat1 = toRad(a.lat)
        val lat2 = toRad(b.lat)
        val h = sin(dLat / 2).pow(2) + sin(dLng / 2).pow(2) * cos(lat1) * cos(lat2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    fun offset(origin: LatLng, metres: Double, bearingDeg: Double): LatLng {
        val angular = metres / EARTH_RADIUS_M
        val bearing = toRad(bearingDeg)
        val lat1 = toRad(origin.lat)
        val lng1 = toRad(origin.lng)

        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lng2 = lng1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )

        return LatLng(lat2 * 180.0 / Math.PI, ((lng2 * 180.0 / Math.PI + 540.0) % 360.0) - 180.0)
    }

    private val COMPASS = listOf(
        "north", "north-east", "east", "south-east",
        "south", "south-west", "west", "north-west",
    )

    fun compassFrom(from: LatLng, to: LatLng): String {
        val dLng = toRad(to.lng - from.lng)
        val lat1 = toRad(from.lat)
        val lat2 = toRad(to.lat)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val deg = ((atan2(y, x) * 180.0 / Math.PI) + 360.0) % 360.0
        return COMPASS[(Math.round(deg / 45.0).toInt()) % 8]
    }

    fun formatDistance(metres: Double): String {
        if (metres < 1000) return "${(metres / 10).roundToInt() * 10} m"
        val km = metres / 1000.0
        return if (km < 10) String.format("%.1f km", km) else "${km.roundToInt()} km"
    }

    // ---- deterministic random ----------------------------------------------

    /**
     * The same 32-bit LCG the web app uses. Kept in a Long masked to 32 bits so
     * the sequence matches JavaScript's `(state * 1664525 + 1013904223) >>> 0`
     * exactly — the intermediate product stays well inside Long's range, as it
     * does inside JS doubles.
     */
    private class Lcg(seed: Long) {
        private var state: Long = seed and 0xFFFFFFFFL

        fun next(): Double {
            state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return state.toDouble() / 4294967296.0
        }
    }

    /** JavaScript's Math.round: half rounds up, toward positive infinity. */
    private fun jsRound(value: Double): Double = floor(value + 0.5)

    // ---- model -------------------------------------------------------------

    data class Vote(val distanceM: Double, val onPatrol: Boolean, val createdAt: Double)

    data class Sighting(
        val lat: Double,
        val lng: Double,
        val createdAt: Double,
        val tagWeight: Double,
        val reportedOnPatrol: Boolean,
        val confirms: List<Vote>,
        val denies: List<Vote>,
    ) {
        val position get() = LatLng(lat, lng)
    }

    private data class ClusterSpec(
        val ageMin: Double,
        val count: Int,
        val confirms: Double,
        val radiusM: Double,
        val distanceM: Double,
    )

    private val CLUSTERS = listOf(
        ClusterSpec(8.0, 5, 9.0, 220.0, 700.0),
        ClusterSpec(35.0, 3, 5.0, 260.0, 1500.0),
        ClusterSpec(95.0, 4, 4.0, 300.0, 2400.0),
        ClusterSpec(180.0, 2, 3.0, 180.0, 3100.0),
        ClusterSpec(260.0, 3, 2.0, 340.0, 1900.0),
    )

    /** Mirrors the TAGS array in seed.ts, mapped to their baseWeight in types.ts. */
    private val TAG_WEIGHTS = doubleArrayOf(1.0, 1.0, 0.6, 1.2, 1.4, 0.9)

    private const val HANDLE_COUNT = 16
    private const val NOTE_COUNT = 10
    private const val MINUTE = 60_000.0

    /**
     * Reproduces seedSightings() from src/lib/seed.ts.
     *
     * The order of draws matters as much as the constants: every `random()` here
     * corresponds one-to-one with a call in the TypeScript, including the ones
     * whose results this port does not need (handle, note). Removing an unused
     * draw would desynchronise every value after it.
     */
    fun seedSightings(home: LatLng, now: Long): List<Sighting> {
        val random = Lcg(((now / 86_400_000L) * 7919L + 13L))
        val sightings = mutableListOf<Sighting>()

        for (cluster in CLUSTERS) {
            val centreDistance = cluster.distanceM * (0.7 + random.next() * 0.6)
            val centre = offset(home, centreDistance, random.next() * 360.0)

            repeat(cluster.count) {
                val at = offset(centre, random.next() * cluster.radiusM, random.next() * 360.0)
                // Matches seed.ts: never in the future, so decay stays a pure exponential.
                val ageMinutes = max(1.0, cluster.ageMin + random.next() * 20.0 - 10.0)
                val createdAt = now - ageMinutes * MINUTE
                val confirmCount = max(0.0, jsRound(cluster.confirms * (0.5 + random.next()))).toInt()
                val denyCount = if (random.next() < 0.35) jsRound(random.next() * 2.0).toInt() else 0

                fun makeVote(): Vote {
                    random.next() // userId
                    val voteDistance = random.next() * 900.0
                    val onPatrol = random.next() < 0.25
                    val voteAt = min(now.toDouble(), createdAt + random.next() * (now - createdAt))
                    return Vote(voteDistance, onPatrol, voteAt)
                }

                random.next() // handle
                val tagWeight = TAG_WEIGHTS[floor(random.next() * TAG_WEIGHTS.size).toInt()]
                random.next() // note
                val reportedOnPatrol = random.next() < 0.3

                val confirms = (0 until confirmCount).map { makeVote() }
                val denies = (0 until denyCount).map { makeVote() }

                sightings.add(
                    Sighting(at.lat, at.lng, createdAt, tagWeight, reportedOnPatrol, confirms, denies),
                )
            }
        }

        // seed.ts returns newest first; the order is part of the contract.
        return sightings.sortedByDescending { it.createdAt }
    }

    // ---- confidence --------------------------------------------------------

    const val HALF_LIFE_MS = 90.0 * 60.0 * 1000.0
    private const val SATURATION = 2.5
    private const val DENY_MULTIPLIER = 1.1
    const val HOT_THRESHOLD = 0.66
    const val WARM_THRESHOLD = 0.3

    private fun decayFrom(timestamp: Double, now: Long) =
        0.5.pow(max(0.0, now - timestamp) / HALF_LIFE_MS)

    private fun proximityWeight(distanceM: Double) = 1.0 / (1.0 + max(0.0, distanceM) / 400.0)

    private fun voteWeight(vote: Vote) =
        proximityWeight(vote.distanceM) * (if (vote.onPatrol) 1.5 else 1.0)

    fun scoreOf(sighting: Sighting, now: Long): Double {
        var score = sighting.tagWeight *
            (if (sighting.reportedOnPatrol) 1.3 else 1.0) *
            decayFrom(sighting.createdAt, now)

        for (vote in sighting.confirms) score += voteWeight(vote) * decayFrom(vote.createdAt, now)
        for (vote in sighting.denies) {
            score -= voteWeight(vote) * DENY_MULTIPLIER * decayFrom(vote.createdAt, now)
        }

        return max(0.0, score)
    }

    fun confidenceOf(sighting: Sighting, now: Long): Double {
        val score = scoreOf(sighting, now)
        return score / (score + SATURATION)
    }

    enum class Heat { HOT, WARM, COLD }

    fun heatFromConfidence(confidence: Double): Heat = when {
        confidence >= HOT_THRESHOLD -> Heat.HOT
        confidence >= WARM_THRESHOLD -> Heat.WARM
        else -> Heat.COLD
    }

    fun heatOf(sighting: Sighting, now: Long): Heat =
        heatFromConfidence(confidenceOf(sighting, now))

    private fun scoreForConfidence(confidence: Double) =
        SATURATION * confidence / (1 - confidence)

    /**
     * Milliseconds until this sighting drops to the next band down, or null if it
     * is already there. Exact rather than simulated: every contribution shares
     * one half-life, so the total score is itself an exponential in future time.
     */
    fun msUntilNextBand(sighting: Sighting, now: Long): Long? {
        val score = scoreOf(sighting, now)
        val target = when (heatOf(sighting, now)) {
            Heat.HOT -> scoreForConfidence(HOT_THRESHOLD)
            Heat.WARM -> scoreForConfidence(WARM_THRESHOLD)
            Heat.COLD -> return null
        }
        if (score <= target) return null
        return (ln(score / target) / ln(2.0) * HALF_LIFE_MS).toLong()
    }

    // ---- what the widget shows ---------------------------------------------

    data class Summary(
        val hot: Int,
        val warm: Int,
        val cold: Int,
        val nearestDistanceM: Double?,
        val nearestWhere: String?,
        val nearestHeat: Heat?,
        val fadeMs: Long?,
    )

    fun summarise(home: LatLng, now: Long): Summary {
        val sightings = seedSightings(home, now)

        var hot = 0
        var warm = 0
        var cold = 0
        for (sighting in sightings) {
            when (heatOf(sighting, now)) {
                Heat.HOT -> hot++
                Heat.WARM -> warm++
                Heat.COLD -> cold++
            }
        }

        // Hottest first, then nearest — what you would actually go and look at.
        val top = sightings.maxWithOrNull(
            compareBy<Sighting> { confidenceOf(it, now) }
                .thenByDescending { distanceM(home, it.position) },
        )

        return Summary(
            hot = hot,
            warm = warm,
            cold = cold,
            nearestDistanceM = top?.let { distanceM(home, it.position) },
            nearestWhere = top?.let { compassFrom(home, it.position) },
            nearestHeat = top?.let { heatOf(it, now) },
            fadeMs = top?.let { msUntilNextBand(it, now) },
        )
    }

    fun formatMinutes(ms: Long): String {
        val total = (ms / 60_000.0).roundToInt()
        return if (total < 60) "${total}m" else "${total / 60}h ${total % 60}m"
    }

    /** Only used by the parity test. */
    fun debugConfidences(home: LatLng, now: Long): List<Double> =
        seedSightings(home, now).map { confidenceOf(it, now) }

    @Suppress("unused")
    private fun unusedGuard() = abs(0.0)
}
