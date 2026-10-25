package com.nipunrautela.agnihotra.integrations

import com.nipunrautela.agnihotra.core.SolarEventType
import com.nipunrautela.agnihotra.R
import android.content.Context
import com.nipunrautela.agnihotra.data.AppPreferences

data class PracticeMusicTrack(val id: String, val titleResource: Int, val audioResource: Int)

/** Public release links and explicitly approved audio resources. */
object ReleaseConfiguration {
    // GitHub Pages; HTTPS and the deployed English/Hindi policy have been verified.
    val privacyPolicyUrl: String? = "https://nipunrautela.me/agnihotra/"
    val supportEmail: String? = "rautelanipun@gmail.com"

    // Add approved, redistributable recordings to res/raw and map their R.raw IDs here.
    // No playback controls appear until an actual recording is supplied.
    val mantraRecordings: Map<SolarEventType, Int> = emptyMap()

    // Owner-supplied Om recordings (2026-10-25), separate from the offering mantras.
    val practiceMusic: List<PracticeMusicTrack> = listOf(
        PracticeMusicTrack("om-1", R.string.om_chanting_1, R.raw.om_chanting_1),
        PracticeMusicTrack("om-2", R.string.om_chanting_2, R.raw.om_chanting_2),
    )
}

fun AppPreferences.musicUri(context: Context): String? = ReleaseConfiguration.practiceMusic
    .firstOrNull { "bundle:${it.id}" == practiceMusicSource }
    ?.let { "android.resource://${context.packageName}/${it.audioResource}" }
    ?: practiceMusicSource?.takeIf { it.startsWith("content:") }

fun AppPreferences.musicTitle(context: Context): String = ReleaseConfiguration.practiceMusic
    .firstOrNull { "bundle:${it.id}" == practiceMusicSource }?.let { context.getString(it.titleResource) }
    ?: practiceMusicName ?: context.getString(R.string.session_silent)
