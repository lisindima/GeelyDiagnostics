package com.geelydiagnostics.app.vehicle.vhal

import android.car.VehiclePropertyIds
import com.geelydiagnostics.app.vehicle.mapping.TransformResult
import com.geelydiagnostics.app.vehicle.mapping.TransformValue
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVehiclePropertyRegistryTest {
    @Test
    fun namesEveryPublicAndroidPropertyWithoutGuessingVendorIds() {
        val model = requireNotNull(
            AndroidVehiclePropertyRegistry.property(VehiclePropertyIds.INFO_MODEL),
        )

        assertEquals("INFO_MODEL", model.apiName)
        assertEquals("Модель", model.title)
        assertEquals("AOSP", model.profileKey)
        assertNull(model.normalizedMapping)
        assertNull(AndroidVehiclePropertyRegistry.property(0x21401234))
    }

    @Test
    fun convertsAospUnitsToNormalizedCatalogUnits() {
        assertNumber(
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            raw = 10.0,
            expectedProperty = CarPropertyId.VEHICLE_SPEED,
            expected = 36.0,
        )
        assertNumber(
            VehiclePropertyIds.FUEL_LEVEL,
            raw = 41_700.0,
            expectedProperty = CarPropertyId.REMAINING_FUEL_LITERS,
            expected = 41.7,
        )
        assertNumber(
            VehiclePropertyIds.RANGE_REMAINING,
            raw = 123_400.0,
            expectedProperty = CarPropertyId.REMAINING_RANGE,
            expected = 123.4,
        )
    }

    @Test
    fun decodesVehicleGearBitValuesAndKeepsDifferentGearSemanticsSeparate() {
        assertText(
            VehiclePropertyIds.GEAR_SELECTION,
            raw = 4.0,
            expectedProperty = CarPropertyId.GEAR,
            expected = "P",
        )
        assertNumber(
            VehiclePropertyIds.CURRENT_GEAR,
            raw = 128.0,
            expectedProperty = CarPropertyId.TRANSMISSION_GEAR,
            expected = 4.0,
        )
    }

    private fun assertNumber(
        androidPropertyId: Int,
        raw: Double,
        expectedProperty: CarPropertyId,
        expected: Double,
    ) {
        val property = requireNotNull(AndroidVehiclePropertyRegistry.property(androidPropertyId))
        val mapping = requireNotNull(property.normalizedMapping)
        assertEquals(expectedProperty, mapping.propertyId)
        val result = mapping.transform.apply(RawVehicleValue(raw.toString(), raw))
        assertTrue(result is TransformResult.Success)
        assertEquals(
            TransformValue.NumberValue(expected),
            (result as TransformResult.Success).value,
        )
    }

    private fun assertText(
        androidPropertyId: Int,
        raw: Double,
        expectedProperty: CarPropertyId,
        expected: String,
    ) {
        val property = requireNotNull(AndroidVehiclePropertyRegistry.property(androidPropertyId))
        val mapping = requireNotNull(property.normalizedMapping)
        assertEquals(expectedProperty, mapping.propertyId)
        val result = mapping.transform.apply(RawVehicleValue(raw.toString(), raw))
        assertTrue(result is TransformResult.Success)
        assertEquals(
            TransformValue.StringValue(expected),
            (result as TransformResult.Success).value,
        )
    }
}
