package com.nipunrautela.agnihotra.core

import org.junit.Assert.*
import org.junit.Test

class LocationQualityTest {
    @Test fun `fresh approximate and precise fixes are usable`() {
        assertTrue(usableLocationFix(28.6, 77.2, 10f, 0))
        assertTrue(usableLocationFix(28.6, 77.2, 3000f, 120_000_000_000L))
    }

    @Test fun `stale future and missing accuracy fixes are rejected`() {
        assertFalse(usableLocationFix(28.6, 77.2, 10f, -1))
        assertFalse(usableLocationFix(28.6, 77.2, 10f, 120_000_000_001L))
        assertFalse(usableLocationFix(28.6, 77.2, null, 0))
    }

    @Test fun `malformed provider values cannot crash coordinate construction`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0).forEach {
            assertFalse(usableLocationFix(it, 77.2, 10f, 0))
        }
        assertFalse(usableLocationFix(28.6, -181.0, 10f, 0))
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach {
            assertFalse(usableLocationFix(28.6, 77.2, it, 0))
        }
    }
}
