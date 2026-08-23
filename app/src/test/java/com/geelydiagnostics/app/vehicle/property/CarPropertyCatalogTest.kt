package com.geelydiagnostics.app.vehicle.property

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarPropertyCatalogTest {
    @Test
    fun parsesReadOnlyDefinitionsWithoutWriteMetadata() {
        val catalog = JsonCarPropertyCatalog(
            ByteArrayInputStream(
                """[
                    {"propertyId":10001,"valueType":"INT","description":"speed"},
                    {"propertyId":10005,"valueType":"FLOAT","decimalPlaces":1,"description":"temp"}
                ]""".trimIndent().toByteArray(),
            ),
        )

        assertEquals(2, catalog.all().size)
        assertEquals(CarValueType.INT, catalog.definition(CarPropertyId.VEHICLE_SPEED)?.valueType)
        assertEquals(1, catalog.definition(CarPropertyId.EXTERIOR_TEMPERATURE)?.decimalPlaces)
        assertNull(catalog.definition(CarPropertyId(99999)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateDefinitions() {
        JsonCarPropertyCatalog(
            ByteArrayInputStream(
                """[
                    {"propertyId":10001,"valueType":"INT","description":"a"},
                    {"propertyId":10001,"valueType":"INT","description":"b"}
                ]""".trimIndent().toByteArray(),
            ),
        )
    }
}
