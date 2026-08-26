package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.model.*

import com.geelydiagnostics.app.model.DtcRecord
import com.geelydiagnostics.app.model.ReadStatus

internal data class DiagnosticsState(
    val diagnosticsStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val diagnosticsDetail: String = "",
    val dtcManagerStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val dtcManagerDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val ecarxDetails: EcarxDiagnosticDetails = EcarxDiagnosticDetails(),
    val obd2: Obd2Snapshot = Obd2Snapshot(),
)

/** DTC state is intentionally outside the normalized vehicle-property cache. */
internal class DiagnosticsRepository {
    private var state = DiagnosticsState()

    fun snapshot(): DiagnosticsState = state

    fun updateDetails(details: EcarxDiagnosticDetails) { state = state.copy(ecarxDetails = details) }
    fun updateObd2(snapshot: Obd2Snapshot) { state = state.copy(obd2 = snapshot) }

    fun reset() {
        state = DiagnosticsState(
            diagnosticsStatus = ReadStatus.CHECKING,
            dtcManagerStatus = ReadStatus.CHECKING,
        )
    }

    fun updateDiagnostics(status: ReadStatus, detail: String) {
        state = state.copy(diagnosticsStatus = status, diagnosticsDetail = detail)
    }

    fun updateManager(status: ReadStatus, detail: String) {
        state = state.copy(dtcManagerStatus = status, dtcManagerDetail = detail)
    }

    fun updateDtcs(dtcs: List<DtcRecord>) {
        state = state.copy(dtcs = dtcs)
    }
}
