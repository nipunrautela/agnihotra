package com.nipunrautela.agnihotra.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Validate queued broadcasts against today's saved place/settings before making a sound. */
class ReminderDeliveryPolicy(private val calculator: SolarCalculator = SolarCalculator()) {
    fun shouldDeliver(now: Instant, event: SolarEvent, preparation: Boolean,
        coordinates: Coordinates, zone: ZoneId, sunriseEnabled: Boolean,
        sunsetEnabled: Boolean, preparationMinutes: Int): Boolean {
        if (preparationMinutes !in 0..60) return false
        if (!(if (event.type == SolarEventType.SUNRISE) sunriseEnabled else sunsetEnabled)) return false
        if (preparation && preparationMinutes == 0) return false
        if (preparation && now >= event.instant) return false
        val trigger = if (preparation) event.instant.minusSeconds(preparationMinutes * 60L) else event.instant
        // Never ring early, and don't replay old events after clock changes or delayed delivery.
        val lateness = Duration.between(trigger, now)
        if (lateness.isNegative || lateness > Duration.ofMinutes(2)) return false
        val current = calculator.calculate(event.instant.atZone(zone).toLocalDate(), coordinates, zone)
            .events.firstOrNull { it.type == event.type } ?: return false
        // Broadcast timestamps have millisecond precision; the calculator bisects more finely.
        return current.instant.toEpochMilli() == event.instant.toEpochMilli()
    }
}
