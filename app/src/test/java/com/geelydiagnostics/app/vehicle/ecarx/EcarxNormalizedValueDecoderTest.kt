package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.vehicle.mapping.ReadTransform
import com.geelydiagnostics.app.vehicle.property.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class EcarxNormalizedValueDecoderTest {
    private val catalog = sequenceOf(File("src/main/assets/vehicle/car_properties.json"),
        File("app/src/main/assets/vehicle/car_properties.json"))
        .first(File::isFile).inputStream().use(::JsonCarPropertyCatalog)
    private val decoder = EcarxNormalizedValueDecoder(catalog)

    @Test fun speedIsNormalizedBeforeFormatting() {
        val result = decode("SENSOR_TYPE_CAR_SPEED", "10.0")
        assertEquals(CarPropertyId.VEHICLE_SPEED, result.propertyId)
        assertEquals(CarValue.FloatValue(36.0), result.value)
        assertEquals("36 км/ч", result.displayValue)
        assertEquals("10.0", result.rawValue?.text)
        assertEquals(VehicleMappingOrigin.ECARX, result.mappingOrigin)
        assertTrue(result.readTransform is ReadTransform.Pipeline)
    }

    @Test fun gearUsesCanonicalCharacterAndNoDefault() {
        assertEquals(CarValue.CharValue('D'), decode("SENSOR_TYPE_GEAR", "2097696").value)
        listOf("-1", "999", "2097696.5", "2097674").forEach { raw ->
            val unknown = decode("SENSOR_TYPE_GEAR", raw)
            assertNull(unknown.value)
            assertFalse(unknown.decoded)
            assertTrue(unknown.hasEcarxSample)
            assertEquals(raw, unknown.rawValue?.text)
        }
    }

    @Test fun seatBeltUsesCatalogStateCodes() {
        assertEquals(CarValue.IntValue(1), decode("SENSOR_TYPE_SAFE_BELT_DRIVER", "2101761").value)
        assertEquals(CarValue.IntValue(2), decode("SENSOR_TYPE_SAFE_BELT_DRIVER", "2101762").value)
        assertNull(decode("SENSOR_TYPE_SAFE_BELT_DRIVER", "-1").value)
    }

    @Test fun continuousIdentityMappingsPreserveFractionalMeasurements() {
        listOf("SENSOR_TYPE_TEMPERATURE_AMBIENT", "SENSOR_TYPE_RPM", "SENSOR_TYPE_FUEL_LEVEL",
            "SENSOR_TYPE_ENDURANCE_MILEAGE", "SENSOR_TYPE_BRAKE_DEPTH", "SENSOR_TYPE_STEERING_WHEEL_ANGLE",
            "SENSOR_TYPE_ENDURANCE_MILEAGE_EV").forEach { api ->
            assertEquals(api, CarValue.FloatValue(12.5), decode(api, "12.5").value)
        }
    }

    @Test fun noNewUnverifiedNormalizedIdsAreInferredFromDisplayLabels() {
        listOf("SENSOR_TYPE_IGNITION_STATE", "SENSOR_TYPE_BRAKE_FLUID_LEVEL", "SENSOR_TYPE_CAR_MODE")
            .forEach { api -> assertNull(decoder.decode(api, 1, RawVehicleValue("1", 1.0), 100)) }
    }

    @Test fun nonFiniteNumbersRemainRawErrors() {
        listOf("NaN", "Infinity").forEach { raw ->
            val result = decode("SENSOR_TYPE_CAR_SPEED", raw)
            assertNull(result.value)
            assertFalse(result.decoded)
            assertEquals(raw, result.rawValue?.text)
        }
    }

    private fun decode(api: String, raw: String): CarPropertySnapshot = requireNotNull(
        decoder.decode(api, 1, RawVehicleValue(raw, raw.toDoubleOrNull()), 100),
    )
}
