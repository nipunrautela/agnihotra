package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.abs

class SolarCalculatorTest {
    private val calculator = SolarCalculator()
    private val delhi = Coordinates(28.6139, 77.2090)
    private val india = ZoneId.of("Asia/Kolkata")

    @Test fun `sunrise and sunset match independent astronomy model across India and time zones`() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/solar-reference.csv"))
        reference.bufferedReader().useLines { lines ->
            lines.filterNot { it.startsWith("#") || it.startsWith("name,") || it.isBlank() }.forEach { row ->
                val fields = row.split(',')
                val day = calculator.calculate(LocalDate.parse(fields[1]), Coordinates(fields[2].toDouble(), fields[3].toDouble()), ZoneId.of(fields[4]))
                listOf(day.sunrise to fields[5], day.sunset to fields[6]).forEach { (actual, expected) ->
                    assertNotNull(fields[0], actual)
                    val difference = abs(Duration.between(Instant.parse(expected), actual).seconds)
                    assertTrue("${fields[0]}: $actual differs by ${difference}s from $expected", difference <= 60)
                }
                day.events.forEach { assertEquals(day.date, it.instant.atZone(ZoneId.of(fields[4])).toLocalDate()) }
            }
        }
    }

    @Test fun `sunset agrees with NOAA published Bangkok annual table`() {
        // NOAA PDF: https://gml.noaa.gov/grad/solcalc/tmp/solcalc_676401856a77e.pdf
        val coordinates = Coordinates(15.22105, 102.49375)
        val zone = ZoneId.of("Asia/Bangkok")
        val examples = listOf("2020-01-01" to "17:51")
        examples.forEach { (date, expected) ->
            val actual = calculator.calculate(LocalDate.parse(date), coordinates, zone).sunset!!
            val reference = LocalDate.parse(date).atTime(LocalTime.parse(expected)).atZone(zone).toInstant()
            assertTrue("$date: $actual", abs(Duration.between(reference, actual).seconds) <= 60)
        }
    }

    @Test fun `polar day and night return no fabricated events`() {
        val tromso = Coordinates(69.6492, 18.9553)
        for (date in listOf("2026-06-21", "2026-12-21")) {
            val day = calculator.calculate(LocalDate.parse(date), tromso, ZoneId.of("Europe/Oslo"))
            assertNull(day.sunrise)
            assertNull(day.sunset)
        }
    }

    @Test fun `next event advances at sunrise sunset and local midnight`() {
        val date = LocalDate.of(2026, 6, 21)
        val day = calculator.calculate(date, delhi, india)
        assertEquals(day.sunrise, calculator.nextEvent(date.atStartOfDay(india).toInstant(), delhi, india)?.instant)
        assertEquals(SolarEventType.SUNSET, calculator.nextEvent(day.sunrise!!.plusSeconds(1), delhi, india)?.type)
        val afterSunset = calculator.nextEvent(day.sunset!!.plusSeconds(1), delhi, india)!!
        assertEquals(date.plusDays(1), afterSunset.instant.atZone(india).toLocalDate())
        assertEquals(SolarEventType.SUNRISE, afterSunset.type)
    }

    @Test fun `DST transition events stay within their local date`() {
        val zone = ZoneId.of("America/New_York")
        for (date in listOf("2026-03-08", "2026-11-01")) {
            val day = calculator.calculate(LocalDate.parse(date), Coordinates(40.7128, -74.006), zone)
            assertEquals(2, day.events.size)
            day.events.forEach { assertEquals(LocalDate.parse(date), it.instant.atZone(zone).toLocalDate()) }
        }
    }

    @Test fun `distance supports travel threshold and date line`() {
        assertEquals(0.0, delhi.distanceKmTo(delhi), 0.0001)
        assertTrue(delhi.distanceKmTo(Coordinates(28.9, 77.209)) > 25)
        assertEquals(22.239, Coordinates(0.0, 179.9).distanceKmTo(Coordinates(0.0, -179.9)), 0.01)
    }

    @Test fun `invalid and nonfinite coordinates are rejected`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0, -91.0)) {
            assertThrows(IllegalArgumentException::class.java) { Coordinates(value, 0.0) }
        }
        assertThrows(IllegalArgumentException::class.java) { Coordinates(0.0, 181.0) }
    }
}
