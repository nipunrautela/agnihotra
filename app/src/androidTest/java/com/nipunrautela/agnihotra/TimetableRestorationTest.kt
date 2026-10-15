package com.nipunrautela.agnihotra

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nipunrautela.agnihotra.core.SolarCalculator
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.ui.AgnihotraAppearance
import com.nipunrautela.agnihotra.ui.TimetablePage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TimetableRestorationTest {
    @get:Rule val compose = createComposeRule()
    private val place = SavedPlace("test-home", "Delhi home", 28.6139, 77.209)
    private val prefs = AppPreferences(language = "en", timeFormat = "24")

    @Test fun browsedMonthSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val nextMonth = YearMonth.now(place.zone).plusMonths(1)
        restoration.setContent {
            AgnihotraAppearance(prefs) { TimetablePage(place, SolarCalculator(), prefs) }
        }
        compose.onNodeWithContentDescription("Next month").performClick()
        val label = nextMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        compose.onNodeWithText(label).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun selectedDayDialogSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val firstDay = YearMonth.now(place.zone).atDay(1)
        val rowLabel = firstDay.format(DateTimeFormatter.ofPattern("d EEE", Locale.ENGLISH))
        restoration.setContent {
            AgnihotraAppearance(prefs) { TimetablePage(place, SolarCalculator(), prefs) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(rowLabel).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(rowLabel).performClick()
        val title = firstDay.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
        compose.onNodeWithText(title).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
