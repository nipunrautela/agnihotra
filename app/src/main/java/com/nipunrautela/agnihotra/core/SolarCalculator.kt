package com.nipunrautela.agnihotra.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }

    fun distanceKmTo(other: Coordinates): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(latitude)) *
            cos(Math.toRadians(other.latitude)) * sin(dLon / 2).pow(2)
        return 6371.0088 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

enum class SolarEventType { SUNRISE, SUNSET }
data class SolarEvent(val type: SolarEventType, val instant: Instant)
data class SolarDay(val date: LocalDate, val sunrise: Instant?, val sunset: Instant?) {
    val events: List<SolarEvent>
        get() = listOfNotNull(
            sunrise?.let { SolarEvent(SolarEventType.SUNRISE, it) },
            sunset?.let { SolarEvent(SolarEventType.SUNSET, it) },
        ).sortedBy { it.instant }
}

/**
 * Meeus solar coordinates, using NOAA's published equations.
 * https://gml.noaa.gov/grad/solcalc/calcdetails.html
 *
 * Standard flat, unobstructed horizon: solar centre at -0.833 degrees, including
 * average refraction and solar semidiameter. No terrain/GPS-altitude correction.
 * Bracket and bisect crossings over the actual local calendar day, which also
 * handles date-line locations, DST and polar days without fabricated events.
 */
class SolarCalculator {
    fun calculate(date: LocalDate, coordinates: Coordinates, zone: ZoneId): SolarDay {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        var previousTime = start
        var previousAltitude = altitude(start, coordinates) + 0.833
        var sunrise: Instant? = null
        var sunset: Instant? = null
        while (previousTime < end) {
            val nextTime = minOf(previousTime.plusSeconds(300), end)
            val nextAltitude = altitude(nextTime, coordinates) + 0.833
            val rising = previousAltitude <= 0 && nextAltitude > 0
            val setting = previousAltitude >= 0 && nextAltitude < 0
            if (rising || setting) {
                var low = previousTime.toEpochMilli()
                var high = nextTime.toEpochMilli()
                while (high - low > 100) {
                    val mid = low + (high - low) / 2
                    val above = altitude(Instant.ofEpochMilli(mid), coordinates) + 0.833 > 0
                    if (above == rising) high = mid else low = mid
                }
                val crossing = Instant.ofEpochMilli(low + (high - low) / 2)
                if (crossing < end) {
                    if (rising && sunrise == null) sunrise = crossing
                    if (setting && sunset == null) sunset = crossing
                }
            }
            previousTime = nextTime
            previousAltitude = nextAltitude
        }
        return SolarDay(date, sunrise, sunset)
    }

    fun nextEvent(now: Instant, coordinates: Coordinates, zone: ZoneId): SolarEvent? {
        val date = now.atZone(zone).toLocalDate()
        return (0L..2L).asSequence()
            .flatMap { calculate(date.plusDays(it), coordinates, zone).events.asSequence() }
            .firstOrNull { it.instant > now }
    }

    private fun altitude(instant: Instant, coordinates: Coordinates): Double {
        val julianDay = instant.toEpochMilli() / 86_400_000.0 + 2440587.5
        val t = (julianDay - 2451545.0) / 36525.0
        val meanLongitude = normalize(280.46646 + t * (36000.76983 + t * 0.0003032))
        val anomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val centre = sinDeg(anomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sinDeg(2 * anomaly) * (0.019993 - 0.000101 * t) + sinDeg(3 * anomaly) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val apparentLongitude = meanLongitude + centre - 0.00569 - 0.00478 * sinDeg(omega)
        val obliquity = 23 + (26 + (21.448 - t * (46.815 + t *
            (0.00059 - t * 0.001813))) / 60) / 60 + 0.00256 * cosDeg(omega)
        val declination = asin(sinDeg(obliquity) * sinDeg(apparentLongitude))
        val y = tan(Math.toRadians(obliquity / 2)).pow(2)
        val equationOfTime = 4 * Math.toDegrees(
            y * sinDeg(2 * meanLongitude) - 2 * eccentricity * sinDeg(anomaly) +
                4 * eccentricity * y * sinDeg(anomaly) * cosDeg(2 * meanLongitude) -
                0.5 * y * y * sinDeg(4 * meanLongitude) -
                1.25 * eccentricity * eccentricity * sinDeg(2 * anomaly),
        )
        val utcMinutes = Math.floorMod(instant.epochSecond, 86400L) / 60.0
        val solarMinutes = ((utcMinutes + equationOfTime + 4 * coordinates.longitude) % 1440 + 1440) % 1440
        val hourAngle = Math.toRadians(solarMinutes / 4 - 180)
        val latitude = Math.toRadians(coordinates.latitude)
        return Math.toDegrees(asin((sin(latitude) * sin(declination) +
            cos(latitude) * cos(declination) * cos(hourAngle)).coerceIn(-1.0, 1.0)))
    }

    private fun sinDeg(value: Double) = sin(Math.toRadians(value))
    private fun cosDeg(value: Double) = cos(Math.toRadians(value))
    private fun normalize(value: Double) = ((value % 360) + 360) % 360
}
