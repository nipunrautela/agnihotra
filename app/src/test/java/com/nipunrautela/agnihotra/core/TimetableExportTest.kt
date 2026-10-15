package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimetableExportTest {
    private val coordinates = Coordinates(28.6139, 77.209)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = SolarDay(LocalDate.of(2026, 10, 15), Instant.parse("2026-10-15T00:45:12Z"),
        Instant.parse("2026-10-15T12:15:34Z"))

    @Test fun `Hindi names quotes and commas survive export with explicit zone and seconds`() {
        val csv = timetableCsv("घर, \"दिल्ली\"", coordinates, zone, listOf(day))
        assertTrue(csv.startsWith("\uFEFFPlace,Latitude,Longitude,Time zone,Date,Sunrise,Sunset\r\n"))
        assertTrue(csv.contains("\"घर, \"\"दिल्ली\"\"\",28.6139,77.209,\"Asia/Kolkata\",2026-10-15,06:15:12,17:45:34\r\n"))
    }

    @Test fun `formula-like user names are escaped for spreadsheet viewers`() {
        listOf("=1+1", "+SUM(1)", "-1+1", "@SUM(1)", "\t=1", "\r=1").forEach { name ->
            assertTrue(timetableCsv(name, coordinates, zone, listOf(day)).contains("\"'$name\""))
        }
    }

    @Test fun `missing polar events remain empty and preserve date`() {
        val csv = timetableCsv("Tromso", Coordinates(69.65, 18.96), ZoneId.of("Europe/Oslo"),
            listOf(SolarDay(LocalDate.of(2026, 6, 21), null, null)))
        assertTrue(csv.endsWith("2026-06-21,,\r\n"))
    }

    @Test fun `saved zone wins over phone zone at UTC date boundary`() {
        val csv = timetableCsv("घर", coordinates, zone, listOf(day.copy(sunrise = Instant.parse("2026-10-14T23:45:12Z"))))
        assertTrue(csv.contains("2026-10-15,05:15:12,17:45:34"))
    }
}
