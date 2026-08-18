package com.spidey.tracker

import kotlin.math.abs

/**
 * Turns raw pins into the day's story. Port of src/lib/bugle.ts.
 *
 * Sightings are grouped into clusters (close in space, close in time) and each
 * cluster gets a headline from its size, heat and dominant tag. No geocoding:
 * locations are described relative to the reader, which works in any city and
 * needs no service.
 */
object Bugle {

    private const val CLUSTER_RADIUS_M = 600.0
    private const val CLUSTER_WINDOW_MS = 75 * 60 * 1000.0

    data class Cluster(
        val sightings: List<SpideyCore.Sighting>,
        val centre: SpideyCore.LatLng,
        val latestAt: Long,
        val confidence: Double,
    )

    enum class Tone { ALARM, WRY, NEUTRAL }

    data class Story(
        val id: String,
        val headline: String,
        val standfirst: String,
        val at: Long,
        val sightingIds: List<String>,
        val tone: Tone,
    )

    fun cluster(sightings: List<SpideyCore.Sighting>, now: Long): List<Cluster> {
        val remaining = sightings.sortedByDescending { it.createdAt }.toMutableList()
        val clusters = mutableListOf<Cluster>()

        while (remaining.isNotEmpty()) {
            val head = remaining.removeAt(0)
            val members = mutableListOf(head)

            for (i in remaining.indices.reversed()) {
                val candidate = remaining[i]
                val closeInSpace =
                    SpideyCore.distanceM(head.position, candidate.position) <= CLUSTER_RADIUS_M
                val closeInTime = abs(head.createdAt - candidate.createdAt) <= CLUSTER_WINDOW_MS
                if (closeInSpace && closeInTime) {
                    members.add(candidate)
                    remaining.removeAt(i)
                }
            }

            clusters.add(
                Cluster(
                    sightings = members,
                    centre = SpideyCore.centroid(members.map { it.position }),
                    latestAt = members.maxOf { it.createdAtMs },
                    // A cluster is as strong as its strongest pin, not the average.
                    confidence = members.maxOf { SpideyCore.confidenceOf(it, now) },
                ),
            )
        }

        return clusters.sortedByDescending { it.latestAt }
    }

    private val SPELLED = listOf(
        "zero", "a single", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    )

    private fun spell(n: Int) = (SPELLED.getOrNull(n) ?: n.toString()).uppercase()

    fun build(sightings: List<SpideyCore.Sighting>, home: SpideyCore.LatLng, now: Long): List<Story> =
        cluster(sightings, now).map { cluster ->
            val count = cluster.sightings.size
            val heat = SpideyCore.heatFromConfidence(cluster.confidence)
            val where = SpideyCore.compassFrom(home, cluster.centre).uppercase()
            val tag = cluster.sightings.groupingBy { it.tag }.eachCount()
                .maxByOrNull { it.value }!!.key
            val confirms = cluster.sightings.sumOf { it.confirms.size }
            val denies = cluster.sightings.sumOf { it.denies.size }

            var tone = Tone.NEUTRAL
            val headline = when {
                tag == "acting-strange" && heat != SpideyCore.Heat.COLD && confirms > 0 -> {
                    tone = Tone.ALARM
                    "SOMETHING IS WRONG WITH PEOPLE $where"
                }
                tag == "heavy" && heat != SpideyCore.Heat.COLD -> {
                    tone = Tone.ALARM
                    "SOMETHING BIG CAME THROUGH $where"
                }
                heat == SpideyCore.Heat.HOT && count >= 3 -> {
                    tone = Tone.ALARM
                    "${spell(count)} SIGHTINGS $where INSIDE THE HOUR — MENACE?"
                }
                tag == "stopped-something" && heat != SpideyCore.Heat.COLD && confirms > 0 -> {
                    // An alarmist headline needs someone other than the reporter behind it.
                    tone = Tone.ALARM
                    "MASKED VIGILANTE INTERFERES AGAIN, $where"
                }
                heat == SpideyCore.Heat.HOT -> {
                    // Four years of this and the city still cannot put a name to him.
                    tone = Tone.ALARM
                    "WHO IS HE? SIGHTING CONFIRMED $where, STILL NO NAME"
                }
                denies > confirms -> {
                    tone = Tone.WRY
                    "'SIGHTING' $where COLLAPSES UNDER SCRUTINY"
                }
                heat == SpideyCore.Heat.COLD && count == 1 -> {
                    tone = Tone.WRY
                    "READER REPORTS RED BLUR $where. BUGLE UNCONVINCED."
                }
                tag == "webbing" -> {
                    tone = Tone.WRY
                    "WEBBING FOUND $where — WHO CLEANS THIS UP?"
                }
                else -> "${spell(count)} REPORT${if (count == 1) "" else "S"} $where, STILL UNVERIFIED"
            }

            Story(
                id = "story-${cluster.sightings[0].id}",
                headline = headline,
                standfirst = "$count pin${if (count == 1) "" else "s"} · " +
                    "$confirms confirmed · $denies disputed",
                at = cluster.latestAt,
                sightingIds = cluster.sightings.map { it.id },
                tone = tone,
            )
        }
}
