package com.spidey.tracker

import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * What a single location fix does to the state.
 *
 * Everything a fix decides happens in one place — the spidey-sense alert, the
 * accuracy gate, the step threshold — and they all write to the same state, so
 * they are checked together.
 */
@RunWith(RobolectricTestRunner::class)
class FixHandlingTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val home = DEFAULT_HOME

    private lateinit var model: SpideyViewModel

    @Before
    fun setUp() {
        app.filesDir.deleteRecursively()
        app.filesDir.mkdirs()
        model = SpideyViewModel(app)
        model.start(hasLocation = false)
    }

    private fun fix(at: SpideyCore.LatLng, accuracy: Float? = 5f) =
        Location(LocationManager.GPS_PROVIDER).apply {
            latitude = at.lat
            longitude = at.lng
            if (accuracy != null) this.accuracy = accuracy
        }

    private fun hotAt(at: SpideyCore.LatLng, id: String, at_: Long) = SpideyCore.Sighting(
        id = id,
        lat = at.lat,
        lng = at.lng,
        createdAt = at_.toDouble(),
        tag = "stopped-something",
        note = null,
        reporterHandle = "someone",
        reportedOnPatrol = true,
        confirms = (0 until 14).map { SpideyCore.Vote("v$it", 10.0, true, at_.toDouble()) },
        denies = emptyList(),
    )

    /**
     * Rebuilds the model over a city containing one hot pin a hundred metres from
     * home and nothing else — written through the repository and loaded through
     * start(), so the pin arrives by the same route a real one would.
     */
    private fun armSense(): SpideyCore.Sighting {
        val stamp = System.currentTimeMillis()
        val near = hotAt(SpideyCore.offset(DEFAULT_HOME, 100.0, 0.0), "hot-1", stamp)
        assertTrue(
            "the pin under test has to actually be hot",
            SpideyCore.heatOf(near, stamp) == SpideyCore.Heat.HOT,
        )

        SpideyRepository(app).save(
            SpideyRepository.State(
                sightings = listOf(near),
                patrols = emptyList(),
                profile = SpideyRepository.Profile("local-test", "test_watcher", 0, null),
                home = DEFAULT_HOME,
                seededDay = SpideyRepository.dayKey(stamp),
            ),
        )

        model = SpideyViewModel(app)
        model.start(hasLocation = false)
        assertEquals(listOf("hot-1"), model.state.value.sightings.map { it.id })
        return near
    }

    @Test
    fun `a fix that trips spidey-sense leaves the alert up`() {
        armSense()
        model.onFix(fix(home))

        // The buzz, the notification and the banner are one event. Publishing the
        // alert and then writing a state built from a snapshot taken before it
        // reverted the banner while the phone had already buzzed — and the pin
        // was marked announced, so it could never fire again.
        assertEquals("hot-1", model.state.value.lastSense)
    }

    @Test
    fun `the alert survives a fix that is too vague to count as movement`() {
        armSense()
        model.startPatrol()
        model.onFix(fix(home, accuracy = 500f))

        assertEquals("hot-1", model.state.value.lastSense)
        assertEquals(0.0, model.state.value.activePatrol!!.distanceM, 1e-9)
    }

    @Test
    fun `the alert survives a fix too small to be a step`() {
        armSense()
        model.startPatrol()
        model.onFix(fix(home))
        model.onFix(fix(SpideyCore.offset(home, 2.0, 0.0)))

        assertEquals("hot-1", model.state.value.lastSense)
        assertEquals(0.0, model.state.value.activePatrol!!.distanceM, 1e-9)
    }

    @Test
    fun `a real step is still banked as distance`() {
        model.startPatrol()
        model.onFix(fix(home))
        model.onFix(fix(SpideyCore.offset(home, 40.0, 90.0)))

        assertEquals(40.0, model.state.value.activePatrol!!.distanceM, 1.0)
    }

    @Test
    fun `a vague fix still moves the blue dot without banking distance`() {
        model.startPatrol()
        model.onFix(fix(home))
        val away = SpideyCore.offset(home, 800.0, 0.0)
        model.onFix(fix(away, accuracy = 500f))

        assertEquals(away.lat, model.state.value.position!!.lat, 1e-9)
        assertEquals(0.0, model.state.value.activePatrol!!.distanceM, 1e-9)
    }

    @Test
    fun `spidey-sense off means no alert at all`() {
        armSense()
        model.toggleSense()
        model.onFix(fix(home))

        assertNull(model.state.value.lastSense)
    }
}

/**
 * Formatting that has to read the same on every device, and day keys that have to
 * stay comparable with the ones the web app and the iOS widget write.
 */
@RunWith(RobolectricTestRunner::class)
class LocaleTest {

    private var original: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        original = Locale.getDefault()
    }

    private fun <T> under(locale: Locale, body: () -> T): T {
        Locale.setDefault(locale)
        try {
            return body()
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `distances keep a decimal point on a phone set to a comma locale`() {
        // Left to the default locale this reads "1,5 km" in the middle of an
        // otherwise English interface.
        assertEquals("1.5 km", under(Locale.GERMANY) { SpideyCore.formatDistance(1_500.0) })
        assertEquals("1.5 km", under(Locale.FRANCE) { SpideyCore.formatDistance(1_500.0) })
    }

    @Test
    fun `day keys stay ISO whatever numerals the device prefers`() {
        val arabic = Locale.forLanguageTag("ar-EG-u-nu-arab")
        val stamp = 1_760_000_000_000L

        val expected = SpideyRepository.dayKey(stamp)
        assertTrue("expected an ISO date, got $expected", expected.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(expected, under(arabic) { SpideyRepository.dayKey(stamp) })
        assertEquals(
            SpideyRepository.localDayKey(stamp),
            under(arabic) { SpideyRepository.localDayKey(stamp) },
        )
    }

    @Test
    fun `ages round the way the TypeScript rounds them`() {
        val now = 1_760_000_000_000L
        // 59.6 seconds. Truncating gives 59 and "just now"; the web app rounds to
        // 60 and says "1 min ago", and the two platforms disagree on the same pin.
        assertEquals("1 min ago", SpideyCore.formatAgo(now - 59_600, now))
        assertEquals("just now", SpideyCore.formatAgo(now - 20_000, now))
    }

    @Test
    fun `photos left behind by pins that are gone get swept up`() {
        val app: Application = ApplicationProvider.getApplicationContext()
        app.filesDir.deleteRecursively()
        app.filesDir.mkdirs()

        val repository = SpideyRepository(app)
        val state = repository.loadOrSeed(SpideyCore.LatLng(40.7484, -73.9857))
        val orphan = java.io.File(app.filesDir, "photos").apply { mkdirs() }
            .resolve("gone.png")
        orphan.writeBytes(ByteArray(16))
        assertTrue(orphan.exists())

        // Nothing points at it, and start-up is where the sweep happens. Before
        // this, a photo only ever went away if the user reset the whole app.
        repository.loadOrSeed(state.home)
        assertTrue("orphaned photo should be gone", !orphan.exists())
        assertNotNull(state.profile)
    }
}
