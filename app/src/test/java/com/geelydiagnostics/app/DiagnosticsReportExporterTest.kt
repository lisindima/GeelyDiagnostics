package com.geelydiagnostics.app

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
            updatedAtMillis = 1_700_000_000_000L,
            changedSinceScan = true,
        )
        val state = AppUiState(
            selectedVhalProfile = VhalProfile.G426,
            sensors = listOf(sensor),
            favoriteKeys = setOf(sensor.favoriteKey),
            logLines = listOf("12:00:00.000  test"),
            scanStartedAtMillis = 1_700_000_000_000L,
        )

        val report = JSONObject(
            DiagnosticsReportExporter.create(state, 1_700_000_001_000L, "0.11.0"),
        )
        val exported = report.getJSONArray("sensors").getJSONObject(0)

        assertTrue(report.getBoolean("readOnly"))
        assertEquals("0.11.0", report.getString("appVersion"))
        assertEquals("G426", report.getString("vhalProfile"))
        assertEquals("1", exported.getString("raw"))
        assertEquals("G426", exported.getString("mappingProfile"))
        assertTrue(exported.getBoolean("changedSinceScan"))
        assertTrue(exported.getBoolean("favorite"))
        assertEquals(1, report.getJSONArray("log").length())
    }
}
