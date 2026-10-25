package com.nipunrautela.agnihotra

import androidx.compose.runtime.*
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nipunrautela.agnihotra.core.SolarCalculator
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.io.File

@RunWith(AndroidJUnit4::class)
class PracticeSessionTest {
    @get:Rule val compose = createComposeRule()
    private val place = SavedPlace("practice-home", "Delhi", 28.6139, 77.209)
    private val calculator = SolarCalculator()
    private val sunrise = requireNotNull(calculator.calculate(LocalDate.of(2026, 10, 23), place.coordinates, place.zone).sunrise)
    private val now = mutableStateOf(sunrise.minusSeconds(60))
    private val elapsed = mutableStateOf(1000L)
    private val prefs = AppPreferences(language = "en", timeFormat = "24", practiceQuietMinutes = 5, practiceMusicSource = null, practiceMusicEnabled = false)

    @Test fun sessionKeepsItsEventAndMantraAcrossRestorationAndTheEventBoundary() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AgnihotraAppearance(prefs) {
                FocusPage(AppState(false, listOf(place), prefs), calculator, nowOverride = now.value, elapsedClock = { elapsed.value })
            }
        }
        compose.onNodeWithTag("practice_start").performScrollTo().performClick()
        compose.onNodeWithTag("practice_complete_offerings").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { now.value = sunrise.plusSeconds(30) }
        compose.onNodeWithTag("practice_complete_offerings").assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("practice_complete_offerings").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Quiet sitting", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Quiet sitting", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { elapsed.value = 301_000 }
        compose.onNodeWithText("Ready for meditation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Play music").assertDoesNotExist()
        compose.onNodeWithText("Finish session").performScrollTo().performClick()
        compose.onNodeWithTag("practice_start").performScrollTo().assertIsEnabled()
    }

    @Test fun areaMetadataDoesNotResetSessionButCoordinatesDo() {
        val current = mutableStateOf(place)
        compose.setContent {
            AgnihotraAppearance(prefs) {
                FocusPage(AppState(false, listOf(current.value), prefs), calculator, nowOverride = now.value)
            }
        }
        compose.onNodeWithTag("practice_start").performScrollTo().performClick()
        compose.runOnIdle { current.value = place.copy(areaName = "New Delhi") }
        compose.onNodeWithTag("practice_complete_offerings").performScrollTo().assertExists()
        compose.runOnIdle { current.value = place.copy(latitude = 19.076, longitude = 72.8777) }
        compose.onNodeWithTag("practice_start").performScrollTo().assertIsEnabled()
    }

    @Test fun sessionControlsFitNarrowScreensInBothLanguagesThemesAndLargeText() {
        val language = mutableStateOf("en")
        val theme = mutableStateOf("light")
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                val options = prefs.copy(language = language.value, theme = theme.value)
                AgnihotraAppearance(options) {
                    Surface(Modifier.width(320.dp)) {
                        FocusPage(AppState(false, listOf(place), options), calculator, nowOverride = now.value)
                    }
                }
            }
        }
        for ((lang, mode) in listOf("en" to "light", "en" to "dark", "hi" to "light", "hi" to "dark")) {
            compose.runOnIdle { language.value = lang; theme.value = mode }
            compose.onNodeWithTag("practice_start").performScrollTo().assertIsDisplayed().assertIsEnabled()
            saveScreenshot("session-$lang-$mode-options")
            compose.onNodeWithText(if (lang == "en") "Session options" else "सत्र के विकल्प").performScrollTo().assertIsDisplayed()
        }
    }

    private fun saveScreenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "practice-previews").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
