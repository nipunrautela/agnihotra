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

    @Test fun centerStartsImmediatelyAndReturningFromOtherTabsDoesNotRestartTheSession() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("start_agnihotra").fetchSemanticsNodes().isNotEmpty() }
        val order = listOf("Today", "Timetable").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.center.x } +
            compose.onNodeWithTag("start_agnihotra").fetchSemanticsNode().boundsInRoot.center.x +
            listOf("Mantras", "Places").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.center.x }
        assertEquals(order.sorted(), order)
        compose.onNodeWithTag("start_agnihotra").performClick()
        compose.waitUntil(10_000) { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
        val session = PracticePlaybackService.state.value
        assertEquals(30 * 60_000L, session.timer?.durationMillis)
        compose.onNodeWithText("Pause music").assertIsDisplayed()
        compose.onNodeWithText("Mantras").performClick()
        assertEquals(session.id, PracticePlaybackService.state.value.id)
        compose.onNodeWithTag("start_agnihotra").performClick()
        assertEquals(session.timer, PracticePlaybackService.state.value.timer)
        compose.onNodeWithText("Next session options").performScrollTo().performClick()
        compose.onNodeWithText("30 min").assertIsSelected()
        compose.onNodeWithText("Om chanting · 2").performClick().assertIsSelected()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("End session").performScrollTo().performClick()
        compose.waitUntil(10_000) { !PracticePlaybackService.state.value.active }
    }
}
