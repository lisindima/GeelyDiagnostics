package com.geelydiagnostics.app.vehicle.mapping

import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadTransformTest {
    @Test
    fun appliesG426FuelScale() {
        val transform = ReadTransformParser.parse(
            JSONObject("""{"steps":[{"type":"expression","expression":"x / 1000"}]}"""),
        )

        assertEquals(
            TransformResult.Success(TransformValue.NumberValue(41.7)),
            transform.apply(RawVehicleValue(text = "41700", number = 41700.0)),
        )
    }

    @Test
    fun mapsGearAndUsesExplicitDefault() {
        val transform = ReadTransformParser.parse(
            JSONObject(
                """{"steps":[{"type":"mapping","map":{"3":"D"},"default":"-"}]}""",
            ),
        )

        assertEquals(
            TransformResult.Success(TransformValue.StringValue("D")),
            transform.apply(RawVehicleValue(text = "3", number = 3.0)),
        )
        assertEquals(
            TransformResult.Success(TransformValue.StringValue("-")),
            transform.apply(RawVehicleValue(text = "9", number = 9.0)),
        )
    }

    @Test
    fun reportsMissingMappingInsteadOfSilentlyReturningRaw() {
        val transform = ReadTransform.Pipeline(
            listOf(ReadTransformStep.Mapping(mapOf("1" to TransformValue.StringValue("on")), null)),
        )

        assertTrue(transform.apply(RawVehicleValue("2", 2.0)) is TransformResult.Failure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDivisionByZeroAtLoadTime() {
        ReadTransformParser.parse(
            JSONObject("""{"steps":[{"type":"expression","expression":"x / 0"}]}"""),
        )
    }
}
