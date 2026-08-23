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

    @Test(expected = IllegalStateException::class)
    fun rejectsWriteSignalNameEvenWithoutWriteId() {
        JsonVehicleProfileMappingLoader().load(
            VehicleProfile.G426,
            ByteArrayInputStream(
                """[
                    {
                      "propertyId":10012,
                      "readSignalId":1,
                      "readSignalName":"fuel",
                      "writeSignalName":"must-not-exist"
                    }
                ]""".trimIndent().toByteArray(),
            ),
        )
    }

    @Test
    fun acceptsNamedMappingsArrayForFutureSources() {
        val mapping = JsonVehicleProfileMappingLoader().load(
            VehicleProfile.G426,
            ByteArrayInputStream(
                """{"mappings":[
                    {"propertyId":10001,"readSignalId":7,"readSignalName":"speed"}
                ]}""".trimIndent().toByteArray(),
            ),
        )

        assertEquals(CarPropertyId.VEHICLE_SPEED, mapping.forSignal(7)?.propertyId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateLogicalProperties() {
        VehicleProfileMapping(
            VehicleProfile.G426,
            listOf(
                ReadSignalMapping(CarPropertyId.VEHICLE_SPEED, 1, "speed-a"),
                ReadSignalMapping(CarPropertyId.VEHICLE_SPEED, 2, "speed-b"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateSourceSignals() {
        VehicleProfileMapping(
            VehicleProfile.G426,
            listOf(
                ReadSignalMapping(CarPropertyId.VEHICLE_SPEED, 1, "speed"),
                ReadSignalMapping(CarPropertyId.GEAR, 1, "gear"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rawProfileCannotLoadExternalMapping() {
        JsonVehicleProfileMappingLoader().load(
            VehicleProfile.RAW,
            ByteArrayInputStream("[]".toByteArray()),
        )
    }
}
