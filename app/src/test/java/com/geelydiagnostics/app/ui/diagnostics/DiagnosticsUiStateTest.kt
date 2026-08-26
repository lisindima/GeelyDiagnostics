package com.geelydiagnostics.app.ui.diagnostics

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsUiStateTest {
    @Test fun obd2UpdateInvalidatesScreenWithoutDtcChanges() {
        val original = AppUiState()
        val next = original.copy(obd2 = Obd2Snapshot(
            live = Obd2Frame(123L, floats = mapOf(8 to 800.0)),
            autoUpdates = true,
        ))
        assertEquals(original.dtcs, next.dtcs)
        assertNotEquals(original.diagnosticsUiState(), next.diagnosticsUiState())
        assertEquals(next.obd2, next.diagnosticsUiState().obd2)
    }

    @Test fun partInfoAndApiUpdatesInvalidateScreenWithoutDtcChanges() {
        val original = AppUiState()
        val next = original.copy(ecarxDiagnosticDetails = EcarxDiagnosticDetails(
            partInfoStatus = ReadStatus.AVAILABLE,
            parts = listOf(PartInfoValue(1, "assembly", "Сборка", "TEST-001")),
            apis = listOf(DiagnosticApiInfo("getPartInfoManager", true)),
        ))
        assertEquals(original.dtcs, next.dtcs)
        assertNotEquals(original.diagnosticsUiState(), next.diagnosticsUiState())
        assertEquals(next.ecarxDiagnosticDetails, next.diagnosticsUiState().ecarxDiagnosticDetails)
    }

    @Test fun vhalFailureAndBackendSelectionReachDiagnosticScreen() {
        val original = AppUiState(selectedVhalBackend = VhalGatewayBackend.CAR_PROPERTY_MANAGER)
        val unavailable = original.copy(vhalStatus = ReadStatus.ERROR, vhalDetail = "Нет доступа")
        assertNotEquals(original.diagnosticsUiState(), unavailable.diagnosticsUiState())
        assertEquals("Нет доступа", unavailable.diagnosticsUiState().vhalDetail)
        val hidl = original.copy(selectedVhalBackend = VhalGatewayBackend.HIDL)
        assertNotEquals(original.diagnosticsUiState(), hidl.diagnosticsUiState())
    }

    @Test fun catalogStatusAndLogUpdatesDoNotInvalidateDiagnosticScreen() {
        val original = AppUiState()
        val next = original.copy(
            logLines = listOf("VHAL event: speed updated"),
            ecarxParameterStatus = ReadStatus.AVAILABLE,
            ecarxParameterDetail = "49 значений",
            functionStatus = ReadStatus.AVAILABLE,
        )
        assertEquals(original.diagnosticsUiState(), next.diagnosticsUiState())
    }
}
