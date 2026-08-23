package com.geelydiagnostics.app.vehicle.property

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EcarxNormalizedPropertyRegistryTest {
    @Test
    fun mapsOnlyExplicitUnambiguousSensorNames() {
        assertEquals(
            CarPropertyId.VEHICLE_SPEED,
            EcarxNormalizedPropertyRegistry.sensorProperty("SENSOR_TYPE_CAR_SPEED"),
        )
        assertEquals(
            CarPropertyId.REMAINING_FUEL_PERCENT,
            EcarxNormalizedPropertyRegistry.sensorProperty("SENSOR_TYPE_FUEL_LEVEL"),
        )
        assertNull(EcarxNormalizedPropertyRegistry.sensorProperty("SENSOR_TYPE_CAR_SPEED_FROM_IPK"))
        assertNull(EcarxNormalizedPropertyRegistry.sensorProperty("SENSOR_TYPE_UNKNOWN"))
    }
}
