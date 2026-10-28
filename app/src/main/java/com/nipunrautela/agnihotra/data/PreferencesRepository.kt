package com.nipunrautela.agnihotra.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nipunrautela.agnihotra.core.PracticeSession
import com.nipunrautela.agnihotra.integrations.ReleaseConfiguration

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
    val practiceQuietMinutes: Int = 0,
    val practiceMusicEnabled: Boolean = true,
    val practiceMusicSource: String? = "bundle:om-1",
    val practiceMusicName: String? = null,
    val practiceDurationMinutes: Int = 30,
)

class PreferencesRepository(private val context: Context) {
    private val store = context.appDataStore
    private val musicMutex = Mutex()
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
            practiceQuietMinutes = p[practiceQuiet]?.takeIf { it in PracticeSession.quietOptions } ?: 0,
            practiceMusicEnabled = p[practiceMusicEnabled] ?: true,
            practiceMusicSource = (p[practiceMusicSource] ?: "bundle:om-1").takeUnless { it == "none" },
            practiceMusicName = p[practiceMusicName],
            practiceDurationMinutes = (p[practiceDuration] ?: 30).coerceIn(1, 120),
        )
    }

    suspend fun selectPlace(id: String) = store.edit { it[active] = id }
    suspend fun setHome(id: String) = store.edit { it[home] = id }
    suspend fun setBoolean(key: String, value: Boolean) = store.edit { it[booleanPreferencesKey(key)] = value }
    suspend fun setString(key: String, value: String) = store.edit { it[stringPreferencesKey(key)] = value }
    suspend fun setPreparation(minutes: Int) = store.edit { it[preparation] = minutes.coerceIn(0, 60) }
    suspend fun dismissTravel(until: Long) = store.edit { it[travelDismissedUntil] = until }
    suspend fun setPracticeQuiet(minutes: Int) = store.edit {
        require(minutes in PracticeSession.quietOptions)
        it[practiceQuiet] = minutes
    }
    suspend fun setPracticeDuration(minutes: Int) = store.edit { it[practiceDuration] = minutes.coerceIn(1, 120) }

    /** Keep only the chosen document grant; no broad media/storage permission is needed. */
    suspend fun setPracticeMusic(uri: Uri?) = musicMutex.withLock { withContext(Dispatchers.IO) {
        if (uri != null) require(uri.scheme == "content")
        val old = preferences.first().practiceMusicSource
        val resolver = context.contentResolver
        var acquired = false
        try {
            if (uri != null) {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                acquired = old != uri.toString()
            }
            val name = uri?.let {
                resolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.take(200) else null
                }
            }
            store.edit {
                if (uri == null) { it[practiceMusicSource] = "none"; it.remove(practiceMusicName); it[practiceMusicEnabled] = false }
                else {
                    it[practiceMusicSource] = uri.toString()
                    if (name == null) it.remove(practiceMusicName) else it[practiceMusicName] = name
                }
            }
        } catch (error: Exception) {
            if (acquired) runCatching { resolver.releasePersistableUriPermission(requireNotNull(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            throw error
        }
        releaseOldMusicGrant(old, uri?.toString())
    } }

    suspend fun setBundledPracticeMusic(id: String) = musicMutex.withLock {
        require(ReleaseConfiguration.practiceMusic.any { it.id == id })
        val old = preferences.first().practiceMusicSource
        val source = "bundle:$id"
        store.edit { it[practiceMusicSource] = source; it.remove(practiceMusicName) }
        releaseOldMusicGrant(old, source)
    }

    private fun releaseOldMusicGrant(old: String?, replacement: String?) {
        if (old != replacement && old?.startsWith("content:") == true) runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
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
        private val practiceQuiet = intPreferencesKey("practice_quiet_minutes")
        private val practiceMusicEnabled = booleanPreferencesKey("practice_music_enabled")
        private val practiceMusicSource = stringPreferencesKey("practice_music_source")
        private val practiceMusicName = stringPreferencesKey("practice_music_name")
        private val practiceDuration = intPreferencesKey("practice_duration_minutes")
    }
}
