package com.nipunrautela.agnihotra.core

import java.time.Duration
import java.time.Instant

object ReminderAlarmPolicy {
    const val CHIME_COUNT = 5
    const val CHIME_DURATION_MILLIS = 2_800L
    // A hard deadline also bounds custom tones, delayed focus and decoder failures.
    const val MAX_DURATION_MILLIS = CHIME_COUNT * CHIME_DURATION_MILLIS + 5_000L

    /** A preparation alarm ends at the event boundary, even if the next broadcast is delayed. */
    fun durationMillis(preparation: Boolean, now: Instant, event: Instant): Long =
        if (preparation) minOf(MAX_DURATION_MILLIS, Duration.between(now, event).toMillis().coerceAtLeast(0))
        else MAX_DURATION_MILLIS
}
