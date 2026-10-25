package com.nipunrautela.agnihotra

import androidx.compose.runtime.*
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.ui.AgnihotraAppearance
import com.nipunrautela.agnihotra.ui.SettingsPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingReminderLabelTogglesItsSingleAccessibleSwitch() {
        compose.setContent {
            var prefs by remember { mutableStateOf(AppPreferences()) }
            AgnihotraAppearance(AppPreferences(language = "en", theme = "light")) {
                SettingsPage(prefs, false, true, true,
                    onBoolean = { key, value -> if (key == "sunrise_reminder") prefs = prefs.copy(sunriseReminder = value) },
                    onString = { _, _ -> }, onPreparation = {}, onNotifications = {}, onExactAlarms = {}, onPrivacy = {})
            }
        }
        compose.onNodeWithText("Sunrise reminder").assertIsToggleable().assertIsOff().performClick().assertIsOn()
    }

    @Test fun disabledAnalyticsDoesNotShowAMissingConnectionWarning() {
        compose.setContent {
            val prefs = AppPreferences(analytics = false, language = "en", theme = "light")
            AgnihotraAppearance(prefs) {
                SettingsPage(prefs, false, true, true,
                    onBoolean = { _, _ -> }, onString = { _, _ -> }, onPreparation = {},
                    onNotifications = {}, onExactAlarms = {}, onPrivacy = {})
            }
        }
        compose.onNodeWithText("Usage analytics").performScrollTo().assertIsOff()
        compose.onNodeWithText("Analytics is not connected in this development build.").assertDoesNotExist()
    }

    @Test fun alarmDurationCanBeChangedAndCustomSelectionCanBeCancelled() {
        compose.setContent {
            var prefs by remember { mutableStateOf(AppPreferences(language = "en", theme = "light")) }
            AgnihotraAppearance(prefs) {
                SettingsPage(prefs, false, true, true, onBoolean = { _, _ -> }, onString = { _, _ -> },
                    onPreparation = {}, onNotifications = {}, onExactAlarms = {}, onPrivacy = {},
                    onAlarmDuration = { prefs = prefs.copy(alarmDurationMinutes = it) })
            }
        }
        compose.onNodeWithText("Alarm options").performScrollTo().performClick()
        compose.onNodeWithText("5 min").performScrollTo().assertIsSelected()
        compose.onNodeWithText("10 min").performClick().assertIsSelected()
        compose.onNodeWithText("Custom duration · 10 min").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("10 min").assertIsSelected()
    }

    @Test fun soundControlsRemainAccessibleInHindiWithLargeText() {
        var openedNotifications = false
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { fontScale = 2f }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                val prefs = AppPreferences(language = "hi", theme = "dark")
                AgnihotraAppearance(prefs) {
                    SettingsPage(prefs, false, true, true, onBoolean = { _, _ -> }, onString = { _, _ -> },
                        onPreparation = {}, onNotifications = { openedNotifications = true },
                        onExactAlarms = {}, onPrivacy = {})
                }
            }
        }
        compose.onNodeWithText("अलार्म के विकल्प").performScrollTo().performClick()
        compose.onNodeWithText("रिमाइंडर की सूचना सेटिंग").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(openedNotifications) }
        compose.onNodeWithText("अलार्म की आवाज़ की सेटिंग").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test fun backgroundAlarmHelpExplainsSystemSettingsAndCanBeDismissed() {
        compose.setContent {
            val prefs = AppPreferences(language = "en", theme = "light")
            AgnihotraAppearance(prefs) {
                SettingsPage(prefs, false, true, true, onBoolean = { _, _ -> }, onString = { _, _ -> },
                    onPreparation = {}, onNotifications = {}, onExactAlarms = {}, onPrivacy = {})
            }
        }
        compose.onNodeWithText("Alarm options").performScrollTo().performClick()
        compose.onNodeWithText("Background alarm settings").performScrollTo().performClick()
        compose.onNodeWithText("Open app settings").assertHasClickAction()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Open app settings").assertDoesNotExist()
    }
}
