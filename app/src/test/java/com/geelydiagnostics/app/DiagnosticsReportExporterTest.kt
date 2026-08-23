package com.geelydiagnostics.app

import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportExporterTest {

    @Test
    fun exportContainsRawValuesSourceProfileAndReadOnlyMarker() {
        val sensor = SensorRecord(
            id = 123,
            apiName = "TEST_SENSOR",
            title = "Тестовый сенсор",
            value = ApiValue(display = "Включено", raw = "1"),
            valueKind = "int",
            support = ApiSupportStatus.ACTIVE,
            source = VehicleDataSource.VHAL,
            sourceProfile = "G426",
            propertyId = 10012,
            updatedAtMillis = 1_700_000_000_000L,
            sourceTimestampNanos = 42L,
            changedSinceScan = true,
            autoUpdates = true,
            decoded = true,
            sourceReadings = listOf(
                ParameterSourceReading(
                    source = VehicleDataSource.VHAL,
                    signalId = 123,
                    signalName = "TEST_SENSOR",
                    value = ApiValue(display = "Включено", raw = "1"),
                    support = ApiSupportStatus.ACTIVE,
                    profile = "G426",
                    sourceTimestampNanos = 42L,
                    autoUpdates = true,
                    decoded = true,
                ),
            ),
        )
        val state = AppUiState(
            selectedVhalProfile = VehicleProfile.G426,
            sensors = listOf(sensor),
            favoriteKeys = setOf(sensor.favoriteKey),
            logLines = listOf("12:00:00.000  test"),
            scanStartedAtMillis = 1_700_000_000_000L,
        )

        val report = JSONObject(
            DiagnosticsReportExporter.create(state, 1_700_000_001_000L, "0.11.0"),
        )
        val exported = report.getJSONArray("parameters").getJSONObject(0)

        assertTrue(report.getBoolean("readOnly"))
        assertEquals(3, report.getInt("schemaVersion"))
        assertEquals("0.11.0", report.getString("appVersion"))
        assertEquals("G426", report.getString("vhalProfile"))
        assertEquals("1", exported.getString("raw"))
        assertEquals("G426", exported.getString("mappingProfile"))
        assertEquals(10012, exported.getInt("normalizedPropertyId"))
        assertEquals(42L, exported.getLong("sourceTimestampNanos"))
        assertTrue(exported.getBoolean("changedSinceScan"))
        assertTrue(exported.getBoolean("autoUpdates"))
        assertTrue(exported.getBoolean("favorite"))
        assertEquals(1, exported.getJSONArray("sources").length())
        assertEquals(1, report.getJSONArray("log").length())
    }
}
