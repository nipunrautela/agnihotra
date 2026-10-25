package com.nipunrautela.agnihotra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import com.nipunrautela.agnihotra.audio.PracticeMusicPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Rule

class PracticeMusicPlayerTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun foregroundActivity() { compose.setContent { Text("Audio verification") } }

    @Test fun selectedAudioCanPlayPauseResumeAndRelease() = runBlocking {
        val player = PracticeMusicPlayer(context)
        // Exercise decoding with the existing chime as a test fixture, never as bundled music.
        val uri = "android.resource://${context.packageName}/${R.raw.agnihotra_chime}"
        try {
            instrumentation.runOnMainSync { player.play(uri) }
            val playing = withTimeout(10_000) { player.state.first { it.playing || it.failed } }
            assertFalse("Fixture playback failed", playing.failed)
            instrumentation.runOnMainSync { player.pause() }
            assertFalse(player.state.value.playing)
            instrumentation.runOnMainSync { player.play(uri) }
            assertTrue(player.state.value.playing)
            instrumentation.runOnMainSync { player.release() }
            assertFalse(player.state.value.playing)
            assertFalse(player.state.value.preparing)
        } finally { instrumentation.runOnMainSync { player.release() } }
    }

    @Test fun pausingDuringPreparationPreventsLateAutomaticStart() = runBlocking {
        val player = PracticeMusicPlayer(context)
        try {
            instrumentation.runOnMainSync {
                player.play("android.resource://${context.packageName}/${R.raw.agnihotra_chime}")
                player.pause()
            }
            val prepared = withTimeout(10_000) { player.state.first { !it.preparing } }
            assertFalse(prepared.failed)
            assertFalse(prepared.playing)
        } finally { instrumentation.runOnMainSync { player.release() } }
    }

    @Test fun unavailableFileReportsFailureWithoutCrashingOrPlaying() {
        val player = PracticeMusicPlayer(context)
        try {
            instrumentation.runOnMainSync { player.play("content://com.nipunrautela.missing/removed") }
            assertTrue(player.state.value.failed)
            assertFalse(player.state.value.playing)
            assertFalse(player.state.value.preparing)
        } finally { instrumentation.runOnMainSync { player.release() } }
    }
}
