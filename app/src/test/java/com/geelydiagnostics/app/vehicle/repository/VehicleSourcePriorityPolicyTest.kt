package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.*
import org.junit.Assert.*
import org.junit.Test

class VehicleSourcePriorityPolicyTest {
    @Test fun profileFuelLitersWinOverAospAndKeepRawAndValueTogether() {
        // Deliberately different test readings, not an assertion about actual tank capacity.
        val profile = snapshot(VehiclePropertySource.VHAL, 561025054, "G426", 100).copy(
            propertyId = CarPropertyId.REMAINING_FUEL_LITERS,
            value = CarValue.FloatValue(50.0), displayValue = "50 л",
            rawValue = RawVehicleValue("50000", 50000.0),
        )
        val aosp = snapshot(VehiclePropertySource.VHAL, 291504903, "AOSP", 101).copy(
            propertyId = CarPropertyId.REMAINING_FUEL_LITERS,
            value = CarValue.FloatValue(15.0), displayValue = "15 л",
            rawValue = RawVehicleValue("15000", 15000.0),
        )
        listOf(listOf(profile, aosp), listOf(aosp, profile)).forEach { readings ->
            val cache = UnifiedParameterCache()
            readings.forEach(cache::update)
            val fuel = cache.parameters(110).single()
            assertEquals(profile.sourceSignalId, fuel.primaryReading.signalId)
            assertEquals("50 л", fuel.value.display)
            assertEquals("50000", fuel.value.raw)
            assertEquals(CarValue.FloatValue(50.0), fuel.normalizedValue)
            assertEquals(2, fuel.sourceReadings.size)

            cache.update(profile.copy(status = VehiclePropertyStatus.ERROR))
            val fallback = cache.parameters(110).single()
            assertEquals(aosp.sourceSignalId, fallback.primaryReading.signalId)
            assertEquals("15 л", fallback.value.display)
            assertEquals("15000", fallback.value.raw)
        }
    }

    @Test fun profileThenEcarxThenAospRegardlessOfCallbackOrder() {
        val cache = UnifiedParameterCache()
        val profile = snapshot(VehiclePropertySource.VHAL, 1, "FX12", 100)
        val aosp = snapshot(VehiclePropertySource.VHAL, 2, "AOSP", 101)
        val ecarx = snapshot(VehiclePropertySource.ECARX, 3, null, 102)
        listOf(ecarx, aosp, profile).forEach(cache::update)
        assertEquals(1, cache.parameters(110).single().primaryReading.signalId)
        cache.update(profile.copy(status = VehiclePropertyStatus.ERROR))
        assertEquals(3, cache.parameters(110).single().primaryReading.signalId)
        cache.update(ecarx.copy(status = VehiclePropertyStatus.UNAVAILABLE))
        assertEquals(2, cache.parameters(110).single().primaryReading.signalId)
        cache.update(profile.copy(receivedAtMillis = 111))
        val recovered = cache.parameters(111).single()
        assertEquals(1, recovered.primaryReading.signalId)
        assertEquals(3, recovered.sourceReadings.size)
        assertEquals(CarValue.FloatValue(42.0), recovered.normalizedValue)
    }

    @Test fun newerAospCallbackCannotReplaceEcarxRange() {
        val ecarx = snapshot(VehiclePropertySource.ECARX, 1054720, null, 100).copy(
            propertyId = CarPropertyId.REMAINING_RANGE,
            value = CarValue.FloatValue(690.0), displayValue = "690 км",
            rawValue = RawVehicleValue("690", 690.0),
        )
        val aosp = snapshot(VehiclePropertySource.VHAL, 0x11600308, "AOSP", 101).copy(
            propertyId = CarPropertyId.REMAINING_RANGE,
            value = CarValue.FloatValue(50.0), displayValue = "50 км",
            rawValue = RawVehicleValue("50000", 50000.0),
        )
        listOf(listOf(ecarx, aosp), listOf(aosp, ecarx)).forEach { readings ->
            val cache = UnifiedParameterCache()
            readings.forEach(cache::update)
            cache.update(aosp.copy(receivedAtMillis = 110))
            val range = cache.parameters(110).single()
            assertEquals(ecarx.sourceSignalId, range.primaryReading.signalId)
            assertEquals("690 км", range.value.display)
            assertEquals("690", range.value.raw)
            assertEquals(CarValue.FloatValue(690.0), range.normalizedValue)
            assertEquals(2, range.sourceReadings.size)

            cache.update(ecarx.copy(status = VehiclePropertyStatus.ERROR))
            assertEquals("50 км", cache.parameters(110).single().value.display)
            cache.update(ecarx.copy(receivedAtMillis = 111))
            assertEquals("690 км", cache.parameters(111).single().value.display)
        }
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
