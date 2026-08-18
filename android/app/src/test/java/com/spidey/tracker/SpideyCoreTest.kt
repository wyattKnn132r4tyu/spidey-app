package com.spidey.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the Kotlin port produces the same sightings as the app's TypeScript.
 *
 * Regenerate the fixture with `npm run build:parity` from the repo root after
 * changing anything in src/lib.
 */
class SpideyCoreTest {

    private val home = SpideyCore.LatLng(ParityFixture.HOME_LAT, ParityFixture.HOME_LNG)
    private val sightings = SpideyCore.seedSightings(home, ParityFixture.NOW)

    @Test
    fun `generates the same number of sightings`() {
        assertEquals(ParityFixture.LAT.size, sightings.size)
    }

    @Test
    fun `generates sightings at the same positions`() {
        sightings.forEachIndexed { i, sighting ->
            // Sub-millimetre: any real divergence in the random sequence would be
            // hundreds of metres, not a rounding difference.
            assertEquals("lat[$i]", ParityFixture.LAT[i], sighting.lat, 1e-9)
            assertEquals("lng[$i]", ParityFixture.LNG[i], sighting.lng, 1e-9)
        }
    }

    @Test
    fun `generates the same ages and vote counts`() {
        sightings.forEachIndexed { i, sighting ->
            assertEquals("createdAt[$i]", ParityFixture.CREATED_AT[i], sighting.createdAt, 1e-6)
            assertEquals("confirms[$i]", ParityFixture.CONFIRMS[i], sighting.confirms.size)
            assertEquals("denies[$i]", ParityFixture.DENIES[i], sighting.denies.size)
        }
    }

    @Test
    fun `computes the same confidence`() {
        sightings.forEachIndexed { i, sighting ->
            assertEquals(
                "confidence[$i]",
                ParityFixture.CONFIDENCE[i],
                SpideyCore.confidenceOf(sighting, ParityFixture.NOW),
                1e-12,
            )
        }
    }

    @Test
    fun `puts the same number of sightings in each heat band`() {
        val summary = SpideyCore.summarise(home, ParityFixture.NOW)
        assertEquals("hot", ParityFixture.HOT, summary.hot)
        assertEquals("warm", ParityFixture.WARM, summary.warm)
        assertEquals("cold", ParityFixture.COLD, summary.cold)
    }

    @Test
    fun `confidence decays by half over one half-life`() {
        val sighting = sightings.first()
        val now = ParityFixture.NOW
        val score = SpideyCore.scoreOf(sighting, now)
        val later = SpideyCore.scoreOf(sighting, now + SpideyCore.HALF_LIFE_MS.toLong())
        assertEquals(score / 2, later, score * 1e-6)
    }
}
