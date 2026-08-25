package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedParameterCacheTest {
    @Test
    fun decodedAospSignalDoesNotNeedNormalizedPropertyId() {
        val cache = UnifiedParameterCache()
        cache.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(
                snapshot(VehiclePropertySource.VHAL, 289_408_009, null, "Зажигание включено")
                    .copy(
                        decoded = true,
                        profileKey = "AOSP",
                        sourceDescription = "Обозначает состояние зажигания.",
                    ),
            ),
        )

        val parameter = cache.parameters().single()
        assertTrue(parameter.decoded)
        assertTrue(parameter.sourceReadings.single().decoded)
        assertEquals("Зажигание включено", parameter.value.display)
        assertEquals(
            "Обозначает состояние зажигания.",
            parameter.sourceReadings.single().description,
        )
    }

    @Test
    fun matchingPropertyAndAreaMergeWhileUnknownSignalsRemainSeparate() {
        val cache = UnifiedParameterCache()
        cache.replaceSource(
            VehiclePropertySource.ECARX,
            listOf(snapshot(VehiclePropertySource.ECARX, 1, 10021, "750")),
        )
        cache.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(
                snapshot(VehiclePropertySource.VHAL, 2, 10021, "748"),
                snapshot(VehiclePropertySource.VHAL, 3, null, "[1, 2]"),
            ),
        )

        val parameters = cache.parameters()
        val rpm = parameters.single { it.propertyId == CarPropertyId.ENGINE_RPM }
        assertEquals("748", rpm.value.display)
        assertEquals(
            listOf(VehiclePropertySource.VHAL, VehiclePropertySource.ECARX),
            rpm.sourceReadings.map { it.source },
        )
        assertEquals(1, parameters.count { it.propertyId == null })
    }

    @Test
    fun readableEcarxIsFallbackWhenMappedVhalFailed() {
        val cache = UnifiedParameterCache()
        cache.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(
                snapshot(
                    VehiclePropertySource.VHAL,
                    7,
                    10001,
                    "—",
                    VehiclePropertyStatus.ERROR,
                    raw = null,
                ),
            ),
        )
        cache.replaceSource(
            VehiclePropertySource.ECARX,
            listOf(snapshot(VehiclePropertySource.ECARX, 8, 10001, "42")),
        )

        val parameter = cache.parameters().single()
        assertEquals("42", parameter.value.display)
        assertEquals(VehiclePropertySource.ECARX, parameter.sourceReadings.first().source)
    }

    @Test
    fun replacingOneSourcePreservesOtherSource() {
        val cache = UnifiedParameterCache()
        cache.replaceSource(
            VehiclePropertySource.ECARX,
            listOf(snapshot(VehiclePropertySource.ECARX, 1, 10001, "40")),
        )
        cache.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(snapshot(VehiclePropertySource.VHAL, 2, 10001, "41")),
        )
        cache.replaceSource(VehiclePropertySource.VHAL, emptyList())

        val parameter = cache.parameters().single()
        assertEquals("40", parameter.value.display)
        assertEquals(listOf(VehiclePropertySource.ECARX), parameter.sourceReadings.map { it.source })
    }

    @Test
    fun liveUpdateMarksOnlyChangedRawValue() {
        val cache = UnifiedParameterCache()
        val initial = snapshot(VehiclePropertySource.VHAL, 2, 10001, "41")
        cache.replaceSource(VehiclePropertySource.VHAL, listOf(initial))

        assertFalse(cache.update(initial.copy(receivedAtMillis = 2)))
        assertFalse(cache.parameters().single().changedSinceScan)
        assertTrue(cache.update(initial.copy(displayValue = "42", rawValue = RawVehicleValue("42", 42.0))))
        assertTrue(cache.parameters().single().changedSinceScan)
    }

    @Test
    fun freshestDecodedReadingBecomesPrimary() {
        val cache = UnifiedParameterCache()
        cache.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(snapshot(VehiclePropertySource.VHAL, 2, 10001, "41", receivedAtMillis = 20)),
        )
        cache.replaceSource(
            VehiclePropertySource.ECARX,
            listOf(snapshot(VehiclePropertySource.ECARX, 1, 10001, "40", receivedAtMillis = 10)),
        )

        assertEquals("41", cache.parameters().single().value.display)

        cache.update(
            snapshot(VehiclePropertySource.ECARX, 1, 10001, "42", receivedAtMillis = 30),
        )

        val updated = cache.parameters().single()
        assertEquals("42", updated.value.display)
        assertEquals(VehiclePropertySource.ECARX, updated.sourceReadings.first().source)
    }

    private fun snapshot(
        source: VehiclePropertySource,
        signalId: Int,
        propertyId: Int?,
        display: String,
        status: VehiclePropertyStatus = VehiclePropertyStatus.AVAILABLE,
        raw: String? = display,
        receivedAtMillis: Long = 1,
    ) = CarPropertySnapshot(
        propertyId = propertyId?.let(::CarPropertyId),
        value = raw?.toDoubleOrNull()?.let(CarValue::FloatValue),
        displayValue = display,
        rawValue = raw?.let { RawVehicleValue(it, it.toDoubleOrNull()) },
        status = status,
        source = source,
        sourceSignalId = signalId,
        sourceSignalName = "signal_$signalId",
        sourceTitle = "Signal $signalId",
        receivedAtMillis = receivedAtMillis,
    )
}
