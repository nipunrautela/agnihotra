package com.nipunrautela.agnihotra

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nipunrautela.agnihotra.core.Coordinates
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.reminders.ReminderAlarmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Schedules a real, imminent solar alarm in the isolated verification installation. */
class ReminderAlarmTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph get() = context.appGraph
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }

    private fun enableSettingsSwitch(intent: Intent, enabled: () -> Boolean) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        SystemClock.sleep(700) // Allow the system Settings activity to replace the previous window.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun findSwitch(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isCheckable && node.className?.toString()?.contains("Switch") == true) return node
            for (i in 0 until node.childCount) findSwitch(node.getChild(i))?.let { return it }
            return null
        }
        try {
            waitFor(8_000) {
                val root = automation.rootInActiveWindow
                root?.findAccessibilityNodeInfosByText("Agnihotra verification")?.isNotEmpty() == true && findSwitch(root) != null
            }
            val toggle = findSwitch(automation.rootInActiveWindow)!!
            if (!toggle.isChecked) {
                val bounds = android.graphics.Rect()
                toggle.getBoundsInScreen(bounds)
                shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            }
            waitFor(5_000) { findSwitch(automation.rootInActiveWindow)?.isChecked == true }
        } finally { shell("input keyevent KEYCODE_BACK") }
        // Some OEM Settings apps commit the permission when the screen closes.
        waitFor(10_000, enabled)
    }

    @Before fun prepare() = runBlocking(Dispatchers.IO) {
        check(context.packageName.endsWith(".verification")) { "Alarm tests require the disposable verification app" }
        // Some physical devices block shell app-op changes. Use the actual per-app Settings control.
        if (Build.VERSION.SDK_INT >= 31 && !graph.scheduler.hasExactAccess()) {
            enableSettingsSwitch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"))) { graph.scheduler.hasExactAccess() }
        }
        if (!graph.scheduler.hasNotificationAccess()) enableSettingsSwitch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) { graph.scheduler.hasNotificationAccess() }
        graph.preferences.setBoolean("analytics", false)
        graph.preferences.setBoolean("sunrise_reminder", false)
        graph.preferences.setBoolean("sunset_reminder", false)
        graph.database.clearAllTables()
        graph.preferences.repairSelection(emptyList())
        graph.preferences.setBoolean("sound", true)
        graph.preferences.setBoolean("vibration", false)
        graph.preferences.setPreparation(0)
        graph.preferences.setAlarmDuration(1)
        manager.cancelAll()
    }

    @After fun cleanUp() = runBlocking(Dispatchers.IO) {
        graph.preferences.setBoolean("sunrise_reminder", false)
        context.stopService(Intent(context, ReminderAlarmService::class.java))
        manager.cancelAll()
        graph.preferences.setAlarmDuration(5)
        graph.preferences.setBoolean("vibration", true)
        graph.preferences.setPreparation(10)
        Unit
    }

    private fun startImminentAlarm() {
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking(Dispatchers.IO) {
                val target = Instant.now().plusSeconds(12)
                val date = target.atZone(ZoneOffset.UTC).toLocalDate()
                val hour = target.atZone(ZoneOffset.UTC).toLocalTime().toSecondOfDay() / 3600.0
                var longitude = (6 - hour) * 15
                repeat(4) {
                    val event = graph.calculator.calculate(date, Coordinates(0.0, longitude), ZoneOffset.UTC).sunrise!!
                    longitude += Duration.between(target, event).toMillis() / 240_000.0
                }
                val place = SavedPlace("alarm-fixture", "Alarm test", 0.0, longitude, "UTC")
                val computed = graph.calculator.calculate(date, place.coordinates, place.zone).sunrise!!
                assertTrue("Fixture must produce an imminent sunrise: $computed vs $target",
                    kotlin.math.abs(Duration.between(target, computed).toMillis()) < 1_000)
                graph.places.save(place)
                graph.preferences.repairSelection(listOf(place))
                graph.preferences.selectPlace(place.id)
                graph.preferences.setBoolean("sunrise_reminder", true)
                assertTrue("Exact alarm access required: " + shell("appops get ${context.packageName} SCHEDULE_EXACT_ALARM"), graph.scheduler.hasExactAccess())
                assertTrue("Notification access required", graph.scheduler.hasNotificationAccess(graph.preferences.preferences.first()))
                graph.refresh()
            }
            // Exercise the exact-alarm exemption with the activity in the background.
            shell("input keyevent KEYCODE_HOME")
            waitFor(25_000) { ringingNotification() != null }
        }
    }

    private fun ringingNotification() = manager.activeNotifications.firstOrNull {
        it.id == ReminderAlarmService.NOTIFICATION_ID && it.notification.actions?.isNotEmpty() == true
    }?.notification

    private fun audioIsPlaying(): Boolean = shell("dumpsys audio").lineSequence().any {
        it.contains("u/pid:${context.applicationInfo.uid}/") && it.contains("state:started") && it.contains("USAGE_ALARM")
    }

    private fun waitFor(timeout: Long, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!predicate() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(250)
        assertTrue("Condition not met within $timeout ms", predicate())
    }

    @Test fun alarmLoopsBeyondOneChimeAndNotificationStopReleasesPlayback() {
        startImminentAlarm()
        waitFor(5_000) { audioIsPlaying() }
        SystemClock.sleep(6_000) // The original single chime lasts only 2.8 seconds.
        assertTrue("Alarm must still be playing after two chime lengths", audioIsPlaying())
        val notification = ringingNotification()!!
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        notification.actions.single().actionIntent.send()
        waitFor(5_000) { !audioIsPlaying() && ringingNotification() == null }
    }

    @Test fun selectedOneMinuteDurationStopsAutomaticallyAndLeavesAQuietReminder() {
        startImminentAlarm()
        waitFor(5_000) { audioIsPlaying() }
        waitFor(65_000) { ringingNotification() == null }
        assertFalse(audioIsPlaying())
        assertTrue(manager.activeNotifications.any { it.id == ReminderAlarmService.NOTIFICATION_ID })
    }

    @Test fun disablingSoundDuringAnAlarmReleasesPlayback() {
        startImminentAlarm()
        waitFor(5_000) { audioIsPlaying() }
        runBlocking { graph.preferences.setBoolean("sound", false) }
        waitFor(5_000) { !audioIsPlaying() && ringingNotification() == null }
    }

    @Test fun vibrationOnlyReminderDoesNotStartAudio() {
        runBlocking {
            graph.preferences.setBoolean("sound", false)
            graph.preferences.setBoolean("vibration", true)
        }
        startImminentAlarm()
        SystemClock.sleep(1_000)
        assertFalse("An intentionally silent alarm must not use a sound fallback", audioIsPlaying())
        ringingNotification()!!.actions.single().actionIntent.send()
        waitFor(5_000) { ringingNotification() == null }
    }
}
