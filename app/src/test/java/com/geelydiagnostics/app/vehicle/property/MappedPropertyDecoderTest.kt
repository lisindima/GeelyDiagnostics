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
            CarValueType.FLOAT,
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

        assertNull(result.propertyId)
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

    @Test
    fun formatsVerifiedDecimalPlacesWithoutChangingRaw() {
        val temperatureId = CarPropertyId.EXTERIOR_TEMPERATURE
        val temperatureCatalog = catalogOf(
            CarPropertyDefinition(temperatureId, CarValueType.FLOAT, "temperature", 1),
        )
        val mapping = ReadSignalMapping(temperatureId, 10, "temperature")

        val result = MappedPropertyDecoder(temperatureCatalog).decode(
            mapping,
            RawVehicleValue("21.6", 21.6),
            mapping.signalId,
            mapping.signalName,
            0,
            "G426",
            null,
            100L,
            false,
        )

        assertEquals(CarValue.FloatValue(21.6), result.value)
        assertEquals("21.6 °C", result.displayValue)
        assertEquals("21.6", result.rawValue?.text)
    }

    @Test
    fun roundsConvertedSpeedToOneDecimalPlace() {
        val speedId = CarPropertyId.VEHICLE_SPEED
        val speedCatalog = catalogOf(
            CarPropertyDefinition(speedId, CarValueType.FLOAT, "speed", 1),
        )
        val mapping = ReadSignalMapping(
            propertyId = speedId,
            signalId = 10,
            signalName = "speed",
            transform = ReadTransform.Pipeline(
                listOf(ReadTransformStep.Arithmetic(Operator.MULTIPLY, 3.6)),
            ),
        )

        val result = MappedPropertyDecoder(speedCatalog).decode(
            mapping,
            RawVehicleValue("12.345678", 12.345678),
            mapping.signalId,
            mapping.signalName,
            0,
            "AOSP",
            null,
            100L,
            true,
        )

        assertEquals("44.4 км/ч", result.displayValue)
        assertEquals("12.345678", result.rawValue?.text)
    }

    @Test
    fun convertsZeroAndOneToTypedBoolean() {
        val acId = CarPropertyId(30001)
        val booleanCatalog = catalogOf(
            CarPropertyDefinition(acId, CarValueType.BOOLEAN, "ac"),
        )
        val mapping = ReadSignalMapping(acId, 11, "ac")

        val off = MappedPropertyDecoder(booleanCatalog).decode(
            mapping, RawVehicleValue("0", 0.0), 11, "ac", 0, "G426", null, 1L, true,
        )
        val on = MappedPropertyDecoder(booleanCatalog).decode(
            mapping, RawVehicleValue("1", 1.0), 11, "ac", 0, "G426", null, 2L, true,
        )

        assertEquals(CarValue.BooleanValue(false), off.value)
        assertEquals("Выключен", off.displayValue)
        assertEquals(CarValue.BooleanValue(true), on.value)
        assertEquals("Включён", on.displayValue)
    }

    @Test
    fun invalidBooleanRemainsRawAndBecomesExplicitError() {
        val acId = CarPropertyId(30001)
        val mapping = ReadSignalMapping(acId, 11, "ac")
        val result = MappedPropertyDecoder(
            catalogOf(CarPropertyDefinition(acId, CarValueType.BOOLEAN, "ac")),
        ).decode(
            mapping, RawVehicleValue("2", 2.0), 11, "ac", 0, "G426", null, 1L, true,
        )

        assertEquals(VehiclePropertyStatus.ERROR, result.status)
        assertEquals("2", result.displayValue)
        assertEquals("2", result.rawValue?.text)
        assertTrue(result.error.contains("BOOLEAN"))
    }

    @Test
    fun integerContractRejectsFractionInsteadOfSilentlyReturningFloat() {
        val id = CarPropertyId(10004)
        val decoder = MappedPropertyDecoder(catalogOf(CarPropertyDefinition(id, CarValueType.INT, "gear")))
        val result = decoder.decode(ReadSignalMapping(id, 1, "gear"), RawVehicleValue("1.5", 1.5),
            1, "gear", 0, "G426", null, 100, false)
        assertNull(result.value)
        assertEquals(VehiclePropertyStatus.ERROR, result.status)
        assertEquals("1.5", result.rawValue?.text)
    }

    private fun catalogOf(vararg definitions: CarPropertyDefinition): CarPropertyCatalog =
        object : CarPropertyCatalog {
            private val byId = definitions.associateBy(CarPropertyDefinition::id)

            override fun definition(id: CarPropertyId): CarPropertyDefinition? = byId[id]

            override fun all(): List<CarPropertyDefinition> = definitions.toList()
        }
}
