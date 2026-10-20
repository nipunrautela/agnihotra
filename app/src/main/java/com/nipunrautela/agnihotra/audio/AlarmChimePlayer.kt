package com.nipunrautela.agnihotra.audio

import android.content.ContentResolver
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.nipunrautela.agnihotra.R

/** Main-thread playback owned by the bounded foreground alarm service. Never changes system volume. */
class AlarmChimePlayer(private val context: Context, private val onFocusDenied: () -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var focused = false
    private var active = false
    private var player: MediaPlayer? = null
    private var prepared = false
    private var sound: Uri? = null
    private var bundled = false
    private var attempts = 0
    private val retryFocus = Runnable { if (active) requestFocus() }

    fun start(sound: Uri) {
        stop()
        this.sound = sound
        active = true
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ReminderChime.attributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (active) when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        focused = true
                        playWhenReady()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        focused = false
                        if (prepared) runCatching { player?.pause() }
                    }
                    // Keep the service's vibration/Stop/timeout alive if another app takes focus.
                    AudioManager.AUDIOFOCUS_LOSS -> stop()
                }
            }, handler).build()
        requestFocus()
    }

    private fun requestFocus() {
        val request = focus ?: return
        val result = runCatching { audio.requestAudioFocus(request) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                focused = true
                playWhenReady()
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> Unit // Resume on gain, only within this alarm's lifetime.
            else -> {
                // FGS promotion and audio-app-op propagation are asynchronous on some devices.
                // Retry briefly, then stay quiet; never bypass a denied focus request.
                if (attempts < 3) handler.postDelayed(retryFocus, 250L shl attempts++)
                else {
                    Log.w("Agnihotra", "Alarm audio focus denied; using notification playback")
                    stop()
                    onFocusDenied()
                }
            }
        }
    }

    private fun playWhenReady() {
        if (!active || !focused) return
        if (player == null) {
            val source = sound ?: return
            // Persisted channels may contain resource IDs/names from a previous release.
            // Open our own packaged chime by its current resource ID instead.
            open(source.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE && source.authority == context.packageName)
        } else if (prepared) {
            try { player?.start() }
            catch (_: RuntimeException) { playbackFailed() }
        }
    }

    private fun open(useBundled: Boolean) {
        player?.release()
        prepared = false
        bundled = useBundled
        val created = MediaPlayer()
        player = created
        try {
            created.setAudioAttributes(ReminderChime.attributes)
            if (useBundled) {
                context.resources.openRawResourceFd(R.raw.agnihotra_chime).use { asset ->
                    created.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
            } else created.setDataSource(context, sound!!)
            created.isLooping = true
            created.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            created.setOnErrorListener { failed, what, extra ->
                if (player === failed) {
                    Log.w("Agnihotra", "Alarm decoder failed ($what/$extra)")
                    playbackFailed()
                }
                true
            }
            created.setOnPreparedListener { ready ->
                if (player === ready && active) {
                    prepared = true
                    playWhenReady()
                }
            }
            created.prepareAsync()
        } catch (error: Exception) {
            Log.w("Agnihotra", "Alarm sound unavailable: ${error.javaClass.simpleName}")
            playbackFailed()
        }
    }

    private fun playbackFailed() {
        // A removed custom tone must not silence an otherwise enabled alarm. A null channel
        // sound never reaches this class: the user's deliberate silence remains respected.
        if (active && !bundled) open(useBundled = true)
        else stop()
    }

    fun stop() {
        active = false
        focused = false
        handler.removeCallbacks(retryFocus)
        player?.release()
        player = null
        prepared = false
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        sound = null
        attempts = 0
    }
}
