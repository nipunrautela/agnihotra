package com.nipunrautela.agnihotra.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nipunrautela.agnihotra.MainActivity
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.SessionTimer
import com.nipunrautela.agnihotra.core.withAppLanguage
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.integrations.musicTitle
import com.nipunrautela.agnihotra.integrations.musicUri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class SessionStatus { IDLE, PREPARING, PLAYING, PAUSED, FINISHED, FAILED }
data class TimedPracticeState(
    val id: String? = null,
    val timer: SessionTimer? = null,
    val title: String = "",
    val status: SessionStatus = SessionStatus.IDLE,
    val hasAudio: Boolean = false,
) {
    val active: Boolean get() = status in listOf(SessionStatus.PREPARING, SessionStatus.PLAYING, SessionStatus.PAUSED)
}

/** One bounded, user-started session. Leaving the UI does not stop music; process death never replays it. */
class PracticePlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var player: PracticeMusicPlayer? = null
    private var playbackJob: Job? = null
    private var source: String? = null
    private var media: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var textContext: Context = this
    private var latestStartId = 0
    private var ownedSessionId: String? = null
    private val expire = Runnable {
        mutableState.value.timer?.let { timer ->
            if (mutableState.value.active && !timer.paused) {
                if (timer.finished(SystemClock.elapsedRealtime())) finish() else scheduleDeadline(timer)
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == START) {
            if (mutableState.value.active) return START_NOT_STICKY
            val millis = intent.getLongExtra("duration", 30 * 60_000L)
            if (millis !in 1..120 * 60_000L) { stopSelf(startId); return START_NOT_STICKY }
            source = intent.getStringExtra("source")?.takeIf { it.startsWith("content:") || it.startsWith("android.resource:") }
            textContext = withAppLanguage(intent.getStringExtra("language") ?: "system")
            val timer = SessionTimer(SystemClock.elapsedRealtime(), millis)
            mutableState.value = TimedPracticeState(UUID.randomUUID().toString(), timer,
                intent.getStringExtra("title").orEmpty().take(200), SessionStatus.PREPARING, source != null)
            ownedSessionId = mutableState.value.id
            try {
                getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL, textContext.getString(R.string.session_notification_channel), NotificationManager.IMPORTANCE_LOW)
                        .apply { setSound(null, null); enableVibration(false) })
                media = MediaSession(this, "AgnihotraSession").apply {
                    setCallback(object : MediaSession.Callback() {
                        override fun onPlay() { resumeSession() }
                        override fun onPause() { pauseSession() }
                        override fun onStop() { finish() }
                    })
                    setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, mutableState.value.title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, textContext.getString(R.string.app_name)).build())
                    isActive = true
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                    if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
                scheduleDeadline(timer)
                if (source != null) {
                    val audio = PracticeMusicPlayer(applicationContext, looping = true)
                    player = audio
                    audio.play(requireNotNull(source))
                    playbackJob = scope.launch {
                        audio.state.collect { playback ->
                            if (playback.failed) { finish(failed = true); return@collect }
                            val current = mutableState.value
                            if (!current.active) return@collect
                            when {
                                current.timer?.paused == true -> Unit
                                playback.preparing -> mutableState.value = current.copy(status = SessionStatus.PREPARING)
                                playback.playing -> mutableState.value = current.copy(status = SessionStatus.PLAYING)
                                else -> pauseSession() // Focus loss/headphone removal pauses the whole session.
                            }
                            updateNotification()
                        }
                    }
                } else {
                    mutableState.value = mutableState.value.copy(status = SessionStatus.PLAYING)
                    updateNotification()
                }
            } catch (_: Exception) { finish(failed = true) }
        } else if (intent?.getStringExtra("session_id") == mutableState.value.id && mutableState.value.active) {
            when (intent?.action) { STOP -> finish(); PAUSE -> pauseSession(); RESUME -> resumeSession() }
        } else if (!mutableState.value.active) stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun pauseSession() {
        val current = mutableState.value
        val timer = current.timer ?: return
        if (!current.active || timer.paused) return
        val now = SystemClock.elapsedRealtime()
        if (timer.finished(now)) { finish(); return }
        mutableState.value = current.copy(timer = timer.pause(now), status = SessionStatus.PAUSED)
        cancelDeadline()
        player?.pause()
        updateNotification()
    }

    private fun resumeSession() {
        val current = mutableState.value
        val timer = current.timer ?: return
        if (!current.active || !timer.paused) return
        val resumed = timer.resume(SystemClock.elapsedRealtime())
        mutableState.value = current.copy(timer = resumed,
            status = if (current.hasAudio) SessionStatus.PREPARING else SessionStatus.PLAYING)
        scheduleDeadline(resumed)
        source?.let { player?.play(it) }
        updateNotification()
    }

    private fun cancelDeadline() {
        handler.removeCallbacks(expire)
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    private fun scheduleDeadline(timer: SessionTimer) {
        cancelDeadline()
        if (timer.paused) return
        val remaining = timer.remainingMillis(SystemClock.elapsedRealtime())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "Agnihotra:practice").apply { acquire(remaining + 5_000) }
        handler.postDelayed(expire, remaining)
    }

    private fun notification(): Notification {
        val current = mutableState.value
        val open = PendingIntent.getActivity(this, 61, Intent(this, MainActivity::class.java)
            .putExtra("open_practice_session", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PENDING_FLAGS)
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(textContext.getString(R.string.session_running))
            .setContentText(current.title).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT).setVisibility(Notification.VISIBILITY_PRIVATE)
        val remaining = current.timer?.remainingMillis(SystemClock.elapsedRealtime()) ?: 0L
        val paused = current.timer?.paused == true
        builder.setWhen(System.currentTimeMillis() + remaining).setUsesChronometer(!paused).setChronometerCountDown(true)
        val seconds = (remaining + 999) / 1000
        if (paused) builder.setContentText(textContext.getString(R.string.session_paused_notification, current.title,
            textContext.getString(R.string.session_timer, seconds / 60, seconds % 60)))
        builder.addAction(Notification.Action.Builder(
            if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            textContext.getString(if (paused) R.string.session_resume else R.string.session_pause),
            action(if (paused) RESUME else PAUSE)).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
            textContext.getString(R.string.practice_end), action(STOP)).build())
        return builder.setStyle(Notification.MediaStyle().setMediaSession(media?.sessionToken)
            .setShowActionsInCompactView(0, 1)).build()
    }

    private fun action(action: String): PendingIntent = PendingIntent.getService(this, action.hashCode(),
        Intent(this, PracticePlaybackService::class.java).setAction(action)
            .setData(android.net.Uri.parse("agnihotra://practice/${mutableState.value.id}/$action"))
            .putExtra("session_id", mutableState.value.id), PENDING_FLAGS)

    private fun updateNotification() {
        val current = mutableState.value
        media?.setPlaybackState(PlaybackState.Builder().setState(
            when (current.status) {
                SessionStatus.PLAYING -> PlaybackState.STATE_PLAYING
                SessionStatus.PREPARING -> PlaybackState.STATE_BUFFERING
                else -> PlaybackState.STATE_PAUSED
            },
            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP).build())
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    }

    private fun finish(failed: Boolean = false) {
        val ended = mutableState.value.copy(status = if (failed) SessionStatus.FAILED else SessionStatus.FINISHED)
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Do not stop a newer start request delivered while the old session was finishing.
        stopSelfResult(latestStartId)
        if (mutableState.value.id == ended.id) mutableState.value = ended
    }

    private fun release() {
        cancelDeadline()
        playbackJob?.cancel(); playbackJob = null
        player?.release(); player = null
        media?.release(); media = null
    }

    override fun onDestroy() {
        if (mutableState.value.id == ownedSessionId && mutableState.value.active)
            mutableState.value = mutableState.value.copy(status = SessionStatus.FINISHED)
        release(); scope.cancel(); super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "practice_playback"
        private const val NOTIFICATION_ID = 62
        private const val START = "practice.START"
        private const val PAUSE = "practice.PAUSE"
        private const val RESUME = "practice.RESUME"
        private const val STOP = "practice.STOP"
        private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private val mutableState = MutableStateFlow(TimedPracticeState())
        val state = mutableState.asStateFlow()

        /** Return from the end summary to setup without starting a service or playing audio. */
        fun prepareNext() {
            if (!mutableState.value.active) mutableState.value = TimedPracticeState()
        }

        fun start(context: Context, prefs: AppPreferences, uri: String? = prefs.musicUri(context), title: String = prefs.musicTitle(context)): Boolean {
            if (mutableState.value.active) return true
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PracticePlaybackService::class.java).setAction(START)
                    .putExtra("duration", prefs.practiceDurationMinutes.coerceIn(1, 120) * 60_000L)
                    .putExtra("source", uri.takeIf { prefs.practiceMusicEnabled })
                    .putExtra("title", if (prefs.practiceMusicEnabled) title else context.getString(R.string.session_silent))
                    .putExtra("language", prefs.language))
                true
            }.getOrElse { mutableState.value = TimedPracticeState(status = SessionStatus.FAILED); false }
        }

        fun pause(context: Context) = control(context, PAUSE)
        fun resume(context: Context) = control(context, RESUME)
        fun stop(context: Context) = control(context, STOP)
        private fun control(context: Context, action: String) {
            val id = mutableState.value.id ?: return
            if (!mutableState.value.active) return
            runCatching { context.startService(Intent(context, PracticePlaybackService::class.java).setAction(action).putExtra("session_id", id)) }
        }
    }
}
