package com.nipunrautela.agnihotra

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import com.nipunrautela.agnihotra.core.Coordinates
import com.nipunrautela.agnihotra.location.LocationFix
import com.nipunrautela.agnihotra.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationSetupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun clearSavedPlaces() = runBlocking(Dispatchers.IO) {
        val graph = compose.activity.appGraph
        graph.database.clearAllTables()
        graph.preferences.repairSelection(emptyList())
        graph.preferences.setString("language", "en")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Choose your location").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun manualSetupWorksWithoutLocationPermission() {
        compose.onNodeWithText("Choose your location").performClick()
        compose.onNodeWithText("Place name").performTextInput("Delhi home")
        compose.onNodeWithText("Latitude").performScrollTo().performTextInput("28.6139")
        compose.onNodeWithText("Longitude").performScrollTo().performTextInput("77.2090")
        compose.onNodeWithText("Save place").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Delhi home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Delhi home").assertIsDisplayed()
        compose.onNodeWithText("Today’s timings").assertExists()
    }

    @Test fun HindiCanBeSelectedBeforeLocationSetup() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("हिन्दी").performScrollTo().performClick()
        compose.onNodeWithText("भाषा").assertExists()
        compose.onNodeWithContentDescription("वापस").performClick()
        compose.onNodeWithText("अपना स्थान चुनें").assertIsDisplayed()
    }

    @Test fun restoredGpsFixDoesNotOverwriteLaterManualCoordinateEdits() {
        compose.onNodeWithText("Choose your location").performClick()
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[AppViewModel::class.java].locationFix.value =
                LocationFix(Coordinates(28.6139, 77.209), 10f, 123_000_000L)
        }
        compose.onNodeWithText("Latitude").performScrollTo().performTextReplacement("28.7000")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Latitude").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Latitude").performScrollTo().assertTextContains("28.7000")
    }

    @Test fun areaIsNotAppliedToManuallyChangedCoordinates() {
        compose.onNodeWithText("Choose your location").performClick()
        val fix = LocationFix(Coordinates(28.6139, 77.209), 10f, 124_000_000L)
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].locationFix.value = fix }
        compose.onNodeWithText("Place name").performScrollTo().assertTextContains("Current location")
        compose.onNodeWithText("Latitude").performScrollTo().performTextReplacement("28.7000")
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[AppViewModel::class.java].locationFix.value = fix.copy(areaName = "Connaught Place, New Delhi")
        }
        compose.onNodeWithText("Place name").performScrollTo().assertTextContains("Current location")
        compose.onNodeWithText("Approximate area: Connaught Place, New Delhi").assertDoesNotExist()
        compose.onNodeWithText("Latitude").performScrollTo().assertTextContains("28.7000")
    }

    @Test fun gpsAreaIsASubtitleAndPersistsWithTheSavedPlace() {
        compose.onNodeWithText("Choose your location").performClick()
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[AppViewModel::class.java].locationFix.value =
                LocationFix(Coordinates(28.6139, 77.209), 10f, 126_000_000L, "Connaught Place, New Delhi")
        }
        compose.onNodeWithText("Place name").performScrollTo().assertTextContains("Current location")
        compose.onNodeWithText("Approximate area: Connaught Place, New Delhi").performScrollTo().assertExists()
        compose.onNodeWithText("Save place").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Today’s timings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Current location").assertExists()
        compose.onNodeWithText("Connaught Place, New Delhi").assertExists()
        runBlocking(Dispatchers.IO) {
            val saved = compose.activity.appGraph.places.all().single()
            org.junit.Assert.assertEquals("Connaught Place, New Delhi", saved.areaName)
            org.junit.Assert.assertTrue(saved.fromCurrentLocation)
        }
    }

    @Test fun delayedAreaNamePreservesTypedNameAcrossRecreation() {
        compose.onNodeWithText("Choose your location").performClick()
        val fix = LocationFix(Coordinates(28.6139, 77.209), 10f, 125_000_000L)
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].locationFix.value = fix }
        compose.onNodeWithText("Place name").performScrollTo().performTextReplacement("My home")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Place name").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[AppViewModel::class.java].locationFix.value = fix.copy(areaName = "Connaught Place, New Delhi")
        }
        compose.onNodeWithText("Place name").performScrollTo().assertTextContains("My home")
    }
}
