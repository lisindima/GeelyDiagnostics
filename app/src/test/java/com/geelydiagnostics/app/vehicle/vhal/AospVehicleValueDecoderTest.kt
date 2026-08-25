package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AospVehicleValueDecoderTest {
    @Test
    fun everyPublishedPropertyHasASafeGenericDecodePath() {
        AospVehiclePropertyIds.namesById.forEach { (propertyId, apiName) ->
            val type = propertyId and 0x00ff0000
            val raw = when (type) {
                0x00100000 -> RawVehicleValue("test")
                0x00410000, 0x00510000, 0x00610000, 0x00700000 ->
                    RawVehicleValue("[1, 2]", numbers = listOf(1.0, 2.0))
                else -> RawVehicleValue("1", 1.0)
            }

            AospVehicleValueDecoder.decode(propertyId, apiName, raw)
        }
    }

    @Test
    fun convertsDocumentedUnitsAndPreservesTypedNumber() {
        val result = decode("PERF_VEHICLE_SPEED", RawVehicleValue("12.345", 12.345))

        assertEquals("44.4 км/ч", result.displayValue)
        assertEquals(CarValue.FloatValue(44.442), result.value)
        assertTrue(result.decoded)
    }

    @Test
    fun decodesEnumsWithoutNormalizedPropertyId() {
        val result = decode("IGNITION_STATE", RawVehicleValue("4", 4.0))

        assertEquals("Зажигание включено", result.displayValue)
        assertEquals(CarValue.StringValue("Зажигание включено"), result.value)
        assertTrue(result.decoded)
    }

    @Test
    fun reportsUnknownEnumValueInsteadOfPretendingItWasDecoded() {
        val result = decode("IGNITION_STATE", RawVehicleValue("99", 99.0))

        assertEquals("Неизвестно (99)", result.displayValue)
        assertFalse(result.decoded)
    }

    @Test
    fun decodesBooleanFromPropertyTypeBits() {
        val result = decode("ABS_ACTIVE", RawVehicleValue("1", 1.0))

        assertEquals("Включено", result.displayValue)
        assertEquals(CarValue.BooleanValue(true), result.value)
        assertTrue(result.decoded)
    }

    @Test
    fun labelsDocumentedExteriorDimensionVector() {
        val result = decode(
            "INFO_EXTERIOR_DIMENSIONS",
            RawVehicleValue(
                text = "[1715, 4510, 1865]",
                numbers = listOf(1715.0, 4510.0, 1865.0),
            ),
        )

        assertEquals("Высота: 1715 мм · Длина: 4510 мм · Ширина: 1865 мм", result.displayValue)
        assertTrue(result.decoded)
    }

    @Test
    fun identifiesWheelAreaFromAospAreaBits() {
        val propertyId = id("TIRE_PRESSURE")

        assertEquals(
            "Переднее левое колесо",
            AospVehicleValueDecoder.areaLabel(propertyId, 1),
        )
    }

    private fun decode(apiName: String, raw: RawVehicleValue): AospDecodedValue =
        AospVehicleValueDecoder.decode(id(apiName), apiName, raw)

    private fun id(apiName: String): Int = AospVehiclePropertyIds.id(apiName)
}
