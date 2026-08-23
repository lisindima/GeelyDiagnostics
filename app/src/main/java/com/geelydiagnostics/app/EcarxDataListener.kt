package com.geelydiagnostics.app

import com.geelydiagnostics.app.vehicle.source.VehicleParameterSink

/** Events exposed by the supplemental ECARX reader to the repository. */
internal interface EcarxDataListener : VehicleParameterSink {
    fun onCarStatus(status: ReadStatus, detail: String = "")
    fun onDiagnosticsStatus(status: ReadStatus, detail: String = "")
    fun onDtcManagerStatus(status: ReadStatus, detail: String = "")
    fun onCarInfoStatus(status: ReadStatus, detail: String = "")
    fun onFunctionStatus(status: ReadStatus, detail: String = "")
    fun onDtcsChanged(dtcs: List<DtcRecord>)
    fun onVehicleInfoChanged(items: List<VehicleInfoRecord>)
    fun onFunctionsChanged(functions: List<VehicleFunctionRecord>)
    fun onLog(message: String, error: Throwable? = null)
}
