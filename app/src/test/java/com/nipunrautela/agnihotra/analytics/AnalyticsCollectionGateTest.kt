package com.nipunrautela.agnihotra.analytics

import org.junit.Assert.*
import org.junit.Test

class AnalyticsCollectionGateTest {
    @Test fun `collection waits for preferences and respects a restored opt out`() {
        val gate = AnalyticsCollectionGate()
        assertFalse(gate.enabled)
        gate.applyPersisted(false)
        assertFalse(gate.enabled)
        gate.applyPersisted(true)
        assertTrue(gate.enabled)
    }

    @Test fun `queued preference emissions cannot reverse an explicit opt out`() {
        val gate = AnalyticsCollectionGate()
        gate.applyPersisted(true)
        gate.beginChoice()
        gate.applyPersisted(true)
        assertFalse(gate.enabled)
        gate.choicePersisted(false)
        gate.applyPersisted(true)
        assertFalse(gate.enabled)
    }

    @Test fun `opt in requires a completed write and failed writes stay disabled`() {
        val gate = AnalyticsCollectionGate()
        gate.applyPersisted(false)
        gate.beginChoice()
        gate.applyPersisted(true)
        assertFalse(gate.enabled)
        gate.choicePersisted(true)
        gate.applyPersisted(false)
        assertTrue(gate.enabled)
    }

    @Test fun `successive choices keep the latest committed setting`() {
        val gate = AnalyticsCollectionGate()
        listOf(true, false, true, false).forEach { value ->
            gate.beginChoice()
            assertFalse(gate.enabled)
            gate.choicePersisted(value)
            gate.applyPersisted(!value)
            assertEquals(value, gate.enabled)
        }
    }
}
