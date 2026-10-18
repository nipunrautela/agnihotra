package com.nipunrautela.agnihotra.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReminderAlarmPolicyTest {
    private val now = Instant.parse("2026-10-18T00:00:00Z")

    @Test fun eventAlarmUsesSelectedDurationAndBoundsCorruptValues() {
        assertEquals(300_000L, ReminderAlarmPolicy.durationMillis(5, false, now, now))
        assertEquals(60_000L, ReminderAlarmPolicy.durationMillis(0, false, now, now))
        assertEquals(1_800_000L, ReminderAlarmPolicy.durationMillis(100, false, now, now))
    }

    @Test fun preparationCannotRingThroughTheEventEvenIfTheEventBroadcastIsLate() {
        assertEquals(90_000L, ReminderAlarmPolicy.durationMillis(5, true, now, now.plusSeconds(90)))
        assertEquals(0L, ReminderAlarmPolicy.durationMillis(5, true, now, now.minusSeconds(1)))
        assertEquals(300_000L, ReminderAlarmPolicy.durationMillis(5, true, now, now.plusSeconds(600)))
    }
}
