package com.nipunrautela.agnihotra.integrations

import com.nipunrautela.agnihotra.core.SolarEventType

/** Public release links and explicitly approved audio resources. */
object ReleaseConfiguration {
    // GitHub Pages; HTTPS and the deployed English/Hindi policy have been verified.
    val privacyPolicyUrl: String? = "https://nipunrautela.me/agnihotra/"
    val supportEmail: String? = "rautelanipun@gmail.com"

    // Add approved, redistributable recordings to res/raw and map their R.raw IDs here.
    // No playback controls appear until an actual recording is supplied.
    val mantraRecordings: Map<SolarEventType, Int> = emptyMap()
}
