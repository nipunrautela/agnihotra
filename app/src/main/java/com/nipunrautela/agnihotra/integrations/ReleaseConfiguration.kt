package com.nipunrautela.agnihotra.integrations

import com.nipunrautela.agnihotra.core.SolarEventType

/** External content/configuration is intentionally deferred; empty values have useful UI fallbacks. */
object ReleaseConfiguration {
    // Fill after the reviewed privacy policy has a public URL.
    val privacyPolicyUrl: String? = null
    val supportEmail: String? = "rautelanipun@gmail.com"

    // Add approved, redistributable recordings to res/raw and map their R.raw IDs here.
    // No playback controls appear until an actual recording is supplied.
    val mantraRecordings: Map<SolarEventType, Int> = emptyMap()
}
