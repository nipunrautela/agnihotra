package com.nipunrautela.agnihotra.audio

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.nipunrautela.agnihotra.R

/** Shared audio/attributes for the bundled default and its explicit Settings preview. */
object ReminderChime {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun uri(context: Context): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath("raw")
        // Name-based URIs survive resource ID changes. The R reference also keeps the asset.
        .appendPath(context.resources.getResourceEntryName(R.raw.agnihotra_chime))
        .build()
}

/** A short, user-requested preview; no notification permission or scheduled alarm is needed. */
class ChimePreviewPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(): Boolean {
        stop()
        return try {
            val created = MediaPlayer.create(context, R.raw.agnihotra_chime, ReminderChime.attributes, 0)
                ?: return false
            player = created
            created.setOnCompletionListener { completed -> if (player === completed) stop() }
            created.setOnErrorListener { failed, _, _ -> if (player === failed) stop(); true }
            created.start()
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
