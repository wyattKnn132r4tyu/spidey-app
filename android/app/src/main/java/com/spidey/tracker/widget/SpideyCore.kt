package com.spidey.tracker.widget

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
 * The model: geography, seeding and the confidence maths.
 *
 * This is a deliberate second implementation of the TypeScript in src/lib. It
 * reproduces the same pseudo-random sequence — same LCG, same constants, same
 * order of draws — so the app, the iOS widget and this all describe the same
 * city at the same moment.
 *
 * If you change the model in src/lib, change it here too. The parity test in
 * app/src/test pins the values that must match; regenerate its fixture with
 * `npm run build:parity`.
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

    fun centroid(points: List<LatLng>): LatLng {
        var lat = 0.0
        var lng = 0.0
        for (p in points) {
            lat += p.lat
            lng += p.lng
        }
        return LatLng(lat / points.size, lng / points.size)
    }

    fun formatDistance(metres: Double): String {
        if (metres < 1000) return "${(metres / 10).roundToInt() * 10} m"
        val km = metres / 1000.0
        return if (km < 10) String.format("%.1f km", km) else "${km.roundToInt()} km"
    }

    fun formatAgo(timestamp: Long, now: Long): String {
        val seconds = max(0L, (now - timestamp) / 1000)
        if (seconds < 60) return "just now"
        val minutes = Math.round(seconds / 60.0)
        if (minutes < 60) return "$minutes min ago"
        val hours = Math.round(minutes / 60.0)
        if (hours < 24) return "$hours hr ago"
        val days = Math.round(hours / 24.0)
        return if (days == 1L) "yesterday" else "$days days ago"
    }

    fun formatMinutes(ms: Long): String {
        val total = (ms / 60_000.0).roundToInt()
        return if (total < 60) "${total}m" else "${total / 60}h ${total % 60}m"
    }

    // ---- tags --------------------------------------------------------------

    data class TagMeta(val id: String, val label: String, val icon: String, val baseWeight: Double)

    /**
     * What people can actually report about someone the city cannot name: a
     * shape, a sound, what he left behind — and lately, other people behaving in
     * ways they cannot explain afterwards.
     */
    val TAGS = listOf(
        TagMeta("swinging", "Swinging through", "🕸️", 1.0),
        TagMeta("stopped-something", "Stopped a mugging", "🚨", 1.4),
        TagMeta("red-blur", "Just a red blur", "💨", 0.6),
        TagMeta("rooftop", "Rooftop landing", "🏙️", 1.1),
        TagMeta("webbing", "Fresh webbing", "🧵", 0.9),
        TagMeta("acting-strange", "People acting strange", "🌀", 1.2),
        TagMeta("heavy", "Something big fighting back", "⚡", 1.3),
    )

    private val TAG_BY_ID = TAGS.associateBy { it.id }

    fun tagMeta(id: String): TagMeta = TAG_BY_ID[id] ?: TAGS[0]

    // ---- deterministic random ----------------------------------------------

    /**
     * The same 32-bit LCG the web app uses. Held in a Long masked to 32 bits so
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

    data class Vote(
        val userId: String,
        val distanceM: Double,
        val onPatrol: Boolean,
        val createdAt: Double,
    )

    data class Sighting(
        val id: String,
        val lat: Double,
        val lng: Double,
        val createdAt: Double,
        val tag: String,
        val note: String?,
        val reporterHandle: String,
        val reportedOnPatrol: Boolean,
        val confirms: List<Vote>,
        val denies: List<Vote>,
    ) {
        val position get() = LatLng(lat, lng)
        val createdAtMs get() = createdAt.toLong()
        val tagWeight get() = tagMeta(tag).baseWeight
        val isSeeded get() = id.startsWith("seed-")
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

    private val HANDLES = listOf(
        "foresthills_frank", "f_train_ghost", "bodega_cat_92", "nightshiftnurse",
        "chelsea_walkup", "delivery_dave", "astoria_ana", "rooftop_gardener",
        "lateshift_leo", "bridge_and_tunnel", "midtown_myra", "flatiron_finch",
        "yellowcab_yuri", "harlem_hana", "brooklyn_bram", "notjjjameson",
    )

    private val NOTES = listOf(
        "came off the roof of the parking garage and just kept going",
        "heard the thwip before I saw anything",
        "two blocks up, moving north, fast",
        "whole street stopped and looked up",
        "webbing on the streetlight is still there if you want to check",
        "gone before I got my phone out, obviously",
        "nobody round here knows his name. we just know he turns up",
        "landed on the fire escape, waved, left",
        "kid outside the bodega called it before the rest of us looked up",
        "",
    )

    /** Weighted pick list: the everyday reports outnumber the strange ones. */
    private val SEED_TAGS = listOf(
        "swinging", "swinging", "red-blur", "rooftop", "stopped-something", "acting-strange",
    )

    private const val MINUTE = 60_000.0

    /**
     * Reproduces seedSightings() from src/lib/seed.ts.
     *
     * The order of draws matters as much as the constants: every `next()` here
     * corresponds one-to-one with a `random()` call in the TypeScript. Removing
     * or reordering one desynchronises every value after it.
     */
    fun seedSightings(home: LatLng, now: Long): List<Sighting> {
        val random = Lcg((now / 86_400_000L) * 7919L + 13L)
        val sightings = mutableListOf<Sighting>()

        CLUSTERS.forEachIndexed { clusterIndex, cluster ->
            val centreDistance = cluster.distanceM * (0.7 + random.next() * 0.6)
            val centre = offset(home, centreDistance, random.next() * 360.0)

            for (i in 0 until cluster.count) {
                val at = offset(centre, random.next() * cluster.radiusM, random.next() * 360.0)
                // Never in the future, so decay stays a pure exponential.
                val ageMinutes = max(1.0, cluster.ageMin + random.next() * 20.0 - 10.0)
                val createdAt = now - ageMinutes * MINUTE
                val confirmCount = max(0.0, jsRound(cluster.confirms * (0.5 + random.next()))).toInt()
                val denyCount = if (random.next() < 0.35) jsRound(random.next() * 2.0).toInt() else 0

                fun makeVote(): Vote {
                    val userId = "seed-${floor(random.next() * 9999).toInt()}"
                    val voteDistance = random.next() * 900.0
                    val onPatrol = random.next() < 0.25
                    val voteAt = min(now.toDouble(), createdAt + random.next() * (now - createdAt))
                    return Vote(userId, voteDistance, onPatrol, voteAt)
                }

                val handle = HANDLES[floor(random.next() * HANDLES.size).toInt()]
                val tagId = SEED_TAGS[floor(random.next() * SEED_TAGS.size).toInt()]
                val note = NOTES[floor(random.next() * NOTES.size).toInt()].ifEmpty { null }
                val reportedOnPatrol = random.next() < 0.3

                val confirms = (0 until confirmCount).map { makeVote() }
                val denies = (0 until denyCount).map { makeVote() }

                sightings.add(
                    Sighting(
                        id = "seed-$clusterIndex-$i",
                        lat = at.lat,
                        lng = at.lng,
                        createdAt = createdAt,
                        tag = tagId,
                        note = note,
                        reporterHandle = handle,
                        reportedOnPatrol = reportedOnPatrol,
                        confirms = confirms,
                        denies = denies,
                    ),
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

    /** Below this a pin is noise: too old or too disputed to draw. */
    fun isLive(sighting: Sighting, now: Long) = confidenceOf(sighting, now) > 0.05

    private fun scoreForConfidence(confidence: Double) =
        SATURATION * confidence / (1 - confidence)

    /**
     * Milliseconds until this sighting drops to the next band down, or null if it
     * is already at the bottom. Exact rather than simulated: every contribution
     * shares one half-life, so the total score is itself an exponential in future
     * time, whatever mix of votes produced it.
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

    // ---- summary for the widget --------------------------------------------

    data class Summary(
        val hot: Int,
        val warm: Int,
        val cold: Int,
        val nearestDistanceM: Double?,
        val nearestWhere: String?,
        val nearestHeat: Heat?,
        val fadeMs: Long?,
    )

    fun summarise(home: LatLng, now: Long, sightings: List<Sighting> = seedSightings(home, now)): Summary {
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
}
