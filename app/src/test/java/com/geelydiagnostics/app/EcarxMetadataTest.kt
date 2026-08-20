package com.geelydiagnostics.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcarxMetadataTest {

    @Test
    fun allResearchedMetadataIsBundled() {
        assertEquals(104, EcarxSensorMetadata.fields.size)
        assertEquals(9, EcarxSensorMetadata.fields.values.count { it.values.isNotEmpty() })
        assertEquals(73, EcarxCarInfoMetadata.fields.size)
        assertEquals(410, EcarxFunctionMetadata.fields.size)
        assertEquals(48, EcarxFunctionMetadata.fields.values.count { it.values.isNotEmpty() })
        assertEquals(71, EcarxFunctionMetadata.commonValueKeys.size)
    }

    @Test
    fun confirmedSensorScaleAddsUnitsAndPreservesRaw() {
        assertEquals(
            ApiValue(display = "36 км/ч", raw = "10"),
            VendorValueDecoder.sensor("SENSOR_TYPE_CAR_SPEED", 10f),
        )
    }

    @Test
    fun newlyImportedFunctionEnumIsDecoded() {
        assertEquals(
            ApiValue(display = "Уровень 4", raw = "3"),
            VendorValueDecoder.function(
                apiName = "SETTING_FUNC_RAIN_SENSOR_SENSITIVITY",
                rawValue = 3,
                supportedValues = null,
            ),
        )
        assertEquals(
            ApiValue(display = "Нет значения", raw = "254"),
            VendorValueDecoder.function(
                apiName = "SETTING_FUNC_AUTO_HOLD",
                rawValue = 254,
                supportedValues = null,
            ),
        )
    }

    @Test
    fun carInfoEnumsAndUnitsAreDecoded() {
        assertEquals(
            ApiValue(display = "Подключаемый гибрид PHEV", raw = "1049091"),
            VendorValueDecoder.carInfo("INT_INFO_VEHICLE_TYPES", 1049091),
        )
        assertEquals(
            ApiValue(display = "54 л", raw = "54"),
            VendorValueDecoder.carInfo("FLT_INFO_FUEL_CAPACITY", 54f),
        )
        assertTrue(EcarxCarInfoMetadata.field("INT_INFO_SPEAKER_TOTAL_COUNT") != null)
    }
}
