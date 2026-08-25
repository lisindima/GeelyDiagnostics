package com.geelydiagnostics.app.vehicle.vhal

import android.car.VehiclePropertyIds
import com.geelydiagnostics.app.vehicle.mapping.TransformResult
import com.geelydiagnostics.app.vehicle.mapping.TransformValue
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class AndroidVehiclePropertyRegistryTest {
    @Test
    fun containsEveryPublishedAospPropertyIncludingIdsNewerThanCompileSdk() {
        assertEquals(250, AospVehiclePropertyIds.namesById.size)
        AospVehiclePropertyIds.namesById.forEach { (id, name) ->
            assertEquals(name, requireNotNull(AndroidVehiclePropertyRegistry.property(id)).apiName)
        }
        val acceleratorPedal = requireNotNull(
            AndroidVehiclePropertyRegistry.property(291_504_911),
        )

        assertEquals("ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE", acceleratorPedal.apiName)
        assertEquals("Положение педали акселератора", acceleratorPedal.title)
        assertEquals("Процент сжатия педали акселератора.", acceleratorPedal.description)
        assertEquals("AOSP", acceleratorPedal.profileKey)
    }

    @Test
    fun everyPublishedPropertyExceptInvalidHasARussianDescription() {
        val describedIds = AospVehiclePropertyDescriptions.byId.keys

        assertEquals(AospVehiclePropertyIds.namesById.keys - setOf(0), describedIds)
        AospVehiclePropertyDescriptions.byId.values.forEach { description ->
            assertTrue(description.isNotBlank())
            assertTrue(description.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' })
        }
    }

    @Test
    fun explicitAospCatalogIncludesEveryPropertyAvailableInCompileSdk() {
        val compileSdkProperties = VehiclePropertyIds::class.java.fields
            .filter { field ->
                field.type == Int::class.javaPrimitiveType && Modifier.isStatic(field.modifiers)
            }

        compileSdkProperties.forEach { field ->
            assertEquals(field.name, AospVehiclePropertyIds.namesById[field.getInt(null)])
        }
    }

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
    fun classifiesAospPropertiesIntoUnifiedApplicationSections() {
        assertEquals(
            VehicleDataSection.VEHICLE_INFO,
            requireNotNull(AndroidVehiclePropertyRegistry.property(VehiclePropertyIds.INFO_MODEL)).section,
        )
        assertEquals(
            VehicleDataSection.CAPABILITY,
            requireNotNull(AndroidVehiclePropertyRegistry.property(VehiclePropertyIds.HVAC_AC_ON)).section,
        )
        assertEquals(
            VehicleDataSection.PARAMETER,
            requireNotNull(
                AndroidVehiclePropertyRegistry.property(VehiclePropertyIds.PERF_VEHICLE_SPEED),
            ).section,
        )
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
            VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
            raw = 10.0,
            expectedProperty = CarPropertyId.DISPLAY_VEHICLE_SPEED,
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
        assertNumber(
            AospVehiclePropertyIds.id("ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE"),
            raw = 42.5,
            expectedProperty = CarPropertyId.ACCELERATOR_POSITION,
            expected = 42.5,
        )
        assertNumber(
            AospVehiclePropertyIds.id("BRAKE_PEDAL_COMPRESSION_PERCENTAGE"),
            raw = 18.0,
            expectedProperty = CarPropertyId.BRAKE_POSITION,
            expected = 18.0,
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

    @Test
    fun doesNotMergeFrontWheelAngleWithSteeringWheelAngle() {
        val property = requireNotNull(
            AndroidVehiclePropertyRegistry.property(VehiclePropertyIds.PERF_STEERING_ANGLE),
        )

        assertEquals("Угол передних управляемых колёс", property.title)
        assertNull(property.normalizedMapping)
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
