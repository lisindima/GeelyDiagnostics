package com.geelydiagnostics.app

import androidx.compose.runtime.Immutable

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

enum class VehicleDataSource(val label: String) {
    ECARX("ECARX"),
    VHAL("VHAL"),
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
    val source: VehicleDataSource = VehicleDataSource.ECARX,
    val sourceProfile: String? = null,
    val profilePropertyId: Int? = null,
    val areaId: Int = 0,
    val updatedAtMillis: Long? = null,
    val expectedUpdateIntervalMillis: Long? = null,
    val changedSinceScan: Boolean = false,
    val autoUpdates: Boolean = false,
)

data class VehicleInfoRecord(
    val id: Int,
    val apiName: String,
    val title: String,
    val value: ApiValue,
    val support: ApiSupportStatus,
    val error: String = "",
    val source: VehicleDataSource = VehicleDataSource.ECARX,
    val updatedAtMillis: Long? = null,
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
    val source: VehicleDataSource = VehicleDataSource.ECARX,
    val updatedAtMillis: Long? = null,
)

@Immutable
data class AppUiState(
    val carStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carDetail: String = "",
    val diagnosticsStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val diagnosticsDetail: String = "",
    val dtcManagerStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val dtcManagerDetail: String = "",
    val sensorStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val sensorDetail: String = "",
    val vhalStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val vhalDetail: String = "",
    val selectedVhalProfile: VhalProfile = VhalProfile.RAW,
    val carInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carInfoDetail: String = "",
    val functionStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val functionDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val sensors: List<SensorRecord> = emptyList(),
    val vehicleInfo: List<VehicleInfoRecord> = emptyList(),
    val functions: List<VehicleFunctionRecord> = emptyList(),
    val logLines: List<String> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val scanStartedAtMillis: Long? = null,
)

internal val SensorRecord.favoriteKey: String
    get() = "sensor:${source.name}:$id:$areaId"

internal val VehicleInfoRecord.favoriteKey: String
    get() = "vehicle:${source.name}:$id"

internal val VehicleFunctionRecord.favoriteKey: String
    get() = "function:${source.name}:$id"

interface ReadOnlySink {
    fun onCarStatus(status: ReadStatus, detail: String = "")
    fun onDiagnosticsStatus(status: ReadStatus, detail: String = "")
    fun onDtcManagerStatus(status: ReadStatus, detail: String = "")
    fun onSensorStatus(status: ReadStatus, detail: String = "")
    fun onVhalStatus(status: ReadStatus, detail: String = "")
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
