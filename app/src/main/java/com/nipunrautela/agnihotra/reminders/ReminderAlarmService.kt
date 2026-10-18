package com.nipunrautela.agnihotra.reminders

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.appGraph
import com.nipunrautela.agnihotra.audio.ReminderChime
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
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
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
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0)
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
                val duration = ReminderAlarmPolicy.durationMillis(prefs.alarmDurationMinutes, preparation, Instant.now(), eventTime)
                if (duration <= 0) { finishAlarm(keepNotification = true); return@launch }
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "Agnihotra:reminder").apply { acquire(duration + 5_000) }
                handler.postDelayed(expire, duration)
                val channel = appGraph.scheduler.ensureChannel(prefs)
                if (prefs.sound && channel.sound != null) {
                    val manager = getSystemService(AudioManager::class.java)
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(ReminderChime.attributes)
                        .setOnAudioFocusChangeListener({ change ->
                            if (change < 0 && token == nextToken) finishAlarm(keepNotification = true)
                        }, handler).build()
                    focus = request
                    if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        val created = MediaPlayer()
                        player = created
                        created.apply {
                            setAudioAttributes(ReminderChime.attributes)
                            setDataSource(this@ReminderAlarmService, channel.sound)
                            isLooping = true
                            setWakeMode(this@ReminderAlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                            setOnErrorListener { failed, _, _ ->
                                if (player === failed && token == nextToken) finishAlarm(keepNotification = true)
                                true
                            }
                            setOnPreparedListener { ready -> if (player === ready && token == nextToken) ready.start() }
                            prepareAsync()
                        }
                    }
                }
                if (prefs.vibration && channel.shouldVibrate()) {
                    @Suppress("DEPRECATION")
                    vibrator = (if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
                        else getSystemService(Vibrator::class.java)).also {
                        it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 400, 450, 2_000), 0), ReminderChime.attributes)
                    }
                }
                // Changing the active place, disabling reminders or changing ringing options stops this session.
                appGraph.preferences.preferences.collect { current ->
                    if (current.activePlaceId != placeId || current.sound != prefs.sound || current.vibration != prefs.vibration ||
                        current.alarmDurationMinutes != prefs.alarmDurationMinutes ||
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
        session?.cancel(); session = null
        player?.release(); player = null
        vibrator?.cancel(); vibrator = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; focus = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    private fun finishAlarm(keepNotification: Boolean = false) {
        releasePlayback()
        token = null
        val completed = finishedNotification
        finishedNotification = null
        stopForeground(if (keepNotification && completed != null) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
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
