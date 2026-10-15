package com.nipunrautela.agnihotra.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.nipunrautela.agnihotra.core.SolarEventType
import com.nipunrautela.agnihotra.integrations.ReleaseConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioPlayback(val event: SolarEventType? = null, val playing: Boolean = false, val failed: Boolean = false)

/** Bundled-file playback only. No recordings are supplied until their redistribution is approved. */
class MantraAudioPlayer(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mutableState = MutableStateFlow(AudioPlayback())
    val state = mutableState.asStateFlow()
    private var player: MediaPlayer? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if (change < 0) pause()
        }.build()

    fun toggle(event: SolarEventType) {
        val resource = ReleaseConfiguration.mantraRecordings[event] ?: return
        if (mutableState.value.event == event && player != null) {
            if (mutableState.value.playing) pause() else start()
            return
        }
        release()
        try {
            val created = MediaPlayer.create(context, resource, attributes, 0) ?: error("Recording unavailable")
            player = created
            created.setOnCompletionListener { pause(); created.seekTo(0) }
            created.setOnErrorListener { _, _, _ -> release(); mutableState.value = AudioPlayback(failed = true); true }
            mutableState.value = AudioPlayback(event)
            start()
        } catch (_: Exception) { release(); mutableState.value = AudioPlayback(failed = true) }
    }

    private fun start() {
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            player?.start()
            mutableState.value = mutableState.value.copy(playing = true)
        }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        mutableState.value = mutableState.value.copy(playing = false)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun release() {
        player?.release()
        player = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        mutableState.value = AudioPlayback()
    }
}
