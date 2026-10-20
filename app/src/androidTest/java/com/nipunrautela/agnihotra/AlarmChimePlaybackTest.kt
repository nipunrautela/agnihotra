package com.nipunrautela.agnihotra

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nipunrautela.agnihotra.audio.AlarmChimePlayer
import com.nipunrautela.agnihotra.audio.ReminderChime
import org.junit.Assert.*
import org.junit.Test

class AlarmChimePlaybackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun playing(): Boolean = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys audio"))
        .bufferedReader().use { it.readText() }.lineSequence().any {
            it.contains("u/pid:${context.applicationInfo.uid}/") && it.contains("state:started") && it.contains("USAGE_ALARM")
        }

    private fun awaitPlaying(expected: Boolean) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (playing() != expected && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
        assertEquals("Alarm playback state", expected, playing())
    }

    private fun checkFallback(uri: Uri) {
        check(context.packageName.endsWith(".verification"))
        ActivityScenario.launch(MainActivity::class.java).use {
            val player = AlarmChimePlayer(context)
            try {
                onMain { player.start(uri) }
                awaitPlaying(true)
            } finally { onMain { player.stop() } }
            awaitPlaying(false)
        }
    }

    @Test fun previousReleaseResourceUriUsesCurrentBundledChime() = checkFallback(
        Uri.parse("android.resource://${context.packageName}/2131624999"))

    @Test fun removedCustomSoundFallsBackToBundledChime() = checkFallback(
        Uri.parse("content://${context.packageName}.missing/tone"))

    @Test fun temporaryAudioFocusLossPausesAndResumesWithinTheAlarmSession() {
        check(context.packageName.endsWith(".verification"))
        ActivityScenario.launch(MainActivity::class.java).use {
            val player = AlarmChimePlayer(context)
            val audio = context.getSystemService(AudioManager::class.java)
            val competing = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ReminderChime.attributes).setOnAudioFocusChangeListener { }.build()
            try {
                onMain { player.start(ReminderChime.uri(context)) }
                awaitPlaying(true)
                onMain { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competing)) }
                awaitPlaying(false)
                onMain { audio.abandonAudioFocusRequest(competing) }
                awaitPlaying(true)
            } finally {
                onMain { player.stop(); audio.abandonAudioFocusRequest(competing) }
            }
            awaitPlaying(false)
        }
    }

    @Test fun stoppingWhileFocusIsLostCannotRestartOnLaterFocusGain() {
        check(context.packageName.endsWith(".verification"))
        ActivityScenario.launch(MainActivity::class.java).use {
            val player = AlarmChimePlayer(context)
            val audio = context.getSystemService(AudioManager::class.java)
            val competing = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ReminderChime.attributes).setOnAudioFocusChangeListener { }.build()
            try {
                onMain { player.start(ReminderChime.uri(context)) }
                awaitPlaying(true)
                onMain { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competing)) }
                awaitPlaying(false)
                onMain { player.stop(); audio.abandonAudioFocusRequest(competing) }
                SystemClock.sleep(750)
                assertFalse("A stopped alarm must never resume", playing())
            } finally { onMain { player.stop(); audio.abandonAudioFocusRequest(competing) } }
        }
    }
}
