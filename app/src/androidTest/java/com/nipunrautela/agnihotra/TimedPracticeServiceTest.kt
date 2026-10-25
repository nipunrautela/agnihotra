package com.nipunrautela.agnihotra

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nipunrautela.agnihotra.audio.PracticePlaybackService
import com.nipunrautela.agnihotra.audio.SessionStatus
import com.nipunrautela.agnihotra.integrations.ReleaseConfiguration
import org.junit.*
import org.junit.Assert.*

class TimedPracticeServiceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun prepare() {
        check(context.packageName.endsWith(".verification"))
        context.stopService(Intent(context, PracticePlaybackService::class.java))
        compose.setContent { Text("Session verification") }
    }
    @After fun stop() { context.stopService(Intent(context, PracticePlaybackService::class.java)) }
    private fun waitFor(timeout: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(40)
        assertTrue("Session condition timed out: ${PracticePlaybackService.state.value}", condition())
    }
    private fun start(duration: Long = 20_000) {
        ContextCompat.startForegroundService(context, Intent(context, PracticePlaybackService::class.java)
            .setAction("practice.START").putExtra("duration", duration)
            .putExtra("source", "android.resource://${context.packageName}/${R.raw.agnihotra_chime}")
            .putExtra("title", "Fixture").putExtra("language", "en"))
        waitFor { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
    }

    @Test fun suppliedMp3RecordingsHaveDecodableAudio() {
        assertEquals(2, ReleaseConfiguration.practiceMusic.size)
        for (track in ReleaseConfiguration.practiceMusic) {
            val metadata = MediaMetadataRetriever()
            try {
                context.resources.openRawResourceFd(track.audioResource).use { descriptor ->
                    metadata.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                }
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                assertTrue(requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong() > 1000)
            } finally { metadata.release() }
        }
    }

    @Test fun loopingPlaybackContinuesPastOneTrackAndStopsAtTheSessionDeadline() {
        start(6500)
        val initial = PracticePlaybackService.state.value
        SystemClock.sleep(4000) // The chime fixture is shorter than this; non-looping playback would have ended.
        assertEquals(SessionStatus.PLAYING, PracticePlaybackService.state.value.status)
        assertEquals(initial.id, PracticePlaybackService.state.value.id)
        waitFor { PracticePlaybackService.state.value.status == SessionStatus.FINISHED }
    }

    @Test fun audioPauseResumeAndRepeatedStartKeepTheOriginalDeadline() {
        start()
        val original = PracticePlaybackService.state.value
        PracticePlaybackService.pause(context)
        waitFor { PracticePlaybackService.state.value.status == SessionStatus.PAUSED }
        PracticePlaybackService.resume(context)
        waitFor { PracticePlaybackService.state.value.status == SessionStatus.PLAYING }
        start(60_000)
        assertEquals(original.id, PracticePlaybackService.state.value.id)
        assertEquals(original.timer, PracticePlaybackService.state.value.timer)
        PracticePlaybackService.stop(context)
        waitFor { !PracticePlaybackService.state.value.active }
    }

    @Test fun anOldNotificationCannotStopANewerSession() {
        repeat(3) {
            start()
            val oldId = PracticePlaybackService.state.value.id
            PracticePlaybackService.stop(context)
            waitFor { !PracticePlaybackService.state.value.active }
            start()
            val current = PracticePlaybackService.state.value.id
            assertNotEquals(oldId, current)
            context.startService(Intent(context, PracticePlaybackService::class.java).setAction("practice.STOP").putExtra("session_id", oldId))
            SystemClock.sleep(150)
            assertTrue(PracticePlaybackService.state.value.active)
            assertEquals(current, PracticePlaybackService.state.value.id)
            PracticePlaybackService.stop(context)
            waitFor { !PracticePlaybackService.state.value.active }
        }
    }

    @Test fun backgroundingTheActivityDoesNotPauseOrRestartMusic() {
        start()
        val original = PracticePlaybackService.state.value
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        SystemClock.sleep(3000)
        assertEquals(SessionStatus.PLAYING, PracticePlaybackService.state.value.status)
        assertEquals(original.timer, PracticePlaybackService.state.value.timer)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }
}
