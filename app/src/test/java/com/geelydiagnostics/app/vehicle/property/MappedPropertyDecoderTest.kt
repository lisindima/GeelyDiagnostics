package com.geelydiagnostics.app.vehicle.property

import com.geelydiagnostics.app.vehicle.mapping.ReadSignalMapping
import com.geelydiagnostics.app.vehicle.mapping.ReadTransform
import com.geelydiagnostics.app.vehicle.mapping.ReadTransformStep
import com.geelydiagnostics.app.vehicle.mapping.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappedPropertyDecoderTest {
    private val catalog = object : CarPropertyCatalog {
        private val fuel = CarPropertyDefinition(
            CarPropertyId.REMAINING_FUEL_LITERS,
            CarValueType.INT,
            "fuel",
            1,
        )

        override fun definition(id: CarPropertyId): CarPropertyDefinition? =
            fuel.takeIf { it.id == id }

        override fun all(): List<CarPropertyDefinition> = listOf(fuel)
    }

    @Test
    fun keepsNormalizedAndRawValuesTogether() {
        val mapping = ReadSignalMapping(
            propertyId = CarPropertyId.REMAINING_FUEL_LITERS,
            signalId = 561025054,
            signalName = "fuel",
            transform = ReadTransform.Pipeline(
                listOf(ReadTransformStep.Arithmetic(Operator.DIVIDE, 1000.0)),
            ),
        )

        val result = MappedPropertyDecoder(catalog).decode(
            mapping = mapping,
            raw = RawVehicleValue("41700", 41700.0),
            sourceSignalId = mapping.signalId,
            sourceSignalName = mapping.signalName,
            areaId = 0,
            profileKey = "G426",
            sourceTimestampNanos = 42L,
            receivedAtMillis = 100L,
            autoUpdates = true,
        )

        assertEquals(CarValue.FloatValue(41.7), result.value)
        assertEquals("41.7 л", result.displayValue)
        assertEquals("41700", result.rawValue?.text)
        assertEquals(42L, result.sourceTimestampNanos)
        assertEquals(100L, result.receivedAtMillis)
    }

    @Test
    fun unknownSignalRemainsVisibleAsRaw() {
        val result = MappedPropertyDecoder(catalog).decode(
            mapping = null,
            raw = RawVehicleValue("[1, 2]"),
            sourceSignalId = 123,
            sourceSignalName = "VHAL_0x0000007B",
            areaId = 7,
            profileKey = null,
            sourceTimestampNanos = null,
            receivedAtMillis = 100L,
            autoUpdates = false,
        )

        assertNull(result.id)
        assertEquals("[1, 2]", result.displayValue)
        assertEquals(VehiclePropertyStatus.AVAILABLE, result.status)
    }

    @Test
    fun transformFailureKeepsRawAndReportsError() {
        val mapping = ReadSignalMapping(
            propertyId = CarPropertyId.REMAINING_FUEL_LITERS,
            signalId = 1,
            signalName = "fuel",
            transform = ReadTransform.Pipeline(
                listOf(ReadTransformStep.Mapping(emptyMap(), null)),
            ),
        )

        val result = MappedPropertyDecoder(catalog).decode(
            mapping,
            RawVehicleValue("7", 7.0),
            mapping.signalId,
            mapping.signalName,
            0,
            "G426",
            null,
            100L,
            false,
        )

        assertEquals(VehiclePropertyStatus.ERROR, result.status)
        assertEquals("7", result.rawValue?.text)
        assertTrue(result.error.contains("No mapping"))
    }
}
