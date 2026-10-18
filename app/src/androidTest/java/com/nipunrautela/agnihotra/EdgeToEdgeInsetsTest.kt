package com.nipunrautela.agnihotra

import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nipunrautela.agnihotra.data.SavedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EdgeToEdgeInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() = runBlocking(Dispatchers.IO) {
        val graph = compose.activity.appGraph
        graph.preferences.setBoolean("analytics", false)
        graph.preferences.setBoolean("sunrise_reminder", false)
        graph.preferences.setBoolean("sunset_reminder", false)
        graph.preferences.setString("language", "en")
        graph.preferences.setString("theme", "light")
        graph.database.clearAllTables()
        val place = SavedPlace("insets-fixture", "New Delhi", 28.6139, 77.209)
        graph.places.save(place)
        graph.preferences.repairSelection(listOf(place))
        graph.preferences.selectPlace(place.id)
        Unit
    }

    private fun assertSafe(bounds: Rect) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(view)!!
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        assertTrue("Control overlaps status bar/cutout", bounds.top >= insets.top)
        assertTrue("Control overlaps navigation bar", bounds.bottom <= view.height - insets.bottom)
        assertTrue("Control overlaps side cutout", bounds.left >= insets.left && bounds.right <= view.width - insets.right)
    }

    @Test fun toolbarAndAllNavigationTabsStayInsideSafeInsets() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
        assertSafe(compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot)
        listOf("Today", "Timetable", "Mantras", "Places").forEach {
            assertSafe(compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot)
        }
        if (Build.VERSION.SDK_INT >= 30) compose.runOnIdle {
            assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                compose.activity.window.attributes.layoutInDisplayCutoutMode)
        }
    }

    @Test fun settingsBackControlStaysSafeInDarkTheme() {
        runBlocking { compose.activity.appGraph.preferences.setString("theme", "dark") }
        compose.onNodeWithContentDescription("Settings").performClick()
        assertSafe(compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Sunrise reminder").assertIsDisplayed()
    }
}
