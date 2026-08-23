package com.geelydiagnostics.app.vehicle.mapping

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleProfileMappingTest {
    @Test
    fun parsesTopLevelAutoServiceArray() {
        val mapping = JsonVehicleProfileMappingLoader().load(
            VehicleProfile.G426,
            ByteArrayInputStream(
                """[
                    {
                      "propertyId":10012,
                      "readSignalId":561025054,
                      "readSignalName":"fuel",
                      "readTransform":{"steps":[{"type":"expression","expression":"x / 1000"}]}
                    }
                ]""".trimIndent().toByteArray(),
            ),
        )

        assertEquals(CarPropertyId.REMAINING_FUEL_LITERS, mapping.forSignal(561025054)?.propertyId)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsAnyWriteFields() {
        JsonVehicleProfileMappingLoader().load(
            VehicleProfile.G426,
            ByteArrayInputStream(
                """[
                    {
                      "propertyId":10012,
                      "readSignalId":1,
                      "readSignalName":"fuel",
                      "writeSignalId":2
                    }
                ]""".trimIndent().toByteArray(),
            ),
        )
    }
}
