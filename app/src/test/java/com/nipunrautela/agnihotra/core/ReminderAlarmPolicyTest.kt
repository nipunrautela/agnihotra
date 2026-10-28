package com.nipunrautela.agnihotra.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReminderAlarmPolicyTest {
    private val now = Instant.parse("2026-10-18T00:00:00Z")

    @Test fun eventAlarmHasAShortDeadlineEvenWhenPlaybackNeverCompletes() {
        assertEquals(19_000L, ReminderAlarmPolicy.durationMillis(false, now, now))
    }

    @Test fun preparationCannotRingThroughTheEventEvenIfTheEventBroadcastIsLate() {
        assertEquals(9_000L, ReminderAlarmPolicy.durationMillis(true, now, now.plusSeconds(9)))
        assertEquals(0L, ReminderAlarmPolicy.durationMillis(true, now, now.minusSeconds(1)))
        assertEquals(19_000L, ReminderAlarmPolicy.durationMillis(true, now, now.plusSeconds(600)))
    }
}
