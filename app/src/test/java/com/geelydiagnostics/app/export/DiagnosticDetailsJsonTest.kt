package com.geelydiagnostics.app.export

import com.geelydiagnostics.app.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagnosticDetailsJsonTest {
    @Test fun exportRetainsRawDtcTimePartKeysAndExactObdTimestamps() {
        val timestamp = 9007199254740993L
        val state = AppUiState(
            dtcs = listOf(DtcRecord("U1234", "id", 5, 123, 1786695380305)),
            ecarxDiagnosticDetails = EcarxDiagnosticDetails(parts = listOf(PartInfoValue(1, "KEY", "Название", "0000123")),
                apis = listOf(DiagnosticApiInfo("diagSubscribe", null))),
            obd2 = Obd2Snapshot(backend = "HIDL", freezeTimestamps = listOf(timestamp), freezeFrames = listOf(
                Obd2Frame(timestamp, "P0123", floats = mapOf(8 to 748.5),
                    raw = Obd2RawPayload(int64Values = listOf(timestamp), floatValues = listOf(Double.NaN))))),
        )
        val result = JSONObject(DiagnosticsReportExporter.create(state, 1, "test"))
        assertEquals("AUD", result.getJSONArray("dtcs").getJSONObject(0).getString("ecuName"))
        assertEquals(1786695380305, result.getJSONArray("dtcs").getJSONObject(0).getLong("tickTimeRaw"))
        assertEquals("0000123", result.getJSONObject("ecarxDiagnostics").getJSONArray("parts").getJSONObject(0).getString("raw"))
        assertTrue(result.getJSONObject("ecarxDiagnostics").getJSONArray("apis").getJSONObject(0).isNull("present"))
        assertEquals(timestamp, result.getJSONObject("obd2").getJSONArray("freezeTimestampsNanos").getLong(0))
        val frame = result.getJSONObject("obd2").getJSONArray("freezeFrames").getJSONObject(0)
        assertEquals(timestamp, frame.getJSONObject("rawHidlPayload").getJSONArray("int64Values").getLong(0))
        assertEquals("NaN", frame.getJSONObject("rawHidlPayload").getJSONArray("floatValues").getString(0))
    }
}
