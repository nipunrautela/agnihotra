package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderPlannerTest {
    private val coordinates = Coordinates(28.6139, 77.209)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 6, 21).atStartOfDay(zone).toInstant()
    private val planner = ReminderPlanner()

    @Test fun `preparation is exactly requested minutes before each enabled event`() {
        val reminders = planner.upcoming(now, coordinates, zone, true, false, 10)
        assertTrue(reminders.all { it.event.type == SolarEventType.SUNRISE })
        reminders.filter { it.preparation }.forEach {
            assertEquals(600, Duration.between(it.trigger, it.event.instant).seconds)
        }
        assertEquals(reminders.sortedBy { it.trigger }, reminders)
        assertEquals(6, reminders.size)
    }

    @Test fun `past preparation is skipped while event itself remains scheduled`() {
        val sunrise = SolarCalculator().calculate(now.atZone(zone).toLocalDate(), coordinates, zone).sunrise!!
        val reminders = planner.upcoming(sunrise.minusSeconds(60), coordinates, zone, true, true, 10)
        assertEquals(sunrise, reminders.first().trigger)
        assertFalse(reminders.first().preparation)
    }

    @Test fun `disabled reminders and zero preparation are respected`() {
        assertTrue(planner.upcoming(now, coordinates, zone, false, false, 10).isEmpty())
        assertTrue(planner.upcoming(now, coordinates, zone, true, true, 0).none { it.preparation })
    }

    @Test fun `changing coordinates changes the scheduled instant`() {
        val delhi = planner.upcoming(now, coordinates, zone, true, false, 10).first().trigger
        val guwahati = planner.upcoming(now, Coordinates(26.1445, 91.7362), zone, true, false, 10).first().trigger
        assertNotEquals(delhi, guwahati)
    }

    @Test fun `preparation interval bounds are enforced`() {
        assertThrows(IllegalArgumentException::class.java) { planner.upcoming(now, coordinates, zone, true, true, -1) }
        assertThrows(IllegalArgumentException::class.java) { planner.upcoming(now, coordinates, zone, true, true, 61) }
    }
}
