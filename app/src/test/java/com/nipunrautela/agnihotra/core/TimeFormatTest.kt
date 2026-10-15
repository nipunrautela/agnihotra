package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class TimeFormatTest {
    @Test fun `Devanagari and Unicode minus coordinates are accepted`() {
        assertEquals(28.6139, " २८.६१३९ ".coordinateNumberOrNull()!!, 0.000001)
        assertEquals(-77.209, "−७७.२०९".coordinateNumberOrNull()!!, 0.000001)
    }

    @Test fun `non finite and ambiguous coordinates are rejected`() {
        listOf("NaN", "Infinity", "1e9999", "28,6139", "", "28 61").forEach {
            assertNull(it.coordinateNumberOrNull())
        }
    }

    @Test fun `12 and 24 hour formats use the requested zone and seconds`() {
        val instant = Instant.parse("2026-10-15T00:45:12Z")
        val zone = ZoneId.of("Asia/Kolkata")
        assertEquals("06:15", instant.formatTime(zone, true, Locale.ENGLISH))
        assertEquals("06:15:12", instant.formatTime(zone, true, Locale.ENGLISH, true))
        assertEquals("6:15 AM", instant.formatTime(zone, false, Locale.ENGLISH))
    }
}
