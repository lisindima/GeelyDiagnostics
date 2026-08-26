package com.geelydiagnostics.app.ui.catalog

import com.geelydiagnostics.app.model.*

import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleSourceReading
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueFreshnessTest {

    @Test
    fun continuousValueBecomesStaleAfterItsLimit() {
        val record = parameter(updatedAt = 10_000L, staleAfter = 15_000L)
        assertFalse(record.isStale(25_000L))
        assertTrue(record.isStale(25_001L))
    }

    @Test
    fun onChangeValueNeverLooksStaleOnlyBecauseItDidNotChange() {
        assertFalse(parameter(updatedAt = 10_000L, staleAfter = null).isStale(1_000_000L))
    }

    @Test
    fun manualReadingDoesNotExpectSubscriptionEvents() {
        assertFalse(parameter(10_000, 15_000).copy(autoUpdates = false).isStale(1_000_000))
    }

    @Test
    fun updateLabelContainsRelativeAge() {
        assertTrue(formatUpdateTime(10_000L, 15_000L).endsWith("5 с назад"))
    }

    private fun parameter(updatedAt: Long, staleAfter: Long?) = VehicleParameter(
        propertyId = null,
        areaId = 0,
        title = "Тест",
        value = VehicleDisplayValue.raw("1"),
        valueKind = "int",
        status = VehiclePropertyStatus.AVAILABLE,
        updatedAtMillis = updatedAt,
        expectedUpdateIntervalMillis = staleAfter,
        autoUpdates = true,
        sourceReadings = listOf(
            VehicleSourceReading(
                source = VehiclePropertySource.VHAL,
                signalId = 1,
                signalName = "TEST",
                value = VehicleDisplayValue.raw("1"),
                status = VehiclePropertyStatus.AVAILABLE,
                updatedAtMillis = updatedAt,
            ),
        ),
    )
}
