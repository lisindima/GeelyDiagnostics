package com.geelydiagnostics.app.ui.diagnostics

import com.geelydiagnostics.app.model.AppUiState
import com.geelydiagnostics.app.model.DtcRecord
import com.geelydiagnostics.app.model.EcarxDiagnosticDetails
import com.geelydiagnostics.app.model.Obd2Snapshot
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend

/** Only diagnostic inputs: unrelated catalog events and log lines do not invalidate this screen. */
internal data class DiagnosticsUiState(
    val carStatus: ReadStatus,
    val carDetail: String,
    val diagnosticsStatus: ReadStatus,
    val diagnosticsDetail: String,
    val dtcManagerStatus: ReadStatus,
    val dtcManagerDetail: String,
    val dtcs: List<DtcRecord>,
    val ecarxDiagnosticDetails: EcarxDiagnosticDetails,
    val obd2: Obd2Snapshot,
    val vhalStatus: ReadStatus,
    val vhalDetail: String,
    val selectedVhalBackend: VhalGatewayBackend,
)

internal fun AppUiState.diagnosticsUiState() = DiagnosticsUiState(
    carStatus, carDetail, diagnosticsStatus, diagnosticsDetail,
    dtcManagerStatus, dtcManagerDetail, dtcs, ecarxDiagnosticDetails,
    obd2, vhalStatus, vhalDetail, selectedVhalBackend,
)
