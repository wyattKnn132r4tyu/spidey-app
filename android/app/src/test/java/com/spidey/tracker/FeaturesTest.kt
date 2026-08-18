package com.spidey.tracker

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Photos, filters and spidey-sense. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeaturesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: SpideyRepository

    private val home = SpideyCore.LatLng(40.7484, -73.9857)
    private val now = 1_760_000_000_000L

    @Before
    fun setUp() {
        context.filesDir.deleteRecursively()
        context.filesDir.mkdirs()
        repository = SpideyRepository(context)
    }

    private fun bitmap(color: Int = android.graphics.Color.RED): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    // ---- photos ------------------------------------------------------------

    @Test
    fun `stores a photo and reads it back with the sighting`() {
        var state = repository.loadOrSeed(home, now)
        val name = repository.writePhoto("local-abc", bitmap())
        assertNotNull(name)

        state = repository.report(state, home, "rooftop", "", false, now, photo = name).first
        val saved = repository.peek()!!.sightings.first { !it.isSeeded }

        assertEquals(name, saved.photo)
        assertNotNull(repository.readPhoto(saved.photo!!))
    }

    @Test
    fun `a sighting with no photo stays null through a save and load`() {
        var state = repository.loadOrSeed(home, now)
        state = repository.report(state, home, "swinging", "no photo", false, now).first
        assertNull(repository.peek()!!.sightings.first { !it.isSeeded }.photo)
    }

    @Test
    fun `reading a photo that is not there returns null rather than throwing`() {
        assertNull(repository.readPhoto("nothing-here.png"))
    }

    @Test
    fun `pruning deletes photos no sighting points at any more`() {
        var state = repository.loadOrSeed(home, now)
        val keep = repository.writePhoto("local-keep", bitmap())!!
        val orphan = repository.writePhoto("local-orphan", bitmap())!!

        state = repository.report(state, home, "rooftop", "", false, now, photo = keep).first
        repository.prunePhotos(state)

        assertTrue(repository.photoFile(keep).exists())
        assertFalse("an orphaned photo should not linger", repository.photoFile(orphan).exists())
    }

    @Test
    fun `reset clears the photos along with the pins`() {
        var state = repository.loadOrSeed(home, now)
        val name = repository.writePhoto("local-x", bitmap())!!
        state = repository.report(state, home, "rooftop", "", false, now, photo = name).first

        state = repository.reset(state, now)
        repository.prunePhotos(state)

        assertFalse(repository.photoFile(name).exists())
    }

    // ---- filters -----------------------------------------------------------

    private fun uiState(): UiState {
        val state = repository.loadOrSeed(home, now)
        return UiState(ready = true, home = home, sightings = state.sightings, clock = now)
    }

    @Test
    fun `hiding a band removes exactly that band from the map`() {
        val base = uiState()
        val hotCount = base.allLive.count { SpideyCore.heatOf(it, now) == SpideyCore.Heat.HOT }
        assertTrue("fixture should contain hot pins", hotCount > 0)

        val filtered = base.copy(hiddenHeats = setOf(SpideyCore.Heat.HOT))

        assertEquals(base.allLive.size - hotCount, filtered.live.size)
        assertTrue(filtered.live.none { SpideyCore.heatOf(it, now) == SpideyCore.Heat.HOT })
        // The counters ignore the filter, so you can still see what is hidden.
        assertEquals(base.allLive.size, filtered.allLive.size)
    }

    @Test
    fun `hiding every band empties the map without losing the data`() {
        val filtered = uiState().copy(
            hiddenHeats = setOf(SpideyCore.Heat.HOT, SpideyCore.Heat.WARM, SpideyCore.Heat.COLD),
        )
        assertTrue(filtered.live.isEmpty())
        assertTrue(filtered.allLive.isNotEmpty())
    }

    // ---- spidey-sense ------------------------------------------------------

    private fun hotSighting(at: SpideyCore.LatLng, id: String = "hot-1") = SpideyCore.Sighting(
        id = id,
        lat = at.lat,
        lng = at.lng,
        createdAt = now.toDouble(),
        tag = "stopped-something",
        note = null,
        reporterHandle = "someone",
        reportedOnPatrol = true,
        confirms = (0 until 14).map { SpideyCore.Vote("v$it", 10.0, true, now.toDouble()) },
        denies = emptyList(),
    )

    @Test
    fun `fires for a hot sighting inside the radius`() {
        val sense = SpideySense(context)
        val near = hotSighting(SpideyCore.offset(home, 120.0, 0.0))

        assertEquals(near.id, sense.check(home, listOf(near), now)?.id)
    }

    @Test
    fun `stays quiet for a hot sighting beyond the radius`() {
        val sense = SpideySense(context)
        val far = hotSighting(SpideyCore.offset(home, 3_000.0, 0.0))

        assertNull(sense.check(home, listOf(far), now))
    }

    @Test
    fun `fires once per sighting rather than on every fix`() {
        val sense = SpideySense(context)
        val near = hotSighting(SpideyCore.offset(home, 100.0, 0.0))

        assertNotNull(sense.check(home, listOf(near), now))
        assertNull("a second fix must not buzz again", sense.check(home, listOf(near), now))

        sense.forget(near.id)
        assertNotNull("forgetting lets it buzz again once it reheats", sense.check(home, listOf(near), now))
    }

    @Test
    fun `ignores pins that are merely warm`() {
        val sense = SpideySense(context)
        val warm = hotSighting(SpideyCore.offset(home, 100.0, 0.0)).copy(
            confirms = listOf(SpideyCore.Vote("v", 200.0, false, now.toDouble())),
        )
        assertTrue(SpideyCore.heatOf(warm, now) != SpideyCore.Heat.HOT)
        assertNull(sense.check(home, listOf(warm), now))
    }

    @Test
    fun `picks the nearest hot sighting when several are close`() {
        val sense = SpideySense(context)
        val near = hotSighting(SpideyCore.offset(home, 90.0, 0.0), "near")
        val nearer = hotSighting(SpideyCore.offset(home, 30.0, 90.0), "nearer")

        assertEquals("nearer", sense.check(home, listOf(near, nearer), now)?.id)
    }
}
