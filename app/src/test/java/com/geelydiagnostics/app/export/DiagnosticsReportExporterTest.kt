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
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend
import com.geelydiagnostics.app.ui.display.DisplayMetricsSnapshot
import com.geelydiagnostics.app.ui.display.DisplaySafeArea
import com.geelydiagnostics.app.ui.display.DisplaySafeAreaMode
import com.geelydiagnostics.app.ui.display.DisplaySafeAreaState
import com.geelydiagnostics.app.ui.display.EdgeInsetsPx
import com.geelydiagnostics.app.ui.display.SafeAreaSource
import com.geelydiagnostics.app.ui.display.WindowInsetsSnapshot

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
            selectedVhalBackend = VhalGatewayBackend.HIDL,
            parameters = listOf(parameter),
            favoriteKeys = setOf(parameter.favoriteKey),
            logLines = listOf("12:00:00.000  test"),
            scanStartedAtMillis = 1_700_000_000_000L,
        )

        val displayState = DisplaySafeAreaState(
            safeArea = DisplaySafeArea(bottomPx = 120),
            bottomSource = SafeAreaSource.TAPPABLE_ELEMENT,
            mode = DisplaySafeAreaMode.AUTO,
            systemBottomPx = 120,
            oemProfile = "G426",
            insets = WindowInsetsSnapshot(tappableElement = EdgeInsetsPx(bottom = 120)),
            display = DisplayMetricsSnapshot(
                displayId = 0,
                physicalWidthPx = 1440,
                physicalHeightPx = 1920,
                windowWidthPx = 1440,
                windowHeightPx = 1920,
                density = 2f,
                densityDpi = 320,
            ),
        )
        val report = JSONObject(DiagnosticsReportExporter.create(
            state = state,
            generatedAtMillis = 1_700_000_001_000L,
            appVersion = "0.11.0",
            displaySafeAreaState = displayState,
        ))
        val exported = report.getJSONArray("parameters").getJSONObject(0)

        assertTrue(report.getBoolean("readOnly"))
        assertEquals(9, report.getInt("schemaVersion"))
        assertTrue(report.has("display"))
        assertTrue(report.getJSONObject("display").has("insets"))
        assertEquals(
            "TAPPABLE_ELEMENT",
            report.getJSONObject("display")
                .getJSONObject("calculatedSafeArea")
                .getString("bottomSource"),
        )
        assertEquals(
            60.0,
            report.getJSONObject("display")
                .getJSONObject("insets")
                .getJSONObject("tappableElement")
                .getDouble("bottomDp"),
            0.0,
        )
        assertEquals("0.11.0", report.getString("appVersion"))
        assertEquals("G426", report.getString("vhalProfile"))
        assertEquals("HIDL", report.getString("vhalBackend"))
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
