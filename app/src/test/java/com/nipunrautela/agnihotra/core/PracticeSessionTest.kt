package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PracticeSessionTest {
    private val event = SolarEvent(SolarEventType.SUNSET, Instant.parse("2026-10-23T12:00:00Z"))

    @Test fun offeringsCannotBeCompletedBeforeTheEvent() {
        val session = PracticeSession(event, 5, "content://music/chosen", autoMusic = true)
        assertEquals(PracticePhase.PREPARATION, session.phase(event.instant.minusMillis(1), 1000))
        assertEquals(session, session.completeOfferings(event.instant.minusMillis(1), 1000))
    }

    @Test fun reachingTheEventDoesNotAutomaticallyCompleteTheRitual() {
        val session = PracticeSession(event, 0)
        assertEquals(PracticePhase.OFFERINGS, session.phase(event.instant, 1000))
        assertEquals(PracticePhase.OFFERINGS, session.phase(event.instant.plusSeconds(3600), 3_601_000))
        assertNull(session.quietUntilElapsed)
    }

    @Test fun quietTimerStartsFromConfirmationRatherThanSunset() {
        val session = PracticeSession(event, 5).completeOfferings(event.instant.plusSeconds(90), 20_000)
        assertEquals(320_000L, session.quietUntilElapsed)
        assertEquals(PracticePhase.QUIET, session.phase(event.instant.plusSeconds(390), 319_999))
        assertEquals(PracticePhase.COMPLETE, session.phase(event.instant.plusSeconds(390), 320_000))
    }

    @Test fun clockChangesDoNotChangeQuietSittingDuration() {
        val session = PracticeSession(event, 10).completeOfferings(event.instant, 1000)
        assertEquals(PracticePhase.QUIET, session.phase(event.instant.plusSeconds(86400), 2000))
        assertEquals(PracticePhase.COMPLETE, session.phase(event.instant.minusSeconds(86400), 601_000))
    }

    @Test fun noTimerAllowsMusicOnlyAfterExplicitConfirmation() {
        val session = PracticeSession(event, 0, "content://music/chosen", autoMusic = true)
        val completed = session.completeOfferings(event.instant, 1000)
        assertEquals(PracticePhase.COMPLETE, completed.phase(event.instant, 1000))
        assertEquals(session.event, completed.event)
        assertEquals(session.musicUri, completed.musicUri)
        assertTrue(completed.autoMusic)
    }

    @Test fun repeatedCompletionDoesNotRestartTheQuietTimer() {
        val session = PracticeSession(event, 15).completeOfferings(event.instant, 1000)
        assertEquals(session, session.completeOfferings(event.instant.plusSeconds(30), 31_000))
        assertEquals(session, session.completeOfferings(event.instant.plusSeconds(1000), 1_001_000))
    }
}
