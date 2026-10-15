package com.nipunrautela.agnihotra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nipunrautela.agnihotra.data.PreferencesRepository
import com.nipunrautela.agnihotra.data.SavedPlace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = PreferencesRepository(context)
    private val home = SavedPlace("test-home", "घर", 28.6139, 77.209)
    private val away = SavedPlace("test-away", "Away", 19.076, 72.8777)

    @Before fun reset() { runBlocking {
        repository.repairSelection(emptyList())
        repository.setBoolean("sunrise_reminder", false)
        repository.setBoolean("sunset_reminder", false)
    } }

    @After fun cleanUp() { runBlocking {
        repository.repairSelection(emptyList())
        repository.setString("language", "en")
        repository.setString("time_format", "system")
        repository.setBoolean("show_seconds", true)
        repository.setBoolean("keep_screen_on", false)
        repository.setBoolean("analytics", true)
        repository.setPreparation(10)
        repository.dismissTravel(0)
    } }

    @Test fun selectedPlaceCanChangeWithoutMovingHome() { runBlocking {
        repository.repairSelection(listOf(home, away))
        repository.setHome(home.id)
        repository.selectPlace(away.id)
        val restored = PreferencesRepository(context).preferences.first()
        assertEquals(home.id, restored.homePlaceId)
        assertEquals(away.id, restored.activePlaceId)
    } }

    @Test fun deletingSelectedPlaceRepairsSelectionAndEmptyListClearsBothIds() { runBlocking {
        repository.setHome(home.id)
        repository.selectPlace(away.id)
        repository.repairSelection(listOf(home))
        assertEquals(home.id, repository.preferences.first().activePlaceId)
        repository.repairSelection(emptyList())
        val empty = repository.preferences.first()
        assertNull(empty.activePlaceId)
        assertNull(empty.homePlaceId)
    } }

    @Test fun readingAndAnalyticsPreferencesSurviveRepositoryRecreation() { runBlocking {
        repository.setString("language", "hi")
        repository.setString("time_format", "24")
        repository.setBoolean("show_seconds", false)
        repository.setBoolean("keep_screen_on", true)
        repository.setBoolean("analytics", false)
        repository.dismissTravel(1_800_000_000)
        val restored = PreferencesRepository(context).preferences.first()
        assertEquals("hi", restored.language)
        assertEquals("24", restored.timeFormat)
        assertFalse(restored.showSeconds)
        assertTrue(restored.keepScreenOn)
        assertFalse(restored.analytics)
        assertEquals(1_800_000_000L, restored.travelDismissedUntil)
    } }

    @Test fun preparationMinutesStayWithinSupportedBounds() { runBlocking {
        repository.setPreparation(-1)
        assertEquals(0, repository.preferences.first().preparationMinutes)
        repository.setPreparation(100)
        assertEquals(60, repository.preferences.first().preparationMinutes)
    } }
}
