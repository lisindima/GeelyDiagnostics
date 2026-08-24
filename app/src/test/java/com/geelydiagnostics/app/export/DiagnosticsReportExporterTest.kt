package com.geelydiagnostics.app.export

import com.geelydiagnostics.app.model.*

import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleSourceReading
import com.geelydiagnostics.app.vehicle.property.favoriteKey

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportExporterTest {

    @Test
    fun exportContainsRawValuesSourceProfileAndReadOnlyMarker() {
        val parameter = VehicleParameter(
            propertyId = CarPropertyId(10012),
            areaId = 0,
            title = "Тестовый параметр",
            value = VehicleDisplayValue(display = "Включено", raw = "1"),
            valueKind = "int",
            status = VehiclePropertyStatus.AVAILABLE,
            updatedAtMillis = 1_700_000_000_000L,
            sourceTimestampNanos = 42L,
            changedSinceScan = true,
            autoUpdates = true,
            decoded = true,
            sourceReadings = listOf(
                VehicleSourceReading(
                    source = VehiclePropertySource.VHAL,
                    signalId = 123,
                    signalName = "TEST_SENSOR",
                    value = VehicleDisplayValue(display = "Включено", raw = "1"),
                    status = VehiclePropertyStatus.AVAILABLE,
                    profile = "G426",
                    sourceTimestampNanos = 42L,
                    autoUpdates = true,
                    decoded = true,
                ),
            ),
        )
        val state = AppUiState(
            selectedVhalProfile = VehicleProfile.G426,
            parameters = listOf(parameter),
            favoriteKeys = setOf(parameter.favoriteKey),
            logLines = listOf("12:00:00.000  test"),
            scanStartedAtMillis = 1_700_000_000_000L,
        )

        val report = JSONObject(
            DiagnosticsReportExporter.create(state, 1_700_000_001_000L, "0.11.0"),
        )
        val exported = report.getJSONArray("parameters").getJSONObject(0)

        assertTrue(report.getBoolean("readOnly"))
        assertEquals(4, report.getInt("schemaVersion"))
        assertEquals("0.11.0", report.getString("appVersion"))
        assertEquals("G426", report.getString("vhalProfile"))
        assertEquals("1", exported.getString("raw"))
        assertEquals(10012, exported.getInt("normalizedPropertyId"))
        assertEquals("VHAL", exported.getString("primarySource"))
        assertEquals(42L, exported.getLong("sourceTimestampNanos"))
        assertTrue(exported.getBoolean("changedSinceScan"))
        assertTrue(exported.getBoolean("autoUpdates"))
        assertTrue(exported.getBoolean("favorite"))
        assertEquals(1, exported.getJSONArray("sources").length())
        assertEquals(1, report.getJSONArray("log").length())
    }
}
