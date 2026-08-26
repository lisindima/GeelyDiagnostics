package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.*
import org.junit.Assert.*
import org.junit.Test

class VehicleSourcePriorityPolicyTest {
    @Test fun profileThenAospThenEcarxRegardlessOfCallbackOrder() {
        val cache = UnifiedParameterCache()
        val profile = snapshot(VehiclePropertySource.VHAL, 1, "FX12", 100)
        val aosp = snapshot(VehiclePropertySource.VHAL, 2, "AOSP", 101)
        val ecarx = snapshot(VehiclePropertySource.ECARX, 3, null, 102)
        listOf(ecarx, aosp, profile).forEach(cache::update)
        assertEquals(1, cache.parameters(110).single().primaryReading.signalId)
        cache.update(profile.copy(status = VehiclePropertyStatus.ERROR))
        assertEquals(2, cache.parameters(110).single().primaryReading.signalId)
        cache.update(aosp.copy(status = VehiclePropertyStatus.UNAVAILABLE))
        assertEquals(3, cache.parameters(110).single().primaryReading.signalId)
        cache.update(profile.copy(receivedAtMillis = 111))
        val recovered = cache.parameters(111).single()
        assertEquals(1, recovered.primaryReading.signalId)
        assertEquals(3, recovered.sourceReadings.size)
        assertEquals(CarValue.FloatValue(42.0), recovered.normalizedValue)
    }

    @Test fun staleProfileFallsBackAndRecoversWithoutDiscardingSources() {
        val cache = UnifiedParameterCache()
        val profile = snapshot(VehiclePropertySource.VHAL, 1, "G426", 100)
        cache.update(profile)
        cache.update(snapshot(VehiclePropertySource.ECARX, 2, null, 110))
        assertEquals(1, cache.parameters(200).single().primaryReading.signalId) // exact boundary
        assertEquals(2, cache.parameters(201).single().primaryReading.signalId)
        cache.update(profile.copy(receivedAtMillis = 202))
        assertEquals(1, cache.parameters(202).single().primaryReading.signalId)
    }

    @Test fun staticAndOnChangeValuesDoNotExpire() {
        listOf(
            snapshot(VehiclePropertySource.VHAL, 1, "G426", 1).copy(autoUpdates = false),
            snapshot(VehiclePropertySource.VHAL, 1, "G426", 1).copy(expectedUpdateIntervalMillis = null),
        ).forEach { profile ->
            val cache = UnifiedParameterCache()
            cache.update(profile)
            cache.update(snapshot(VehiclePropertySource.ECARX, 2, null, 10_000))
            assertEquals(1, cache.parameters(10_001).single().primaryReading.signalId)
        }
    }

    @Test fun rawReadingCannotReplaceNormalizedReading() {
        val cache = UnifiedParameterCache()
        cache.update(snapshot(VehiclePropertySource.VHAL, 1, "G426", 1))
        cache.update(snapshot(VehiclePropertySource.ECARX, 2, null, 10_000).copy(decoded = false))
        assertEquals(1, cache.parameters(10_001).single().primaryReading.signalId)
    }

    @Test fun cacheClockRefreshCanSwitchPrimaryWithoutAnyVehicleRead() {
        var now = 200L
        val store = UnifiedParameterStore { now }
        store.update(snapshot(VehiclePropertySource.VHAL, 1, "G426", 100))
        store.update(snapshot(VehiclePropertySource.ECARX, 2, null, 110))
        val unchanged = store.parameters.value
        store.refreshSelection()
        assertSame(unchanged, store.parameters.value)
        now = 201
        store.refreshSelection()
        assertEquals(2, store.parameters.value.single().primaryReading.signalId)
    }

    @Test fun disconnectedSourceFallsBackWithoutWaitingForValueTimeout() {
        val store = UnifiedParameterStore { 110 }
        store.update(snapshot(VehiclePropertySource.VHAL, 1, "G426", 100))
        store.update(snapshot(VehiclePropertySource.ECARX, 2, null, 101))
        store.sourceAvailable(VehiclePropertySource.VHAL, false)
        assertEquals(2, store.parameters.value.single().primaryReading.signalId)
        store.sourceAvailable(VehiclePropertySource.VHAL, true)
        assertEquals(1, store.parameters.value.single().primaryReading.signalId)
    }

    private fun snapshot(source: VehiclePropertySource, id: Int, profile: String?, time: Long) =
        CarPropertySnapshot(propertyId = CarPropertyId.VEHICLE_SPEED, value = CarValue.FloatValue(42.0),
            displayValue = "42 км/ч", rawValue = RawVehicleValue("42", 42.0),
            status = VehiclePropertyStatus.AVAILABLE, source = source, sourceSignalId = id, sourceSignalName = "speed_$id",
            profileKey = profile, receivedAtMillis = time, autoUpdates = true,
            expectedUpdateIntervalMillis = 100)
}
