package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedParameterStoreTest {
    @Test
    fun observeUsesPropertyIdAndAreaInsteadOfSourceSignalIdentity() = runBlocking {
        val store = UnifiedParameterStore()
        store.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(snapshot(signalId = 9001, areaId = 7, raw = "236")),
        )

        val observed = store.observe(CarPropertyId(10013), areaId = 7).first()

        assertEquals("236", observed?.value?.raw)
        assertNull(store.observe(CarPropertyId(10013), areaId = 0).first())
    }

    @Test
    fun clearingStorePublishesMissingProperty() = runBlocking {
        val store = UnifiedParameterStore()
        store.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(snapshot(signalId = 9001, areaId = 0, raw = "236")),
        )
        store.clear()

        assertNull(store.observe(CarPropertyId(10013)).first())
        assertTrue(store.parameters.value.isEmpty())
    }

    @Test
    fun observationUsesSectionAsPartOfNormalizedIdentity() = runBlocking {
        val store = UnifiedParameterStore()
        val parameter = snapshot(signalId = 9001, areaId = 0, raw = "236")
        store.replaceSource(
            VehiclePropertySource.VHAL,
            listOf(parameter, parameter.copy(section = VehicleDataSection.CAPABILITY)),
        )

        assertEquals(
            VehicleDataSection.PARAMETER,
            store.observe(CarPropertyId(10013)).first()?.section,
        )
        assertEquals(
            VehicleDataSection.CAPABILITY,
            store.observe(
                CarPropertyId(10013),
                section = VehicleDataSection.CAPABILITY,
            ).first()?.section,
        )
    }

    private fun snapshot(signalId: Int, areaId: Int, raw: String) = CarPropertySnapshot(
        propertyId = CarPropertyId(10013),
        value = CarValue.FloatValue(raw.toDouble()),
        displayValue = "$raw кПа",
        rawValue = RawVehicleValue(raw, raw.toDouble()),
        status = VehiclePropertyStatus.AVAILABLE,
        source = VehiclePropertySource.VHAL,
        sourceSignalId = signalId,
        sourceSignalName = "TIRE_PRESSURE",
        areaId = areaId,
        receivedAtMillis = 1L,
    )
}
