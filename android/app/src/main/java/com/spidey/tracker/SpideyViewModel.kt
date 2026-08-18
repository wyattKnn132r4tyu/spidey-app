package com.spidey.tracker

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class Tab { MAP, BUGLE, PATROL }

data class UiState(
    val ready: Boolean = false,
    val home: SpideyCore.LatLng = DEFAULT_HOME,
    val position: SpideyCore.LatLng? = null,
    val locationDenied: Boolean = false,
    val sightings: List<SpideyCore.Sighting> = emptyList(),
    val patrols: List<SpideyRepository.Patrol> = emptyList(),
    val activePatrol: SpideyRepository.Patrol? = null,
    val profile: SpideyRepository.Profile? = null,
    val tab: Tab = Tab.MAP,
    val selectedId: String? = null,
    val reporting: Boolean = false,
    /** Heat bands the user has switched off with the edge tabs. */
    val hiddenHeats: Set<SpideyCore.Heat> = emptySet(),
    val soundOn: Boolean = true,
    val senseOn: Boolean = true,
    /** Set when the map should jump back to the user; cleared once consumed. */
    val recenterAt: SpideyCore.LatLng? = null,
    /** A photo taken but not yet attached to a report. */
    val pendingPhoto: android.graphics.Bitmap? = null,
    val lastSense: String? = null,
    val mapCentre: SpideyCore.LatLng? = null,
    /** Bumped on a timer so decaying values recompose. */
    val clock: Long = System.currentTimeMillis(),
) {
    val live get() = sightings.filter {
        SpideyCore.isLive(it, clock) && SpideyCore.heatOf(it, clock) !in hiddenHeats
    }

    /** Everything alive, ignoring the filters — what the counters should say. */
    val allLive get() = sightings.filter { SpideyCore.isLive(it, clock) }
    val selected get() = sightings.firstOrNull { it.id == selectedId }
    val totalDistanceM get() = patrols.sumOf { it.distanceM } + (activePatrol?.distanceM ?: 0.0)
}

/** Midtown, used only when there is no fix at all. */
val DEFAULT_HOME = SpideyCore.LatLng(40.7484, -73.9857)

class SpideyViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SpideyRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var stored: SpideyRepository.State? = null
    private var clockJob: Job? = null
    private var listening = false
    private var foreground = false

    // ---- lifecycle ---------------------------------------------------------

    /**
     * Called once the activity knows whether it has location permission.
     *
     * Guarded against a second run: the activity's LaunchedEffect fires again
     * after a rotation, and reloading here would throw away an in-progress
     * patrol along with the rest of the live state.
     */
    fun start(hasLocation: Boolean) {
        if (_state.value.ready) {
            if (hasLocation && _state.value.locationDenied) onLocationGranted()
            return
        }

        val fix = if (hasLocation) lastKnown() else null
        val home = fix ?: _state.value.home

        val loaded = repository.loadOrSeed(home)
        stored = loaded

        _state.value = _state.value.copy(
            ready = true,
            home = loaded.home,
            position = fix,
            locationDenied = !hasLocation,
            sightings = loaded.sightings,
            patrols = loaded.patrols,
            // A patrol that was running when the process died is picked back up.
            activePatrol = loaded.activePatrol,
            profile = loaded.profile,
            clock = System.currentTimeMillis(),
        )

        if (hasLocation) listen()
    }

    /**
     * Patrols are foreground-only by design, so the location watch and the decay
     * clock both stop with the app rather than draining the battery behind it.
     */
    fun onForeground() {
        foreground = true
        _state.value = _state.value.copy(clock = System.currentTimeMillis())
        startClock()
        if (!_state.value.locationDenied) listen()
    }

    fun onBackground() {
        foreground = false
        clockJob?.cancel()
        clockJob = null
        stopListening()
        // An in-progress patrol survives the process being killed while away.
        persistActivePatrol()
    }

    private fun startClock() {
        if (clockJob != null) return
        clockJob = viewModelScope.launch {
            while (true) {
                delay(15_000)
                _state.value = _state.value.copy(clock = System.currentTimeMillis())
            }
        }
    }

    override fun onCleared() {
        clockJob?.cancel()
        stopListening()
        super.onCleared()
    }

    // ---- location ----------------------------------------------------------

    private val locationManager
        get() = getApplication<Application>()
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val listener = LocationListener { location -> onFix(location) }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): SpideyCore.LatLng? = runCatching {
        locationManager.getProviders(true)
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { SpideyCore.LatLng(it.latitude, it.longitude) }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun listen() {
        if (listening) return
        runCatching {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> return
            }
            locationManager.requestLocationUpdates(provider, 3_000L, 5f, listener)
            listening = true
        }
    }

    private fun stopListening() {
        if (!listening) return
        runCatching { locationManager.removeUpdates(listener) }
        listening = false
    }

    private val sense by lazy { SpideySense(getApplication()) }

    private fun onFix(location: Location) {
        val at = SpideyCore.LatLng(location.latitude, location.longitude)
        val current = _state.value
        val patrol = current.activePatrol

        if (current.senseOn) {
            sense.check(at, current.allLive, current.clock)?.let { near ->
                blip(SpideySounds.Blip.SENSE)
                _state.value = _state.value.copy(lastSense = near.id)
            }
        }

        if (patrol == null) {
            _state.value = current.copy(position = at, locationDenied = false)
            return
        }

        // A vague fix is not movement: a phone on cell-tower positioning jumps
        // hundreds of metres while the user stands still.
        if (location.hasAccuracy() && location.accuracy > SpideyRepository.MAX_ACCURACY_M) {
            _state.value = current.copy(position = at, locationDenied = false)
            return
        }

        val last = patrol.route.lastOrNull()
        val step = if (last == null) 0.0 else SpideyCore.distanceM(last, at)
        if (last != null && step < SpideyRepository.MIN_STEP_M) {
            _state.value = current.copy(position = at, locationDenied = false)
            return
        }

        _state.value = current.copy(
            position = at,
            locationDenied = false,
            activePatrol = patrol.copy(
                route = patrol.route + at,
                // Accumulated, not recomputed: re-measuring the whole route on
                // every fix is quadratic across a long patrol.
                distanceM = patrol.distanceM + step,
            ),
        )
    }

    // ---- actions -----------------------------------------------------------

    fun setTab(tab: Tab) {
        _state.value = _state.value.copy(tab = tab)
    }

    fun select(id: String?) {
        _state.value = _state.value.copy(selectedId = id)
        if (id != null) blip(SpideySounds.Blip.TAP)
    }

    fun setReporting(open: Boolean) {
        _state.value = _state.value.copy(reporting = open)
    }

    fun toggleSound() {
        val on = !_state.value.soundOn
        _state.value = _state.value.copy(soundOn = on)
        if (on) blip(SpideySounds.Blip.TAP)
    }

    fun toggleSense() {
        _state.value = _state.value.copy(senseOn = !_state.value.senseOn)
        blip(SpideySounds.Blip.TAP)
    }

    /** Edge tabs: tap a band to hide it, tap again to bring it back. */
    fun toggleHeatFilter(heat: SpideyCore.Heat) {
        val hidden = _state.value.hiddenHeats.toMutableSet()
        if (!hidden.add(heat)) hidden.remove(heat)
        _state.value = _state.value.copy(hiddenHeats = hidden, selectedId = null)
        blip(SpideySounds.Blip.TAP)
    }

    fun requestRecenter() {
        val at = _state.value.position ?: return
        _state.value = _state.value.copy(recenterAt = at)
        blip(SpideySounds.Blip.TAP)
    }

    fun recenterHandled() {
        _state.value = _state.value.copy(recenterAt = null)
    }

    fun setPendingPhoto(bitmap: android.graphics.Bitmap?) {
        _state.value = _state.value.copy(pendingPhoto = bitmap)
        if (bitmap != null) blip(SpideySounds.Blip.CONFIRM)
    }

    fun photoFor(sighting: SpideyCore.Sighting): android.graphics.Bitmap? =
        sighting.photo?.let { repository.readPhoto(it) }

    private fun blip(blip: SpideySounds.Blip) {
        if (_state.value.soundOn) SpideySounds.play(blip)
    }

    fun setMapCentre(at: SpideyCore.LatLng) {
        _state.value = _state.value.copy(mapCentre = at)
    }

    fun report(tagId: String, note: String) {
        val current = _state.value
        val base = stored ?: return
        // Without a fix the pin goes where the user is looking, which is what the
        // report sheet says will happen.
        val at = current.position ?: current.mapCentre ?: current.home

        // The photo is captured before the pin exists, so it is written under the
        // new id and the reference stored on the sighting.
        val id = "local-${System.currentTimeMillis().toString(36)}"
        val photoName = current.pendingPhoto?.let { repository.writePhoto(id, it) }

        val (next, sighting) = repository.report(
            base.copy(sightings = current.sightings, activePatrol = current.activePatrol),
            at,
            tagId,
            note,
            current.activePatrol != null,
            photo = photoName,
        )
        stored = next
        blip(SpideySounds.Blip.DROP)

        _state.value = current.copy(
            sightings = next.sightings,
            reporting = false,
            pendingPhoto = null,
            selectedId = sighting.id,
            activePatrol = current.activePatrol?.let {
                it.copy(sightingIds = it.sightingIds + sighting.id)
            },
        )
    }

    fun vote(sightingId: String, confirm: Boolean) {
        val current = _state.value
        val base = stored ?: return

        val next = repository.vote(
            base.copy(sightings = current.sightings, activePatrol = current.activePatrol),
            sightingId,
            confirm,
            current.position ?: current.home,
            current.activePatrol != null,
        )
        stored = next
        blip(if (confirm) SpideySounds.Blip.CONFIRM else SpideySounds.Blip.DENY)
        _state.value = current.copy(sightings = next.sightings)
    }

    fun startPatrol() {
        val current = _state.value
        val patrol = SpideyRepository.Patrol(
            id = "patrol-${System.currentTimeMillis().toString(36)}-${Random.nextInt(999)}",
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            route = listOfNotNull(current.position),
            distanceM = 0.0,
            sightingIds = emptyList(),
        )
        _state.value = current.copy(activePatrol = patrol, tab = Tab.MAP)
        persistActivePatrol()
        listen()
    }

    fun stopPatrol() {
        val current = _state.value
        val patrol = current.activePatrol ?: return
        val base = stored ?: return

        val next = repository.finishPatrol(
            base.copy(sightings = current.sightings, activePatrol = null),
            patrol,
        )
        stored = next

        _state.value = current.copy(
            activePatrol = null,
            patrols = next.patrols,
            profile = next.profile,
            tab = Tab.PATROL,
        )
    }

    fun reset() {
        val base = stored ?: return
        val next = repository.reset(base)
        repository.prunePhotos(next)
        stored = next
        _state.value = _state.value.copy(
            sightings = next.sightings,
            patrols = emptyList(),
            activePatrol = null,
            selectedId = null,
            profile = next.profile,
        )
    }

    fun onLocationGranted() {
        val fix = lastKnown()
        if (fix != null) _state.value = _state.value.copy(position = fix, locationDenied = false)
        else _state.value = _state.value.copy(locationDenied = false)
        listen()
    }

    private fun persistActivePatrol() {
        val base = stored ?: return
        val current = _state.value
        stored = repository.save(
            base.copy(sightings = current.sightings, activePatrol = current.activePatrol),
        )
    }
}
