package com.geelydiagnostics.app

enum class ReadStatus {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    ERROR,
}

enum class ApiSupportStatus {
    ACTIVE,
    NOT_ACTIVE,
    NOT_AVAILABLE,
    ERROR,
    UNKNOWN,
}

data class DtcRecord(
    val code: String,
    val id: String,
    val ecuType: Int,
    val status: Int,
    val tickTime: Long,
)

data class ApiValue(
    val display: String,
    val raw: String,
) {
    companion object {
        fun raw(value: String): ApiValue = ApiValue(display = value, raw = value)
        val unavailable = raw("—")
    }
}

data class SensorRecord(
    val id: Int,
    val apiName: String,
    val title: String,
    val value: ApiValue,
    val valueKind: String,
    val support: ApiSupportStatus,
    val error: String = "",
)

data class VehicleInfoRecord(
    val id: Int,
    val apiName: String,
    val title: String,
    val value: ApiValue,
    val support: ApiSupportStatus,
    val error: String = "",
)

data class VehicleFunctionRecord(
    val id: Int,
    val apiName: String,
    val title: String,
    val value: ApiValue = ApiValue.unavailable,
    val supportedValues: String = "",
    val zones: String = "",
    val support: ApiSupportStatus,
    val error: String = "",
)

data class AppUiState(
    val carStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carDetail: String = "",
    val diagnosticsStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val diagnosticsDetail: String = "",
    val dtcManagerStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val dtcManagerDetail: String = "",
    val sensorStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val sensorDetail: String = "",
    val carInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carInfoDetail: String = "",
    val functionStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val functionDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val sensors: List<SensorRecord> = emptyList(),
    val vehicleInfo: List<VehicleInfoRecord> = emptyList(),
    val functions: List<VehicleFunctionRecord> = emptyList(),
    val logLines: List<String> = emptyList(),
)

interface ReadOnlySink {
    fun onCarStatus(status: ReadStatus, detail: String = "")
    fun onDiagnosticsStatus(status: ReadStatus, detail: String = "")
    fun onDtcManagerStatus(status: ReadStatus, detail: String = "")
    fun onSensorStatus(status: ReadStatus, detail: String = "")
    fun onCarInfoStatus(status: ReadStatus, detail: String = "")
    fun onFunctionStatus(status: ReadStatus, detail: String = "")
    fun onDtcsChanged(dtcs: List<DtcRecord>)
    fun onSensorsChanged(sensors: List<SensorRecord>)
    fun onSensorValueChanged(id: Int, value: ApiValue)
    fun onSensorSupportChanged(id: Int, support: ApiSupportStatus)
    fun onVehicleInfoChanged(items: List<VehicleInfoRecord>)
    fun onFunctionsChanged(functions: List<VehicleFunctionRecord>)
    fun onLog(message: String, error: Throwable? = null)
}
