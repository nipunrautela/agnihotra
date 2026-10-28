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

    @Test fun pauseFreezesRemainingTimePastTheOriginalDeadline() {
        val paused = SessionTimer(1000, 10_000).pause(4000)
        assertTrue(paused.paused)
        assertEquals(7000L, paused.remainingMillis(60_000))
        assertFalse(paused.finished(60_000))
        val resumed = paused.resume(60_000)
        assertFalse(resumed.paused)
        assertEquals(7000L, resumed.remainingMillis(60_000))
        assertFalse(resumed.finished(66_999))
        assertTrue(resumed.finished(67_000))
    }

    @Test fun multiplePausesOnlyExcludeTimeActuallySpentPaused() {
        val timer = SessionTimer(0, 10_000).pause(2000).pause(3000).resume(5000)
            .resume(6000).pause(7000).resume(11_000)
        assertEquals(6000L, timer.remainingMillis(11_000))
        assertEquals(17_000L, timer.endsElapsed)
        assertTrue(timer.finished(17_000))
    }

    @Test fun pauseCannotReviveAnExpiredTimer() {
        val timer = SessionTimer(1000, 1000)
        assertEquals(timer, timer.pause(2000).resume(3000))
        assertTrue(timer.finished(3000))
    }
}
