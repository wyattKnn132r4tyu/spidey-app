package com.spidey.tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * The app's own state: persistence, votes, patrols and streaks.
 *
 * These mirror the web app's store tests, because the two implementations have
 * to behave the same way for the same user actions.
 */
@RunWith(RobolectricTestRunner::class)
class SpideyRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: SpideyRepository

    private val home = SpideyCore.LatLng(40.7484, -73.9857)
    private val now = 1_760_000_000_000L

    @Before
    fun setUp() {
        context.filesDir.listFiles()?.forEach(File::delete)
        repository = SpideyRepository(context)
    }

    // ---- seeding -----------------------------------------------------------

    @Test
    fun `seeds a city on first run and writes it down`() {
        val state = repository.loadOrSeed(home, now)

        assertTrue(state.sightings.isNotEmpty())
        assertNotNull(repository.peek())
        assertEquals(state.sightings.size, repository.peek()!!.sightings.size)
    }

    @Test
    fun `reuses the stored city on a second load the same day`() {
        val first = repository.loadOrSeed(home, now).sightings.map { it.id }
        val second = repository.loadOrSeed(home, now).sightings.map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun `reseeds on a new day and keeps pins the user dropped`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.report(state, home, "swinging", "mine", false, now).first

        val nextDay = now + 86_400_000L
        val after = repository.loadOrSeed(home, nextDay)

        assertTrue(after.sightings.any { it.note == "mine" })
        assertTrue(after.sightings.any { it.isSeeded })
        assertEquals(after.sightings.size, after.sightings.map { it.id }.toSet().size)
    }

    @Test
    fun `reseeds around the user after they move to another city`() {
        repository.loadOrSeed(home, now)

        val faraway = SpideyCore.offset(home, 400_000.0, 90.0)
        val after = repository.loadOrSeed(faraway, now)

        for (sighting in after.sightings.filter { it.isSeeded }) {
            assertTrue(SpideyCore.distanceM(faraway, sighting.position) < 10_000)
        }
    }

    @Test
    fun `survives a corrupt state file instead of failing to open`() {
        File(context.filesDir, "spidey-state.json").writeText("{not json")
        val state = repository.loadOrSeed(home, now)
        assertTrue(state.sightings.isNotEmpty())
    }

    // ---- persistence -------------------------------------------------------

    @Test
    fun `round-trips every field through storage`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.report(state, home, "acting-strange", "a note", true, now).first
        state = repository.vote(state, state.sightings[0].id, true, home, false, now)

        val reloaded = repository.peek()!!
        val mine = reloaded.sightings.first { !it.isSeeded }

        assertEquals("acting-strange", mine.tag)
        assertEquals("a note", mine.note)
        assertTrue(mine.reportedOnPatrol)
        assertEquals(1, mine.confirms.size)
        assertEquals(state.profile.handle, mine.reporterHandle)
        assertEquals(state.profile.id, reloaded.profile.id)
    }

    @Test
    fun `keeps seeded votes intact through a save and load`() {
        val state = repository.loadOrSeed(home, now)
        val before = state.sightings.sumOf { it.confirms.size + it.denies.size }
        val after = repository.peek()!!.sightings.sumOf { it.confirms.size + it.denies.size }
        assertEquals(before, after)
    }

    @Test
    fun `preserves confidence exactly across a reload`() {
        val state = repository.loadOrSeed(home, now)
        val before = state.sightings.map { SpideyCore.confidenceOf(it, now) }
        val after = repository.peek()!!.sightings.map { SpideyCore.confidenceOf(it, now) }
        before.zip(after).forEach { (a, b) -> assertEquals(a, b, 1e-12) }
    }

    // ---- reporting and voting ----------------------------------------------

    @Test
    fun `drops a pin with a trimmed note and no note when blank`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.report(state, home, "swinging", "  spaced  ", false, now).first
        assertEquals("spaced", state.sightings[0].note)

        state = repository.report(state, home, "swinging", "   ", false, now).first
        assertNull(state.sightings[0].note)
    }

    @Test
    fun `records the distance a vote was cast from`() {
        var state = repository.loadOrSeed(home, now)
        val target = state.sightings.first()
        state = repository.vote(state, target.id, true, home, false, now)

        val mine = state.sightings.first { it.id == target.id }
            .confirms.first { it.userId == state.profile.id }
        assertEquals(SpideyCore.distanceM(home, target.position), mine.distanceM, 1e-6)
    }

    @Test
    fun `counts one vote per user however many times they tap`() {
        var state = repository.loadOrSeed(home, now)
        val target = state.sightings.first()

        repeat(3) { state = repository.vote(state, target.id, true, home, false, now) }

        val mine = state.sightings.first { it.id == target.id }
            .confirms.filter { it.userId == state.profile.id }
        assertEquals(1, mine.size)
    }

    @Test
    fun `moves the vote rather than stacking when the user changes their mind`() {
        var state = repository.loadOrSeed(home, now)
        val target = state.sightings.first()

        state = repository.vote(state, target.id, true, home, false, now)
        state = repository.vote(state, target.id, false, home, false, now)

        val after = state.sightings.first { it.id == target.id }
        assertEquals(0, after.confirms.count { it.userId == state.profile.id })
        assertEquals(1, after.denies.count { it.userId == state.profile.id })
    }

    @Test
    fun `leaves other sightings untouched when voting`() {
        var state = repository.loadOrSeed(home, now)
        val (first, second) = state.sightings[0] to state.sightings[1]
        val before = second.confirms.size

        state = repository.vote(state, first.id, true, home, false, now)

        assertEquals(before, state.sightings.first { it.id == second.id }.confirms.size)
    }

    // ---- patrols and streaks -----------------------------------------------

    private fun patrol(distance: Double = 500.0) = SpideyRepository.Patrol(
        id = "p-${Math.random()}",
        startedAt = now,
        endedAt = null,
        route = listOf(home),
        distanceM = distance,
        sightingIds = emptyList(),
    )

    /** Local noon on the given date, so a patrol cannot drift across midnight. */
    private fun localNoon(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day, 12, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @Test
    fun `files a patrol and starts the streak at one`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.finishPatrol(state, patrol(), now)

        assertEquals(1, state.patrols.size)
        assertNotNull(state.patrols[0].endedAt)
        assertEquals(1, state.profile.streakDays)
        assertEquals(1, repository.peek()!!.patrols.size)
    }

    @Test
    fun `does not double count two patrols on the same day`() {
        var state = repository.loadOrSeed(home, now)
        repeat(3) { state = repository.finishPatrol(state, patrol(), now) }
        assertEquals(1, state.profile.streakDays)
    }

    @Test
    fun `extends the streak on consecutive days`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.finishPatrol(state, patrol(), localNoon(2026, 8, 17))
        state = repository.finishPatrol(state, patrol(), localNoon(2026, 8, 18))
        assertEquals(2, state.profile.streakDays)
    }

    @Test
    fun `resets the streak after a missed day`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.finishPatrol(state, patrol(), localNoon(2026, 8, 17))
        state = repository.finishPatrol(state, patrol(), localNoon(2026, 8, 20))
        assertEquals(1, state.profile.streakDays)
    }

    @Test
    fun `counts an evening crossing UTC midnight as one local day`() {
        // Streaks are about the user's evening, not Greenwich's date. Both of
        // these are the same evening in any timezone west of UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            var state = repository.loadOrSeed(home, now)
            val evening = 1_755_468_000_000L // 2025-08-17 22:00 UTC = 18:00 local
            val lateEvening = evening + 4 * 3_600_000L // 02:00 UTC next day, 22:00 local

            state = repository.finishPatrol(state, patrol(), evening)
            state = repository.finishPatrol(state, patrol(), lateEvening)

            assertEquals(1, state.profile.streakDays)
        } finally {
            TimeZone.setDefault(null)
        }
    }

    @Test
    fun `reads pins saved under a tag that no longer exists`() {
        // Anyone upgrading from an earlier build has pins tagged "suit-spotted",
        // which is not in the catalogue any more. They must still load, and still
        // score, rather than taking the whole file down with them.
        var state = repository.loadOrSeed(home, now)
        state = repository.report(state, home, "suit-spotted", "old pin", false, now).first

        val reloaded = repository.peek()!!
        val old = reloaded.sightings.first { it.note == "old pin" }

        assertEquals("suit-spotted", old.tag)
        assertTrue(SpideyCore.confidenceOf(old, now) > 0)
        assertNotNull(SpideyCore.tagMeta(old.tag).label)
    }

    @Test
    fun `keeps a running patrol so it survives the process being killed`() {
        var state = repository.loadOrSeed(home, now)
        val running = patrol(750.0).copy(id = "in-progress")

        state = repository.save(state.copy(activePatrol = running))

        val reloaded = repository.loadOrSeed(home, now)
        assertEquals("in-progress", reloaded.activePatrol?.id)
        assertEquals(750.0, reloaded.activePatrol!!.distanceM, 1e-9)
        assertEquals(1, reloaded.activePatrol!!.route.size)
    }

    @Test
    fun `clears the running patrol once it is filed`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.save(state.copy(activePatrol = patrol()))
        state = repository.finishPatrol(state.copy(activePatrol = null), patrol(), now)

        assertNull(repository.peek()!!.activePatrol)
        assertEquals(1, repository.peek()!!.patrols.size)
    }

    @Test
    fun `reset clears history, reseeds and drops the streak with it`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.report(state, home, "swinging", "mine", false, now).first
        state = repository.finishPatrol(state, patrol(), now)

        state = repository.reset(state, now)

        assertTrue(state.patrols.isEmpty())
        assertEquals(0, state.profile.streakDays)
        assertNull(state.profile.lastPatrolDay)
        assertTrue(state.sightings.all { it.isSeeded })
        assertTrue(state.sightings.isNotEmpty())
    }
}
