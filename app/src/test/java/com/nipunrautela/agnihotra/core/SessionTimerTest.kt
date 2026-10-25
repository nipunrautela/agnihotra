package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test

class SessionTimerTest {
    @Test fun thirtyMinuteSessionEndsAtItsMonotonicDeadline() {
        val timer = SessionTimer(12_000, 30 * 60_000L)
        assertEquals(1_800_000L, timer.remainingMillis(12_000))
        assertEquals(1L, timer.remainingMillis(timer.endsElapsed - 1))
        assertFalse(timer.finished(timer.endsElapsed - 1))
        assertTrue(timer.finished(timer.endsElapsed))
        assertEquals(0L, timer.remainingMillis(timer.endsElapsed + 60_000))
    }
    @Test fun readingTheTimerDoesNotRestartOrExtendIt() {
        val timer = SessionTimer(1000, 60_000)
        repeat(5) { assertEquals(30_000L, timer.remainingMillis(31_000)) }
        assertEquals(61_000L, timer.endsElapsed)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroLengthIsRejected() { SessionTimer(0, 0) }
}
