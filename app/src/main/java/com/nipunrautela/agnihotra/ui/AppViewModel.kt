package com.nipunrautela.agnihotra.ui

import android.location.Address
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nipunrautela.agnihotra.AppGraph
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.analytics.UsageEvent
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.location.LocationFix
import com.nipunrautela.agnihotra.location.LocationService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Locale

data class AppState(val loading: Boolean = true, val places: List<SavedPlace> = emptyList(), val preferences: AppPreferences = AppPreferences()) {
    val activePlace get() = places.firstOrNull { it.id == preferences.activePlaceId } ?: places.firstOrNull()
}
data class TravelSuggestion(val fix: LocationFix, val distanceKm: Int, val nearestPlace: SavedPlace? = null)

class AppViewModel(val graph: AppGraph, private val location: LocationService) : ViewModel() {
    val state = combine(graph.places.observe(), graph.preferences.preferences) { places, prefs ->
        AppState(false, places, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<Int?>(null)
    val locationFix = MutableStateFlow<LocationFix?>(null)
    val searchResults = MutableStateFlow<List<Address>>(emptyList())
    val travel = MutableStateFlow<TravelSuggestion?>(null)
    val savingPlace = MutableStateFlow(false)
    val placeSaveFailed = MutableStateFlow(false)
    val savedPlaceId = MutableStateFlow<String?>(null)
    private var lastTravelCheck = 0L
    private var locationJob: Job? = null
    private var travelJob: Job? = null
    private var searchJob: Job? = null
    private var lookupGeneration = 0
    private var areaJob: Job? = null
    private val areaAttempts = mutableMapOf<String, Long>()
    private val analyticsChoiceMutex = Mutex()

    fun save(place: SavedPlace, makeHome: Boolean = false) = viewModelScope.launch {
        if (savingPlace.value) return@launch
        savingPlace.value = true
        placeSaveFailed.value = false
        try {
            graph.places.save(place)
            graph.preferences.repairSelection(graph.places.all())
            graph.preferences.selectPlace(place.id)
            if (makeHome) graph.preferences.setHome(place.id)
            graph.analytics.record(UsageEvent.LOCATION_SETUP_COMPLETED)
            savedPlaceId.value = place.id
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { placeSaveFailed.value = true }
        finally { savingPlace.value = false }
    }
    fun select(id: String) = changeSettings { graph.preferences.selectPlace(id) }
    fun home(id: String) = changeSettings { graph.preferences.setHome(id) }
    fun delete(id: String) = changeSettings {
        graph.places.delete(id)
        graph.preferences.repairSelection(graph.places.all())
    }
    fun boolean(key: String, value: Boolean) = changeSettings {
        if (key == "analytics") analyticsChoiceMutex.withLock {
            graph.analytics.beginChoice()
            graph.preferences.setBoolean(key, value)
            graph.analytics.choicePersisted(value)
        } else graph.preferences.setBoolean(key, value)
        if (key in setOf("sunrise_reminder", "sunset_reminder", "sound", "vibration"))
            graph.analytics.record(UsageEvent.REMINDER_SETTINGS_CHANGED)
    }
    fun string(key: String, value: String) = changeSettings { graph.preferences.setString(key, value) }
    fun preparation(minutes: Int) = changeSettings { graph.preferences.setPreparation(minutes) }
    private fun changeSettings(change: suspend () -> Unit) = viewModelScope.launch {
        try { change() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message.value = R.string.settings_save_failed }
    }
    fun locate() {
        cancelPlaceLookup()
        travelJob?.cancel()
        val generation = lookupGeneration
        locationJob = viewModelScope.launch {
            busy.value = true
            try {
                val fix = location.current()
                locationFix.value = fix
                if (fix == null) message.value = R.string.location_unavailable
                else {
                    // Coordinates are usable immediately, even while the optional name lookup runs.
                    busy.value = false
                    val areaName = location.areaName(fix.coordinates, locationLocale())
                    if (lookupGeneration == generation && locationFix.value?.elapsedRealtimeNanos == fix.elapsedRealtimeNanos)
                        locationFix.value = fix.copy(areaName = areaName)
                }
            } finally { if (lookupGeneration == generation) busy.value = false }
        }
    }
    fun search(query: String) {
        cancelPlaceLookup()
        searchResults.value = emptyList()
        val generation = lookupGeneration
        searchJob = viewModelScope.launch {
            if (query.isBlank()) return@launch
            busy.value = true
            try {
                searchResults.value = location.search(query)
                if (searchResults.value.isEmpty()) message.value = R.string.search_unavailable
            } finally { if (lookupGeneration == generation) busy.value = false }
        }
    }
    fun checkTravel() {
        travelJob?.cancel()
        travelJob = viewModelScope.launch {
            graph.refresh()
            val now = android.os.SystemClock.elapsedRealtime()
            if (!location.hasPermission() ||
                (lastTravelCheck != 0L && now - lastTravelCheck < 30 * 60_000) || busy.value) return@launch
            if (Instant.now().epochSecond < state.value.preferences.travelDismissedUntil) return@launch
            val home = state.value.places.firstOrNull { it.id == state.value.preferences.homePlaceId } ?: return@launch
            val fix = location.current() ?: return@launch
            // A canceled/failed foreground fix must not suppress the next resume attempt.
            lastTravelCheck = now
            val distance = home.coordinates.distanceKmTo(fix.coordinates)
            if (distance - fix.accuracyMeters / 1000.0 >= 25) {
                // Already using a nearby saved place: avoid repeated suggestions to switch.
                val activeDistance = state.value.activePlace?.coordinates?.distanceKmTo(fix.coordinates)
                if (state.value.activePlace?.id == home.id || activeDistance == null || activeDistance > 25)
                    travel.value = TravelSuggestion(fix, distance.toInt(), state.value.places
                        .filter { it.id != home.id }
                        .minByOrNull { it.coordinates.distanceKmTo(fix.coordinates) }
                        ?.takeIf { it.coordinates.distanceKmTo(fix.coordinates) <= 25 })
            }
        }
    }

    fun stopLocation() {
        cancelPlaceLookup()
        travelJob?.cancel()
        areaJob?.cancel()
    }

    private fun locationLocale(): Locale = state.value.preferences.language.let {
        if (it in setOf("en", "hi")) Locale.forLanguageTag(it) else Locale.getDefault()
    }

    fun refreshAreaName() {
        val current = state.value
        val place = current.activePlace ?: return
        if (!place.fromCurrentLocation || place.areaName != null) return
        val key = "${place.id}:${place.latitude}:${place.longitude}"
        val now = android.os.SystemClock.elapsedRealtime()
        if (areaAttempts[key]?.let { now - it < 10 * 60_000 } == true) return
        areaAttempts[key] = now
        areaJob?.cancel()
        areaJob = viewModelScope.launch {
            try {
                val area = location.areaName(place.coordinates, locationLocale()) ?: return@launch
                graph.places.updateArea(place.id, place.latitude, place.longitude, area)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Optional metadata; keep saved coordinates usable. */ }
        }
    }

    fun useCurrentFix(fix: LocationFix) {
        cancelPlaceLookup()
        val generation = lookupGeneration
        locationFix.value = fix
        locationJob = viewModelScope.launch {
            val area = location.areaName(fix.coordinates, locationLocale())
            if (lookupGeneration == generation) locationFix.value = fix.copy(areaName = area)
        }
    }

    fun dismissTravel() = changeSettings {
        travel.value = null
        graph.preferences.dismissTravel(Instant.now().plusSeconds(12 * 3600).epochSecond)
    }

    fun cancelPlaceLookup() {
        lookupGeneration++
        locationJob?.cancel()
        searchJob?.cancel()
        busy.value = false
    }
}
