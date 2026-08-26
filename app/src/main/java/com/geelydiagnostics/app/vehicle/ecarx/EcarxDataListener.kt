package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.model.DtcRecord
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.source.VehicleParameterSink

/** Events exposed by the supplemental ECARX reader to the repository. */
internal interface EcarxDataListener : VehicleParameterSink {
    fun onDiagnosticDetails(details: com.geelydiagnostics.app.model.EcarxDiagnosticDetails) = Unit
    fun onCarStatus(status: ReadStatus, detail: String = "")
    fun onDiagnosticsStatus(status: ReadStatus, detail: String = "")
    fun onDtcManagerStatus(status: ReadStatus, detail: String = "")
    fun onCarInfoStatus(status: ReadStatus, detail: String = "")
    fun onFunctionStatus(status: ReadStatus, detail: String = "")
    fun onDtcsChanged(dtcs: List<DtcRecord>)
    fun onLog(message: String, error: Throwable? = null)
}
