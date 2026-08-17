package com.spidey.tracker.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.max
import kotlin.random.Random

/**
 * Everything the app knows, and the only thing that writes it down.
 *
 * State lives in one JSON file in the app's own storage. The home screen widget
 * reads the same file, so the widget shows the user's real city — the pins they
 * dropped and the votes they cast — rather than regenerating a parallel one.
 */
class SpideyRepository(private val context: Context) {

    data class Patrol(
        val id: String,
        val startedAt: Long,
        val endedAt: Long?,
        val route: List<SpideyCore.LatLng>,
        val distanceM: Double,
        val sightingIds: List<String>,
    )

    data class Profile(
        val id: String,
        val handle: String,
        val streakDays: Int,
        val lastPatrolDay: String?,
    )

    data class State(
        val sightings: List<SpideyCore.Sighting>,
        val patrols: List<Patrol>,
        val profile: Profile,
        val home: SpideyCore.LatLng,
        val seededDay: String,
    )

    private val file: File get() = File(context.filesDir, FILE_NAME)

    // ---- lifecycle ---------------------------------------------------------

    /**
     * Loads state, seeding or reseeding as needed. The seed is keyed to the UTC
     * day, so a new day gets a new city — otherwise the map decays to empty and
     * looks broken. Pins the user dropped always survive.
     */
    fun loadOrSeed(home: SpideyCore.LatLng, now: Long = System.currentTimeMillis()): State {
        val stored = runCatching { read() }.getOrNull()
        val today = dayKey(now)

        val movedCities = stored == null ||
            SpideyCore.distanceM(stored.home, home) > 20_000
        val newDay = stored?.seededDay != today

        val sightings = if (stored == null || movedCities || newDay) {
            SpideyCore.seedSightings(home, now) + (stored?.sightings.orEmpty().filterNot { it.isSeeded })
        } else {
            stored.sightings
        }

        val state = State(
            sightings = sightings,
            patrols = stored?.patrols.orEmpty(),
            profile = stored?.profile ?: newProfile(),
            home = home,
            seededDay = today,
        )
        write(state)
        return state
    }

    /** What the widget reads: whatever the app last wrote, or null if never run. */
    fun peek(): State? = runCatching { read() }.getOrNull()

    // ---- actions -----------------------------------------------------------

    fun report(
        state: State,
        at: SpideyCore.LatLng,
        tagId: String,
        note: String,
        onPatrol: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Pair<State, SpideyCore.Sighting> {
        val sighting = SpideyCore.Sighting(
            id = "local-${now.toString(36)}-${Random.nextInt(0xFFFF).toString(16)}",
            lat = at.lat,
            lng = at.lng,
            createdAt = now.toDouble(),
            tag = tagId,
            note = note.trim().ifEmpty { null },
            reporterHandle = state.profile.handle,
            reportedOnPatrol = onPatrol,
            confirms = emptyList(),
            denies = emptyList(),
        )
        return save(state.copy(sightings = listOf(sighting) + state.sightings)) to sighting
    }

    fun vote(
        state: State,
        sightingId: String,
        confirm: Boolean,
        from: SpideyCore.LatLng,
        onPatrol: Boolean,
        now: Long = System.currentTimeMillis(),
    ): State {
        val me = state.profile.id

        val sightings = state.sightings.map { sighting ->
            if (sighting.id != sightingId) return@map sighting

            // One vote per user per sighting, and a change of mind moves it
            // rather than stacking a second one.
            val confirms = sighting.confirms.filterNot { it.userId == me }
            val denies = sighting.denies.filterNot { it.userId == me }
            val vote = SpideyCore.Vote(
                userId = me,
                distanceM = SpideyCore.distanceM(from, sighting.position),
                onPatrol = onPatrol,
                createdAt = now.toDouble(),
            )

            if (confirm) sighting.copy(confirms = confirms + vote, denies = denies)
            else sighting.copy(confirms = confirms, denies = denies + vote)
        }

        return save(state.copy(sightings = sightings))
    }

    fun finishPatrol(state: State, patrol: Patrol, now: Long = System.currentTimeMillis()): State {
        val finished = patrol.copy(endedAt = now)

        // Local days, not UTC: a streak is about the user's evenings.
        val today = localDayKey(now)
        val yesterday = localDayKey(now - 86_400_000L)
        val streak = when (state.profile.lastPatrolDay) {
            today -> state.profile.streakDays
            yesterday -> state.profile.streakDays + 1
            else -> 1
        }

        return save(
            state.copy(
                patrols = listOf(finished) + state.patrols,
                profile = state.profile.copy(streakDays = streak, lastPatrolDay = today),
            ),
        )
    }

    fun reset(state: State, now: Long = System.currentTimeMillis()): State = save(
        state.copy(
            sightings = SpideyCore.seedSightings(state.home, now),
            patrols = emptyList(),
            // The streak counted patrols that no longer exist.
            profile = state.profile.copy(streakDays = 0, lastPatrolDay = null),
            seededDay = dayKey(now),
        ),
    )

    fun save(state: State): State {
        write(state)
        return state
    }

    // ---- persistence -------------------------------------------------------

    private fun newProfile(): Profile {
        val first = listOf("friendly", "nightly", "uptown", "downtown", "crosstown", "rooftop")
        val second = listOf("watcher", "walker", "lurker", "regular", "commuter", "local")
        return Profile(
            id = "local-${Random.nextLong().toString(36).takeLast(8)}",
            handle = "${first.random()}_${second.random()}",
            streakDays = 0,
            lastPatrolDay = null,
        )
    }

    private fun read(): State? {
        if (!file.exists()) return null
        val root = JSONObject(file.readText())

        val sightings = root.getJSONArray("sightings").map { it.toSighting() }
        val patrols = root.optJSONArray("patrols")?.map { it.toPatrol() }.orEmpty()
        val profileJson = root.getJSONObject("profile")

        return State(
            sightings = sightings,
            patrols = patrols,
            profile = Profile(
                id = profileJson.getString("id"),
                handle = profileJson.getString("handle"),
                streakDays = profileJson.optInt("streakDays"),
                lastPatrolDay = profileJson.optStringOrNull("lastPatrolDay"),
            ),
            home = SpideyCore.LatLng(root.getDouble("homeLat"), root.getDouble("homeLng")),
            seededDay = root.optString("seededDay"),
        )
    }

    private fun write(state: State) {
        val root = JSONObject()
        root.put("sightings", JSONArray().apply { state.sightings.forEach { put(it.toJson()) } })
        root.put("patrols", JSONArray().apply { state.patrols.forEach { put(it.toJson()) } })
        root.put(
            "profile",
            JSONObject()
                .put("id", state.profile.id)
                .put("handle", state.profile.handle)
                .put("streakDays", state.profile.streakDays)
                .put("lastPatrolDay", state.profile.lastPatrolDay ?: JSONObject.NULL),
        )
        root.put("homeLat", state.home.lat)
        root.put("homeLng", state.home.lng)
        root.put("seededDay", state.seededDay)

        // Written whole: a half-written file is worse than a stale one.
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(root.toString())
        temp.renameTo(file)
    }

    private fun SpideyCore.Sighting.toJson() = JSONObject()
        .put("id", id)
        .put("lat", lat)
        .put("lng", lng)
        .put("createdAt", createdAt)
        .put("tag", tag)
        .put("note", note ?: JSONObject.NULL)
        .put("handle", reporterHandle)
        .put("onPatrol", reportedOnPatrol)
        .put("confirms", JSONArray().apply { confirms.forEach { put(it.toJson()) } })
        .put("denies", JSONArray().apply { denies.forEach { put(it.toJson()) } })

    private fun SpideyCore.Vote.toJson() = JSONObject()
        .put("userId", userId)
        .put("distanceM", distanceM)
        .put("onPatrol", onPatrol)
        .put("createdAt", createdAt)

    private fun Patrol.toJson() = JSONObject()
        .put("id", id)
        .put("startedAt", startedAt)
        .put("endedAt", endedAt ?: JSONObject.NULL)
        .put("distanceM", distanceM)
        .put(
            "route",
            JSONArray().apply {
                route.forEach { put(JSONObject().put("lat", it.lat).put("lng", it.lng)) }
            },
        )
        .put("sightingIds", JSONArray().apply { sightingIds.forEach { put(it) } })

    private fun JSONObject.toSighting() = SpideyCore.Sighting(
        id = getString("id"),
        lat = getDouble("lat"),
        lng = getDouble("lng"),
        createdAt = getDouble("createdAt"),
        tag = getString("tag"),
        note = optStringOrNull("note"),
        reporterHandle = optString("handle"),
        reportedOnPatrol = optBoolean("onPatrol"),
        confirms = getJSONArray("confirms").map { it.toVote() },
        denies = getJSONArray("denies").map { it.toVote() },
    )

    private fun JSONObject.toVote() = SpideyCore.Vote(
        userId = optString("userId"),
        distanceM = getDouble("distanceM"),
        onPatrol = optBoolean("onPatrol"),
        createdAt = getDouble("createdAt"),
    )

    private fun JSONObject.toPatrol() = Patrol(
        id = getString("id"),
        startedAt = getLong("startedAt"),
        endedAt = if (isNull("endedAt")) null else getLong("endedAt"),
        route = getJSONArray("route").map {
            SpideyCore.LatLng(it.getDouble("lat"), it.getDouble("lng"))
        },
        distanceM = getDouble("distanceM"),
        sightingIds = getJSONArray("sightingIds").let { array ->
            (0 until array.length()).map { array.getString(it) }
        },
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifEmpty { null }

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

    companion object {
        private const val FILE_NAME = "spidey-state.json"

        /** UTC day, matching the seed key the web app and iOS widget use. */
        fun dayKey(timestamp: Long): String {
            val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = timestamp
            return "%04d-%02d-%02d".format(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        }

        /** The day as the user experiences it, for streaks. */
        fun localDayKey(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            return "%04d-%02d-%02d".format(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        }

        /** Ignore GPS jitter below this when accumulating a patrol route. */
        const val MIN_STEP_M = 8.0

        /**
         * Drop fixes vaguer than this while patrolling. A phone falling back to
         * cell-tower positioning reports jumps of hundreds of metres without the
         * user moving, which would otherwise be banked as distance covered.
         */
        const val MAX_ACCURACY_M = 50.0

        val RANKS = listOf(
            0.0 to "Neighborhood Watch",
            5_000.0 to "Friendly Neighborhood",
            20_000.0 to "Web-Head",
            50_000.0 to "Night Shift",
            100_000.0 to "City-Wide",
        )

        fun rankFor(metres: Double): Triple<String, String?, Double> {
            var index = 0
            RANKS.forEachIndexed { i, (at, _) -> if (metres >= at) index = i }
            val current = RANKS[index]
            val next = RANKS.getOrNull(index + 1)
            val progress = if (next == null) 1.0
            else (metres - current.first) / (next.first - current.first)
            return Triple(current.second, next?.second, progress.coerceIn(0.0, 1.0))
        }

        fun formatDuration(ms: Long): String {
            val minutes = max(0L, Math.round(ms / 60_000.0))
            return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
        }
    }
}
