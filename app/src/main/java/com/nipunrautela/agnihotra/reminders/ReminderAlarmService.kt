package com.nipunrautela.agnihotra.reminders

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.appGraph
import com.nipunrautela.agnihotra.audio.ReminderChime
import com.nipunrautela.agnihotra.audio.AlarmChimePlayer
import com.nipunrautela.agnihotra.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Bounded alarm playback, started only by a validated user-enabled exact alarm. */
class ReminderAlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var session: Job? = null
    private var token: String? = null
    private var chime: AlarmChimePlayer? = null
    private var systemSound = false
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var finishedNotification: Notification? = null
    private val expire = Runnable { finishAlarm(keepNotification = true) }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // A Stop action from an older, replaced alert must not stop a newer one.
            if (intent.getStringExtra("alarm_token") == token) finishAlarm()
            else if (token == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        val placeId = intent?.getStringExtra("place_id")
        val type = intent?.getStringExtra("event_type")?.let { runCatching { SolarEventType.valueOf(it) }.getOrNull() }
        if (intent?.action != ACTION_RING || placeId == null || type == null) {
            if (token == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        val eventTime = Instant.ofEpochMilli(intent.getLongExtra("event_time", 0))
        val preparation = intent.getBooleanExtra("preparation", false)
        @Suppress("DEPRECATION")
        val initialNotification = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("initial_notification", Notification::class.java)
            else intent.getParcelableExtra<Notification>("initial_notification")
        if (initialNotification == null) {
            if (token == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        val nextToken = "$placeId:${type.name}:${eventTime.toEpochMilli()}:$preparation"
        if (token == nextToken) return START_NOT_STICKY // Duplicate delivery cannot extend ringing.
        releasePlayback()
        finishedNotification = null
        token = nextToken
        // Enter the foreground before reading disk or requesting audio focus (Android 15+).
        val initial = NotificationCompat.Builder(this, initialNotification)
            .setSilent(true).setOngoing(true).setAutoCancel(false)
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, initial.build(),
                // Android 15 audio-focus hardening on some devices requires an audio-related
                // FGS type; systemExempted alone can be running yet still be denied focus.
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else if (Build.VERSION.SDK_INT >= 29)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } catch (error: RuntimeException) {
            android.util.Log.w("Agnihotra", "Alarm service unavailable: ${error.javaClass.simpleName}")
            finishAlarm()
            try { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, initialNotification) }
            catch (_: SecurityException) { /* Notification access was revoked as well. */ }
            return START_NOT_STICKY
        }
        session = scope.launch {
            try {
                val prefs = appGraph.preferences.preferences.first()
                val place = withContext(Dispatchers.IO) { appGraph.places.all().firstOrNull { it.id == placeId } }
                if (place == null || prefs.activePlaceId != placeId || !appGraph.scheduler.hasExactAccess() ||
                    !appGraph.scheduler.hasNotificationAccess(prefs) ||
                    !ReminderDeliveryPolicy(appGraph.calculator).shouldDeliver(Instant.now(), SolarEvent(type, eventTime),
                        preparation, place.coordinates, place.zone, prefs.sunriseReminder, prefs.sunsetReminder, prefs.preparationMinutes)) {
                    finishAlarm(); return@launch
                }
                val textContext = withAppLanguage(prefs.language)
                val stop = PendingIntent.getService(this@ReminderAlarmService, 43,
                    Intent(this@ReminderAlarmService, ReminderAlarmService::class.java).setAction(ACTION_STOP)
                        .setData(android.net.Uri.parse("agnihotra://alarm/$nextToken"))
                        .putExtra("alarm_token", nextToken), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                finishedNotification = appGraph.scheduler.notification(place, prefs, type, eventTime, preparation)
                    .setSilent(true).setOnlyAlertOnce(true).build()
                val ringing = appGraph.scheduler.notification(place, prefs, type, eventTime, preparation)
                    .setSilent(true).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(false)
                    .setDeleteIntent(stop)
                    .addAction(0, textContext.getString(R.string.stop_alarm), stop).build()
                try { NotificationManagerCompat.from(this@ReminderAlarmService).notify(NOTIFICATION_ID, ringing) }
                catch (_: SecurityException) { finishAlarm(); return@launch }
                val duration = ReminderAlarmPolicy.durationMillis(preparation, Instant.now(), eventTime)
                if (duration <= 0) { finishAlarm(keepNotification = true); return@launch }
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "Agnihotra:reminder").apply { acquire(duration + 5_000) }
                handler.postDelayed(expire, duration)
                val channel = appGraph.scheduler.ensureChannel(prefs)
                if (prefs.sound && channel.sound != null) {
                    chime = AlarmChimePlayer(this@ReminderAlarmService, onFocusDenied = {
                        if (token == nextToken) {
                            // Some Android 15 firmware rejects audio focus even for an audio FGS
                            // started by an exact alarm. Let Android play one notification tone;
                            // FLAG_INSISTENT cannot count completions and would exceed five chimes.
                            val fallback = appGraph.scheduler.notification(place, prefs, type, eventTime, preparation)
                                .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(false)
                                .setDeleteIntent(stop).addAction(0, textContext.getString(R.string.stop_alarm), stop)
                                .build()
                            try {
                                systemSound = true
                                vibrator?.cancel()
                                NotificationManagerCompat.from(this@ReminderAlarmService).notify(NOTIFICATION_ID, fallback)
                            } catch (_: SecurityException) { finishAlarm() }
                        }
                    }, onCompleted = {
                        if (token == nextToken) finishAlarm(keepNotification = true)
                    }).also { it.start(channel.sound) }
                }
                if (prefs.vibration && channel.shouldVibrate()) {
                    try {
                        @Suppress("DEPRECATION")
                        vibrator = (if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
                            else getSystemService(Vibrator::class.java)).also {
                            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 400, 450, 2_000), 0), ReminderChime.attributes)
                        }
                    } catch (_: RuntimeException) {
                        // Unavailable vibration must not tear down working alarm audio.
                        android.util.Log.w("Agnihotra", "Alarm vibration unavailable; audio remains available")
                    }
                }
                // Changing the active place, disabling reminders or changing ringing options stops this session.
                appGraph.preferences.preferences.collect { current ->
                    if (current.activePlaceId != placeId || current.sound != prefs.sound || current.vibration != prefs.vibration ||
                        !(if (type == SolarEventType.SUNRISE) current.sunriseReminder else current.sunsetReminder) ||
                        (preparation && current.preparationMinutes != prefs.preparationMinutes)) finishAlarm()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("Agnihotra", "Alarm playback unavailable: ${error.javaClass.simpleName}")
                finishAlarm(keepNotification = true)
            }
        }
        return START_NOT_STICKY // Never replay an old alarm after process death.
    }

    private fun releasePlayback() {
        handler.removeCallbacks(expire)
        // Removing the foreground notification cancels any system-owned fallback tone.
        // A quiet completed reminder can then be posted without leaving alarm audio behind.
        if (systemSound) { stopForeground(STOP_FOREGROUND_REMOVE); systemSound = false }
        session?.cancel(); session = null
        chime?.stop(); chime = null
        vibrator?.cancel(); vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    private fun finishAlarm(keepNotification: Boolean = false) {
        releasePlayback()
        token = null
        val completed = finishedNotification
        finishedNotification = null
        // Remove the service-owned copy before posting the completed reminder. Detaching
        // and updating the same ID can retain the old Stop action on newer Android builds.
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (keepNotification && completed != null) {
            try { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, completed) }
            catch (_: SecurityException) { /* Access was revoked while ringing. */ }
        }
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayback()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.nipunrautela.agnihotra.RING"
        const val ACTION_STOP = "com.nipunrautela.agnihotra.STOP_ALARM"
        const val NOTIFICATION_ID = 43
    }
}
