package com.geelydiagnostics.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorValueDecoderTest {

    @Test
    fun gearValuesAreDecodedAndRawIsPreserved() {
        mapOf(
            2097665 to "1",
            2097666 to "2",
            2097667 to "3",
            2097696 to "D",
            2097680 to "N",
            2097712 to "P",
            2097728 to "R",
        ).forEach { (raw, display) ->
            assertEquals(
                ApiValue(display = display, raw = raw.toString()),
                VendorValueDecoder.sensor("SENSOR_TYPE_GEAR", raw),
            )
        }
    }

    @Test
    fun chargingGearLevelIsNotMistakenForTransmissionGear() {
        assertEquals(
            ApiValue(display = "609225730", raw = "609225730"),
            VendorValueDecoder.sensor("SENSOR_TYPE_GEAR", 609225730),
        )
    }

    @Test
    fun ignitionValueIsDecoded() {
        assertEquals(
            ApiValue(display = "Движение", raw = "2097415"),
            VendorValueDecoder.sensor("SENSOR_TYPE_IGNITION_STATE", 2097415),
        )
    }

    @Test
    fun otherConfirmedSensorEnumsAreDecoded() {
        assertEquals(
            ApiValue(display = "Под охраной", raw = "2122498"),
            VendorValueDecoder.sensor("SENSOR_TYPE_ALRM_STS", 2122498),
        )
        assertEquals(
            ApiValue(display = "Пристёгнут", raw = "2101762"),
            VendorValueDecoder.sensor("SENSOR_TYPE_SAFE_BELT_DRIVER", 2101762),
        )
        assertEquals(
            ApiValue(display = "Предупреждение 2", raw = "3149829"),
            VendorValueDecoder.sensor("SENSOR_TYPE_DRIVER_TIREDNESS_STATUS", 3149829),
        )
    }

    @Test
    fun unknownSensorValueStaysRaw() {
        assertEquals(
            ApiValue(display = "123456", raw = "123456"),
            VendorValueDecoder.sensor("SENSOR_TYPE_GEAR", 123456),
        )
    }

    @Test
    fun knownFunctionEnumIsDecoded() {
        assertEquals(
            ApiValue(display = "Обычно", raw = "537133826"),
            VendorValueDecoder.function("SETTING_FUNC_LAMP_AUTOLIGHT", 537133826, null),
        )
    }

    @Test
    fun confirmedBooleanFunctionIsDecoded() {
        assertEquals(
            ApiValue(display = "Включено", raw = "1"),
            VendorValueDecoder.function("SETTING_FUNC_AUTO_HOLD", 1, intArrayOf(0, 1)),
        )
    }
}
