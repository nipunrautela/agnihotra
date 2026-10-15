package com.nipunrautela.agnihotra.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appDataStore by preferencesDataStore("preferences")

data class AppPreferences(
    val activePlaceId: String? = null,
    val homePlaceId: String? = null,
    val sunriseReminder: Boolean = false,
    val sunsetReminder: Boolean = false,
    val preparationMinutes: Int = 10,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val analytics: Boolean = true,
    val theme: String = "system",
    val language: String = "system",
    val timeFormat: String = "system",
    val showSeconds: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showTransliteration: Boolean = true,
    val largeMantraText: Boolean = false,
    val notificationAsked: Boolean = false,
    val travelDismissedUntil: Long = 0,
)

class PreferencesRepository(context: Context) {
    private val store = context.appDataStore
    val preferences = store.data.catch { error ->
        // A failed read must not re-enable analytics for someone who previously opted out.
        if (error is IOException) emit(preferencesOf(analytics to false)) else throw error
    }.map { p ->
        AppPreferences(
            activePlaceId = p[active], homePlaceId = p[home],
            sunriseReminder = p[sunrise] ?: false, sunsetReminder = p[sunset] ?: false,
            preparationMinutes = (p[preparation] ?: 10).coerceIn(0, 60),
            sound = p[sound] ?: true, vibration = p[vibration] ?: true,
            analytics = p[analytics] ?: true, theme = p[theme] ?: "system",
            language = p[language] ?: "system",
            timeFormat = p[timeFormat] ?: "system", showSeconds = p[showSeconds] ?: true,
            keepScreenOn = p[keepScreenOn] ?: false,
            showTransliteration = p[transliteration] ?: true,
            largeMantraText = p[largeMantraText] ?: false,
            notificationAsked = p[notificationAsked] ?: false,
            travelDismissedUntil = p[travelDismissedUntil] ?: 0,
        )
    }

    suspend fun selectPlace(id: String) = store.edit { it[active] = id }
    suspend fun setHome(id: String) = store.edit { it[home] = id }
    suspend fun setBoolean(key: String, value: Boolean) = store.edit { it[booleanPreferencesKey(key)] = value }
    suspend fun setString(key: String, value: String) = store.edit { it[stringPreferencesKey(key)] = value }
    suspend fun setPreparation(minutes: Int) = store.edit { it[preparation] = minutes.coerceIn(0, 60) }
    suspend fun dismissTravel(until: Long) = store.edit { it[travelDismissedUntil] = until }
    suspend fun repairSelection(places: List<SavedPlace>) = store.edit { p ->
        val ids = places.map { it.id }
        if (p[active] !in ids) places.firstOrNull()?.id?.let { p[active] = it } ?: p.remove(active)
        if (p[home] !in ids) places.firstOrNull()?.id?.let { p[home] = it } ?: p.remove(home)
    }

    companion object {
        private val active = stringPreferencesKey("active_place")
        private val home = stringPreferencesKey("home_place")
        private val sunrise = booleanPreferencesKey("sunrise_reminder")
        private val sunset = booleanPreferencesKey("sunset_reminder")
        private val preparation = intPreferencesKey("preparation_minutes")
        private val sound = booleanPreferencesKey("sound")
        private val vibration = booleanPreferencesKey("vibration")
        private val analytics = booleanPreferencesKey("analytics")
        private val theme = stringPreferencesKey("theme")
        private val language = stringPreferencesKey("language")
        private val timeFormat = stringPreferencesKey("time_format")
        private val showSeconds = booleanPreferencesKey("show_seconds")
        private val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        private val transliteration = booleanPreferencesKey("transliteration")
        private val largeMantraText = booleanPreferencesKey("large_mantra_text")
        private val notificationAsked = booleanPreferencesKey("notification_asked")
        private val travelDismissedUntil = longPreferencesKey("travel_dismissed_until")
    }
}
