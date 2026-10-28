package com.nipunrautela.agnihotra

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nipunrautela.agnihotra.core.withAppLanguage
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.location.LocationService
import com.nipunrautela.agnihotra.ui.AgnihotraApp
import com.nipunrautela.agnihotra.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real destinations at phone widths, including Hindi and 200% text. Saves review screenshots. */
class AppLayoutsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destinationsKeepNavigationAndPlayerControlsSeparateAcrossAppearanceChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".verification"))
        val graph = context.appGraph
        runBlocking(Dispatchers.IO) {
            graph.preferences.setBoolean("analytics", false)
            graph.preferences.setBoolean("sunrise_reminder", false)
            graph.preferences.setBoolean("sunset_reminder", false)
            graph.preferences.setString("language", "en")
            graph.preferences.setString("theme", "light")
            graph.preferences.setPracticeDuration(30)
            graph.preferences.setBundledPracticeMusic("om-1")
            graph.preferences.setBoolean("practice_music_enabled", true)
            graph.preferences.setBoolean("show_seconds", true)
            graph.database.clearAllTables()
            val place = SavedPlace("layout-review", "New Delhi", 28.6139, 77.209)
            graph.places.save(place)
            graph.preferences.repairSelection(listOf(place))
        }
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        lateinit var model: AppViewModel
        compose.setContent {
            val factory = remember { object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(graph, LocationService(context)) as T
            } }
            model = viewModel(factory = factory)
            val density = LocalDensity.current.density
            val configuration = Configuration(LocalConfiguration.current).apply { fontScale = scale; screenWidthDp = width }
            CompositionLocalProvider(LocalDensity provides Density(density, scale), LocalConfiguration provides configuration) {
                Box(Modifier.width(width.dp).testTag("app_fixture")) { AgnihotraApp(model) }
            }
        }
        for ((language, theme, textScale) in listOf(Triple("en", "light", 1f), Triple("en", "dark", 1f),
            Triple("hi", "light", 1.5f), Triple("hi", "dark", 2f))) {
            runBlocking { graph.preferences.setString("language", language); graph.preferences.setString("theme", theme) }
            compose.runOnIdle { scale = textScale; width = if (textScale > 1f) 320 else 393 }
            compose.waitUntil(10_000) { model.state.value.preferences.language == language && model.state.value.preferences.theme == theme }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("navigation_TODAY").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("navigation_TODAY").performClick()
            screenshot(context, "$language-$theme-today")
            compose.onNodeWithTag("navigation_TIMETABLE").performClick()
            screenshot(context, "$language-$theme-timetable")
            compose.onNodeWithTag("start_agnihotra").performClick()
            val action = compose.onNodeWithTag("session_action").fetchSemanticsNode().boundsInRoot
            val timer = compose.onNodeWithTag("session_timer").fetchSemanticsNode().boundsInRoot
            val dock = compose.onNodeWithTag("navigation_dock").fetchSemanticsNode().boundsInRoot
            assertTrue("Timer must clear the flame", action.bottom <= timer.top)
            assertTrue("Player action must clear the navigation dock", action.bottom < dock.top)
            assertFalse(com.nipunrautela.agnihotra.audio.PracticePlaybackService.state.value.active)
            screenshot(context, "$language-$theme-player")
            for (destination in listOf("MANTRAS", "PLACES")) {
                compose.onNodeWithTag("navigation_$destination").performClick()
                screenshot(context, "$language-$theme-${destination.lowercase()}")
            }
            compose.onNodeWithContentDescription(context.withAppLanguage(language).getString(R.string.settings)).performClick()
            screenshot(context, "$language-$theme-settings")
            compose.onNodeWithContentDescription(context.withAppLanguage(language).getString(R.string.back)).performClick()
        }
    }

    private fun screenshot(context: Context, name: String) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val destination = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it, "app-layouts") } ?: File(context.getExternalFilesDir(null), "app-layouts")
        destination.mkdirs()
        val bitmap = compose.onNodeWithTag("app_fixture").captureToImage().asAndroidBitmap()
        File(destination, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
