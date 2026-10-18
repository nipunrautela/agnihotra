package com.nipunrautela.agnihotra.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nipunrautela.agnihotra.MainActivity
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.appGraph
import com.nipunrautela.agnihotra.audio.ReminderChime
import com.nipunrautela.agnihotra.core.ReminderPlanner
import com.nipunrautela.agnihotra.core.ReminderDeliveryPolicy
import com.nipunrautela.agnihotra.core.SolarEvent
import com.nipunrautela.agnihotra.core.SolarEventType
import com.nipunrautela.agnihotra.core.withAppLanguage
import com.nipunrautela.agnihotra.core.timeFormatter
import com.nipunrautela.agnihotra.core.use24HourTime
import android.provider.Settings
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.data.SavedPlace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReminderScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun hasExactAccess(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    fun hasNotificationAccess(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun hasNotificationAccess(prefs: AppPreferences): Boolean = hasNotificationAccess() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId(prefs))?.importance != NotificationManager.IMPORTANCE_NONE

    fun notificationSettingsIntent(prefs: AppPreferences): Intent {
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId(prefs))
        return Intent(if (channel == null) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId(prefs))
    }

    internal fun channelId(prefs: AppPreferences) = "agnihotra_${prefs.sound}_${prefs.vibration}"

    @Synchronized
    fun reschedule(place: SavedPlace?, prefs: AppPreferences) {
        PendingIntent.getBroadcast(context, 40, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let(manager::cancel)
        if (place == null || !hasExactAccess() || !hasNotificationAccess(prefs)) {
            context.stopService(Intent(context, ReminderAlarmService::class.java))
            return
        }
        val next = ReminderPlanner().upcoming(Instant.now(), place.coordinates, place.zone,
            prefs.sunriseReminder, prefs.sunsetReminder, prefs.preparationMinutes).firstOrNull() ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("place_id", place.id)
            .putExtra("event_type", next.event.type.name)
            .putExtra("event_time", next.event.instant.toEpochMilli())
            .putExtra("preparation", next.preparation)
        val pending = PendingIntent.getBroadcast(context, 40, intent, flags)
        // Alarm-clock delivery avoids idle quotas between a preparation and event alarm.
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(next.trigger.toEpochMilli(), openApp()), pending)
        } catch (_: SecurityException) {
            // Access can be revoked between checking and scheduling. UI explains unavailable access.
        }
    }

    fun notify(place: SavedPlace, prefs: AppPreferences, type: SolarEventType, eventTime: Instant, preparation: Boolean) {
        if (!hasNotificationAccess(prefs)) return
        ensureChannel(prefs)
        val intent = Intent(context, ReminderAlarmService::class.java)
            .setAction(ReminderAlarmService.ACTION_RING)
            .putExtra("place_id", place.id).putExtra("event_type", type.name)
            .putExtra("event_time", eventTime.toEpochMilli()).putExtra("preparation", preparation)
            .putExtra("initial_notification", notification(place, prefs, type, eventTime, preparation).build())
        if (prefs.sound || prefs.vibration) {
            try { ContextCompat.startForegroundService(context, intent); return }
            catch (_: IllegalStateException) { /* OS disallowed background playback; retain a normal alert. */ }
            catch (_: SecurityException) { /* Exact-alarm access may have been revoked. */ }
        }
        try { NotificationManagerCompat.from(context).notify(ReminderAlarmService.NOTIFICATION_ID,
            notification(place, prefs, type, eventTime, preparation).build()) }
        catch (_: SecurityException) { /* Notification access changed while posting. */ }
    }

    internal fun ensureChannel(prefs: AppPreferences): NotificationChannel {
        val textContext = context.withAppLanguage(prefs.language)
        val channelId = channelId(prefs)
        val channel = NotificationChannel(channelId, textContext.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = textContext.getString(R.string.reminder_channel_description)
            enableVibration(prefs.vibration)
            // Keep existing channel IDs: Android preserves any sound/blocking choices made by users.
            setSound(if (prefs.sound) ReminderChime.uri(context) else null, ReminderChime.attributes)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
    }

    internal fun notification(place: SavedPlace, prefs: AppPreferences, type: SolarEventType,
        eventTime: Instant, preparation: Boolean): NotificationCompat.Builder {
        val textContext = context.withAppLanguage(prefs.language)
        val eventName = textContext.getString(if (type == SolarEventType.SUNRISE) R.string.sunrise else R.string.sunset)
        val time = eventTime.atZone(place.zone).format(timeFormatter(context.use24HourTime(prefs.timeFormat), textContext.resources.configuration.locales[0]))
        val title = textContext.getString(if (preparation) R.string.preparation_notification else R.string.event_notification, eventName)
        return NotificationCompat.Builder(context, channelId(prefs))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(textContext.getString(R.string.notification_detail, place.name, time))
            .setContentIntent(openApp()).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    private fun openApp() = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java), flags)

    companion object { private const val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        context.appGraph.scope.launch {
            try {
                val graph = context.appGraph
                val prefs = graph.preferences.preferences.first()
                val place = graph.places.all().firstOrNull { it.id == prefs.activePlaceId }
                val type = intent.getStringExtra("event_type")?.let { runCatching { SolarEventType.valueOf(it) }.getOrNull() }
                val eventTime = Instant.ofEpochMilli(intent.getLongExtra("event_time", 0))
                val preparation = intent.getBooleanExtra("preparation", false)
                if (place != null && place.id == intent.getStringExtra("place_id") && type != null &&
                    ReminderDeliveryPolicy(graph.calculator).shouldDeliver(Instant.now(), SolarEvent(type, eventTime),
                        preparation, place.coordinates, place.zone, prefs.sunriseReminder,
                        prefs.sunsetReminder, prefs.preparationMinutes)) {
                    graph.scheduler.notify(place, prefs, type, eventTime, preparation)
                }
            } finally {
                try { context.appGraph.refresh() } finally { result.finish() }
            }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result = goAsync()
        context.appGraph.scope.launch { try { context.appGraph.refresh() } finally { result.finish() } }
    }
}
