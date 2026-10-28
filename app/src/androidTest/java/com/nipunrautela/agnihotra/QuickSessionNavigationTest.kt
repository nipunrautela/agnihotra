package com.nipunrautela.agnihotra

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nipunrautela.agnihotra.audio.PracticePlaybackService
import com.nipunrautela.agnihotra.audio.SessionStatus
import com.nipunrautela.agnihotra.data.SavedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File

class QuickSessionNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun setup() = runBlocking(Dispatchers.IO) {
        val graph = compose.activity.appGraph
        graph.preferences.setBoolean("analytics", false)
        graph.preferences.setBoolean("sunrise_reminder", false)
        graph.preferences.setBoolean("sunset_reminder", false)
        graph.preferences.setString("language", "en")
        graph.preferences.setPracticeDuration(30)
        graph.preferences.setBundledPracticeMusic("om-1")
        graph.preferences.setBoolean("practice_music_enabled", true)
        graph.database.clearAllTables()
        val place = SavedPlace("quick-session", "Delhi", 28.6139, 77.209)
        graph.places.save(place); graph.preferences.repairSelection(listOf(place))
        Unit
    }
    @After fun stop() { PracticePlaybackService.stop(compose.activity) }

    @Test fun centerOpensReadyScreenAndSecondTapStartsWithoutRestartingOnReturn() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("start_agnihotra").fetchSemanticsNodes().isNotEmpty() }
        val order = listOf("Today", "Timetable").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.center.x } +
            compose.onNodeWithTag("start_agnihotra").fetchSemanticsNode().boundsInRoot.center.x +
            listOf("Mantras", "Places").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.center.x }
        assertEquals(order.sorted(), order)
        val navigationBounds = compose.onNodeWithTag("start_agnihotra").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("start_agnihotra").performClick()
        compose.onNodeWithTag("session_action").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        assertFalse("Opening the ready screen must not start a timer or music", PracticePlaybackService.state.value.active)
        val readyBounds = compose.onNodeWithTag("session_action").fetchSemanticsNode().boundsInRoot
        assertTrue("The flame moves upward and grows", readyBounds.center.y < navigationBounds.center.y && readyBounds.width > navigationBounds.width)
        compose.onNodeWithTag("session_action").performClick()
        compose.waitUntil(10_000) { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
        val session = PracticePlaybackService.state.value
        assertEquals(30 * 60_000L, session.timer?.durationMillis)
        compose.onNodeWithText("Pause").assertIsDisplayed()
        compose.onNodeWithTag("session_action").performClick()
        compose.waitUntil(5_000) { PracticePlaybackService.state.value.status == SessionStatus.PAUSED }
        compose.onNodeWithTag("session_action").performClick()
        compose.waitUntil(5_000) { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
        compose.onNodeWithText("Mantras").performClick()
        assertEquals(session.id, PracticePlaybackService.state.value.id)
        compose.onNodeWithTag("start_agnihotra").performClick()
        assertEquals(session.id, PracticePlaybackService.state.value.id)
        assertEquals(session.timer?.durationMillis, PracticePlaybackService.state.value.timer?.durationMillis)
        compose.onNodeWithText("Next session options").performScrollTo().performClick()
        compose.onNode(hasText("30 min") and hasAnyAncestor(hasTestTag("session_options_sheet"))).assertIsSelected()
        chooseOption("Om chanting · 2")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithTag("session_end").performScrollTo().performClick()
        compose.waitUntil(10_000) { !PracticePlaybackService.state.value.active }
    }
    @Test fun readyScreenSurvivesRecreationAndCanBeLeftWithoutStartingPlayback() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("start_agnihotra").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("start_agnihotra").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("session_action").assertIsDisplayed()
        assertFalse(PracticePlaybackService.state.value.active)
        compose.onNodeWithText("Session options").performScrollTo().performClick()
        chooseOption("15 min")
        chooseOption("Om chanting · 2")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertFalse("Changing settings must not start playback", PracticePlaybackService.state.value.active)
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithTag("start_agnihotra").assertIsDisplayed()
        assertFalse(PracticePlaybackService.state.value.active)
        compose.onNodeWithTag("start_agnihotra").performClick()
        compose.onNodeWithText("Om chanting · 2").assertIsDisplayed()
        compose.onNodeWithTag("session_action").performClick()
        compose.waitUntil(10_000) { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
        assertEquals(15 * 60_000L, PracticePlaybackService.state.value.timer?.durationMillis)
    }

    @Test fun flameTransitionKeepsDockItemsFixedAndMakesThePlayerFlameProminent() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("start_agnihotra").fetchSemanticsNodes().isNotEmpty() }
        val tabs = listOf("TODAY", "TIMETABLE", "MANTRAS", "PLACES")
        val before = tabs.associateWith { compose.onNodeWithTag("navigation_$it").fetchSemanticsNode().boundsInRoot }
        val smallFlame = compose.onNodeWithTag("flame_mark", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("start_agnihotra").performClick()
            compose.mainClock.advanceTimeBy(160)
            transitionScreenshot("160ms")
            tabs.forEach { tab ->
                val during = compose.onNodeWithTag("navigation_$tab").fetchSemanticsNode().boundsInRoot
                assertEquals("Dock item $tab must not move horizontally", before.getValue(tab).center.x, during.center.x, 1f)
                assertEquals("Dock item $tab must not move vertically", before.getValue(tab).center.y, during.center.y, 1f)
                assertEquals("Dock item $tab must not change width", before.getValue(tab).width, during.width, 1f)
            }
            compose.mainClock.advanceTimeBy(160)
            transitionScreenshot("320ms")
            compose.mainClock.advanceTimeBy(640)
            transitionScreenshot("settled")
            val largeFlame = compose.onNode(hasTestTag("flame_mark") and hasAnyAncestor(hasTestTag("session_action")),
                useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("The flame itself must grow substantially, not just its surrounding circle", largeFlame.height > smallFlame.height * 2.5f)
            assertFalse("The transition must not start playback", PracticePlaybackService.state.value.active)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun dedicatedControlsFreezeCountdownAndRequireSetupBeforeAnotherSession() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("start_agnihotra").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("start_agnihotra").performClick()
        compose.onNodeWithText("15 min").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("15 min") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        transitionScreenshot("session-ready")
        compose.onNodeWithTag("session_primary").performClick()
        compose.waitUntil(10_000) { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
        assertEquals(15 * 60_000L, PracticePlaybackService.state.value.timer?.durationMillis)
        transitionScreenshot("session-playing")
        compose.onNodeWithTag("session_primary").performClick()
        compose.waitUntil(5000) { PracticePlaybackService.state.value.status == SessionStatus.PAUSED }
        val frozen = PracticePlaybackService.state.value.timer
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Session paused").assertIsDisplayed()
        assertEquals(frozen, PracticePlaybackService.state.value.timer)
        transitionScreenshot("session-paused")
        compose.onNodeWithTag("session_primary").performClick()
        compose.waitUntil(5000) { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
        compose.onNodeWithTag("session_end").performClick()
        compose.waitUntil(5000) { PracticePlaybackService.state.value.status == SessionStatus.FINISHED }
        compose.onNodeWithTag("session_timer").assertIsDisplayed()
        compose.onNodeWithText("Session ended").assertIsDisplayed()
        transitionScreenshot("session-ended")
        compose.onNodeWithTag("session_primary").performClick()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        assertFalse(PracticePlaybackService.state.value.active)
        assertNull(PracticePlaybackService.state.value.timer)
    }

    private fun chooseOption(title: String) {
        val option = hasText(title) and isSelectable() and hasAnyAncestor(hasTestTag("session_options_sheet"))
        compose.onNode(option).performClick()
        // DataStore commits asynchronously; Compose being idle does not mean the selection has arrived.
        compose.waitUntil(5_000) { compose.onAllNodes(option and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(option).assertIsSelected()
    }

    private fun transitionScreenshot(name: String) {
        val destination = File(compose.activity.getExternalFilesDir(null), "motion-review").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(destination, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

}
