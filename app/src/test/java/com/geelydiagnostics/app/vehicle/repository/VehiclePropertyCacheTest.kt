package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePropertyCacheTest {
    @Test
    fun sameSignalInDifferentAreasUsesDifferentKeys() {
        val cache = VehiclePropertyCache()
        val left = value(areaId = 1, raw = "210")
        val right = value(areaId = 2, raw = "220")

        cache.replace(listOf(left, right))

        assertEquals(listOf(left, right), cache.values())
        assertFalse(cache.changedSinceSnapshot(left.key))
        assertFalse(cache.changedSinceSnapshot(right.key))
    }

    @Test
    fun identicalLiveValueDoesNotMarkPropertyAsChanged() {
        val cache = VehiclePropertyCache()
        val initial = value(raw = "41.7")
        cache.replace(listOf(initial))

        assertFalse(cache.update(initial.copy(receivedAtMillis = 200L)))
        assertFalse(cache.changedSinceSnapshot(initial.key))
    }

    @Test
    fun changedFlagSurvivesFollowingUpdatesUntilNextSnapshot() {
        val cache = VehiclePropertyCache()
        val initial = value(raw = "41.7")
        val changed = value(raw = "40.5")
        cache.replace(listOf(initial))

        assertTrue(cache.update(changed))
        assertTrue(cache.changedSinceSnapshot(changed.key))
        assertFalse(cache.update(changed.copy(receivedAtMillis = 300L)))
        assertTrue(cache.changedSinceSnapshot(changed.key))

        cache.replace(listOf(changed))
        assertFalse(cache.changedSinceSnapshot(changed.key))
    }

    @Test
    fun unknownRawPropertyIsNotDropped() {
        val cache = VehiclePropertyCache()
        val unknown = value(raw = "[1, 2]").copy(
            id = null,
            value = CarValue.StringValue("[1, 2]"),
            displayValue = "[1, 2]",
        )

        cache.replace(listOf(unknown))

        assertEquals(unknown, cache.values().single())
        assertNull(cache.values().single().id)
    }

    private fun value(areaId: Int = 0, raw: String): CarPropertySnapshot = CarPropertySnapshot(
        id = CarPropertyId.REMAINING_FUEL_LITERS,
        value = CarValue.FloatValue(raw.toDoubleOrNull() ?: 0.0),
        displayValue = raw,
        rawValue = RawVehicleValue(raw, raw.toDoubleOrNull()),
        status = VehiclePropertyStatus.AVAILABLE,
        source = VehiclePropertySource.VHAL,
        sourceSignalId = 561025054,
        sourceSignalName = "fuel",
        areaId = areaId,
        profileKey = "G426",
        receivedAtMillis = 100L,
    )
}
