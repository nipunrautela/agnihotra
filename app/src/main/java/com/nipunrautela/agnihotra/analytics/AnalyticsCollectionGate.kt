package com.nipunrautela.agnihotra.analytics

/** Explicit choices take priority over queued preference emissions for this process. */
internal class AnalyticsCollectionGate {
    private var requestedChoice: Boolean? = null
    private var persistedChoice = false
    val enabled: Boolean get() = requestedChoice ?: persistedChoice

    fun beginChoice() {
        // Opt-in also waits for persistence before collecting anything.
        requestedChoice = false
    }

    fun choicePersisted(value: Boolean) {
        requestedChoice = value
    }

    fun applyPersisted(value: Boolean) {
        persistedChoice = value
    }
}
