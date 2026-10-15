package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderDeliveryPolicyTest {
    private val coordinates = Coordinates(28.6139, 77.209)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val policy = ReminderDeliveryPolicy()
    private val event = SolarCalculator().calculate(LocalDate.of(2026, 6, 21), coordinates, zone)
        .events.first { it.type == SolarEventType.SUNRISE }

    private fun delivery(now: Instant, preparation: Boolean = false, minutes: Int = 10,
        sunrise: Boolean = true, position: Coordinates = coordinates) =
        policy.shouldDeliver(now, event, preparation, position, zone, sunrise, true, minutes)

    @Test fun `delivery is accepted at trigger and within two minutes after it`() {
        assertTrue(delivery(event.instant))
        assertTrue(delivery(event.instant.plusSeconds(120)))
    }

    @Test fun `early and expired queued broadcasts cannot ring`() {
        assertFalse(delivery(event.instant.minusMillis(1)))
        assertFalse(delivery(event.instant.plusSeconds(120).plusMillis(1)))
    }

    @Test fun `editing a place with the same id invalidates its old event`() {
        assertFalse(delivery(event.instant, position = Coordinates(26.1445, 91.7362)))
    }

    @Test fun `disabled reminder is rejected even if already queued`() {
        assertFalse(delivery(event.instant, sunrise = false))
    }

    @Test fun `preparation delivery is checked against the current lead time`() {
        val trigger = event.instant.minusSeconds(600)
        assertTrue(delivery(trigger, preparation = true))
        assertFalse(delivery(trigger, preparation = true, minutes = 5))
        assertFalse(delivery(trigger, preparation = true, minutes = 0))
        assertFalse(delivery(trigger, preparation = true, minutes = 61))
        assertFalse(delivery(event.instant, preparation = true, minutes = 1))
    }

    @Test fun `event queued before midnight can prepare on the preceding local day`() {
        // Deliberately use UTC for this northern location to put sunrise just after midnight.
        val position = Coordinates(64.1466, 18.0574)
        val localZone = ZoneOffset.UTC
        val sunrise = SolarCalculator().calculate(LocalDate.of(2026, 6, 21), position, localZone)
            .events.first { it.type == SolarEventType.SUNRISE }
        val trigger = sunrise.instant.minusSeconds(3600)
        assertTrue(trigger.atZone(localZone).toLocalDate() < sunrise.instant.atZone(localZone).toLocalDate())
        assertTrue(policy.shouldDeliver(trigger, sunrise, true, position, localZone, true, false, 60))
    }
}
