package com.nipunrautela.agnihotra.core

import java.time.Instant

enum class PracticePhase { PREPARATION, OFFERINGS, QUIET, COMPLETE }

/** No fixed ritual duration: only the practitioner can confirm the offerings are complete. */
data class PracticeSession(
    val event: SolarEvent,
    val quietMinutes: Int,
    val musicUri: String? = null,
    val quietUntilElapsed: Long? = null,
    val autoMusic: Boolean = false,
) {
    init { require(quietMinutes in quietOptions) }

    fun phase(now: Instant, elapsedMillis: Long): PracticePhase = when {
        quietUntilElapsed != null -> if (elapsedMillis < quietUntilElapsed) PracticePhase.QUIET else PracticePhase.COMPLETE
        now < event.instant -> PracticePhase.PREPARATION
        else -> PracticePhase.OFFERINGS
    }

    fun completeOfferings(now: Instant, elapsedMillis: Long): PracticeSession {
        if (phase(now, elapsedMillis) != PracticePhase.OFFERINGS) return this
        return copy(quietUntilElapsed = elapsedMillis + quietMinutes * 60_000L)
    }

    companion object { val quietOptions = listOf(0, 5, 10, 15) }
}
