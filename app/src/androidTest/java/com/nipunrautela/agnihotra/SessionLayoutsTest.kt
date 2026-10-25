package com.nipunrautela.agnihotra

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.ui.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SessionLayoutsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionAndOptionsRemainUsableWithLargeTextInBothLanguagesAndThemes() {
        val prefs = mutableStateOf(AppPreferences(language = "en", theme = "light"))
        val sheet = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                AgnihotraAppearance(prefs.value) {
                    Surface(Modifier.width(320.dp)) {
                        TimedPracticePage(prefs.value, "Delhi", onOptions = { sheet.value = true }, onMantras = {})
                    }
                    if (sheet.value) SessionOptionsSheet(prefs.value, onDuration = { prefs.value = prefs.value.copy(practiceDurationMinutes = it) },
                        onQuiet = {}, onBoolean = { _, enabled -> prefs.value = prefs.value.copy(practiceMusicEnabled = enabled) },
                        onMusic = {}, onBundle = { prefs.value = prefs.value.copy(practiceMusicSource = "bundle:$it") },
                        onDismiss = { sheet.value = false })
                }
            }
        }
        for ((language, theme) in listOf("en" to "light", "en" to "dark", "hi" to "light", "hi" to "dark")) {
            compose.runOnIdle { prefs.value = prefs.value.copy(language = language, theme = theme); sheet.value = false }
            compose.onNodeWithTag("session_timer").assertIsDisplayed()
            compose.onNodeWithText(if (language == "en") "Start Agnihotra" else "अग्निहोत्र शुरू करें").performScrollTo().assertIsDisplayed()
            screenshot("timed-$language-$theme")
            compose.runOnIdle { sheet.value = true }
            compose.onNodeWithText(if (language == "en") "30 min" else "30 मिनट").assertIsSelected()
            compose.onNode(hasText(if (language == "en") "Om chanting · 1" else "ॐ जप · 1") and hasClickAction())
                .performScrollTo().assertIsSelected()
            screenshot("options-$language-$theme")
            compose.onNodeWithText(if (language == "en") "Done" else "हो गया").performScrollTo().performClick()
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val destination = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it, "session-layouts") } ?: File(context.getExternalFilesDir(null), "session-layouts")
        destination.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(destination, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
