package com.nipunrautela.agnihotra.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AreaLabelTest {
    @Test fun prefersNeighbourhoodAndCityWithoutDuplicateNames() {
        assertEquals("Indiranagar, Bengaluru", areaLabel(" Indiranagar ", "Bengaluru", "Bengaluru Urban", "Karnataka"))
        assertEquals("Delhi", areaLabel("Delhi", "delhi", null, null))
        assertEquals("लक्ष्मी नगर, दिल्ली", areaLabel("लक्ष्मी नगर", "दिल्ली", null, null))
    }

    @Test fun fallsBackThroughAvailableAdministrativeAreas() {
        assertEquals("Delhi", areaLabel(null, "Delhi", "New Delhi", "Delhi"))
        assertEquals("Almora", areaLabel(" ", null, "Almora", "Uttarakhand"))
        assertEquals("Uttarakhand", areaLabel(null, "", null, "Uttarakhand"))
        assertNull(areaLabel(null, " ", null, ""))
    }
}
