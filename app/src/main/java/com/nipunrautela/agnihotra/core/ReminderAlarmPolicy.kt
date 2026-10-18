package com.nipunrautela.agnihotra.core

import java.time.Duration
import java.time.Instant

object ReminderAlarmPolicy {
    /** A preparation alarm ends at the event boundary, even if the next broadcast is delayed. */
    fun durationMillis(minutes: Int, preparation: Boolean, now: Instant, event: Instant): Long {
        val selected = minutes.coerceIn(1, 30) * 60_000L
        return if (preparation) minOf(selected, Duration.between(now, event).toMillis().coerceAtLeast(0)) else selected
    }
}
