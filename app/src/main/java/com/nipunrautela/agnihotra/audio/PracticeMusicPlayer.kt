package com.nipunrautela.agnihotra.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MusicPlayback(val preparing: Boolean = false, val playing: Boolean = false, val failed: Boolean = false)

/** Session audio; never resumes automatically after an audio-focus interruption. */
class PracticeMusicPlayer(private val context: Context, private val looping: Boolean = false) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val mutableState = MutableStateFlow(MusicPlayback())
    val state = mutableState.asStateFlow()
    private var player: MediaPlayer? = null
    private var source: String? = null
    private var ready = false
    private var playWhenReady = false
    private var listening = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pause() }
    }

    fun play(uri: String) {
        if (source == uri && player != null && ready) { start(); return }
        release()
        playWhenReady = true
        source = uri
        mutableState.value = MusicPlayback(preparing = true)
        try {
            val created = MediaPlayer()
            player = created
            created.setAudioAttributes(attributes)
            created.isLooping = looping
            if (looping) created.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            created.setOnPreparedListener {
                if (player === it) {
                    ready = true
                    mutableState.value = MusicPlayback()
                    if (playWhenReady) start()
                }
            }
            created.setOnCompletionListener { pause(); it.seekTo(0) }
            created.setOnErrorListener { _, _, _ -> fail(); true }
            created.setDataSource(context, Uri.parse(uri))
            created.prepareAsync()
        } catch (_: Exception) { fail() }
    }

    private fun start() {
        try {
            if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { fail(); return }
            if (!listening) {
                ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED)
                listening = true
            }
            player?.start()
            playWhenReady = true
            mutableState.value = MusicPlayback(playing = true)
        } catch (_: Exception) { fail() }
    }

    fun pause() {
        playWhenReady = false
        if (ready) runCatching { player?.takeIf { it.isPlaying }?.pause() }
        manager.abandonAudioFocusRequest(focus)
        if (listening) { context.unregisterReceiver(noisy); listening = false }
        mutableState.value = mutableState.value.copy(playing = false)
    }

    fun release() {
        pause()
        player?.release()
        player = null
        source = null
        ready = false
        mutableState.value = MusicPlayback()
    }

    private fun fail() { release(); mutableState.value = MusicPlayback(failed = true) }
}
