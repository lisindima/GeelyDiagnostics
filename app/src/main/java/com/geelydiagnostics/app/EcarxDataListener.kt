package com.geelydiagnostics.app

/** Events exposed by the supplemental ECARX reader to the repository. */
internal interface EcarxDataListener {
    fun onCarStatus(status: ReadStatus, detail: String = "")
    fun onDiagnosticsStatus(status: ReadStatus, detail: String = "")
    fun onDtcManagerStatus(status: ReadStatus, detail: String = "")
    fun onSensorStatus(status: ReadStatus, detail: String = "")
    fun onCarInfoStatus(status: ReadStatus, detail: String = "")
    fun onFunctionStatus(status: ReadStatus, detail: String = "")
    fun onDtcsChanged(dtcs: List<DtcRecord>)
    fun onSensorsChanged(source: VehicleDataSource, sensors: List<SensorRecord>)
    fun onSensorValueChanged(source: VehicleDataSource, id: Int, value: ApiValue, areaId: Int = 0)
    fun onSensorSupportChanged(source: VehicleDataSource, id: Int, support: ApiSupportStatus)
    fun onVehicleInfoChanged(items: List<VehicleInfoRecord>)
    fun onFunctionsChanged(functions: List<VehicleFunctionRecord>)
    fun onLog(message: String, error: Throwable? = null)
}
