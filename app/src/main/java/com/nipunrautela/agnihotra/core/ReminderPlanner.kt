package com.nipunrautela.agnihotra.core

import java.time.Instant
import java.time.ZoneId

data class PlannedReminder(val event: SolarEvent, val trigger: Instant, val preparation: Boolean)

class ReminderPlanner(private val calculator: SolarCalculator = SolarCalculator()) {
    fun upcoming(
        now: Instant,
        coordinates: Coordinates,
        zone: ZoneId,
        sunriseEnabled: Boolean,
        sunsetEnabled: Boolean,
        preparationMinutes: Int,
    ): List<PlannedReminder> {
        require(preparationMinutes in 0..60)
        val date = now.atZone(zone).toLocalDate()
        return (0L..2L).flatMap { offset ->
            calculator.calculate(date.plusDays(offset), coordinates, zone).events
                .filter { if (it.type == SolarEventType.SUNRISE) sunriseEnabled else sunsetEnabled }
                .flatMap { event ->
                    buildList {
                        add(PlannedReminder(event, event.instant, false))
                        if (preparationMinutes > 0) add(
                            PlannedReminder(event, event.instant.minusSeconds(preparationMinutes * 60L), true),
                        )
                    }
                }
        }.filter { it.trigger > now }.sortedBy { it.trigger }
    }
}
