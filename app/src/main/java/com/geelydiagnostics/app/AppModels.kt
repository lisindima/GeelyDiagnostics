package com.geelydiagnostics.app

import androidx.compose.runtime.Immutable
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile

enum class ReadStatus {
    NOT_CHECKED,
    CHECKING,
    PARTIAL,
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

@Immutable
data class SensorSample(
    val timestampMillis: Long,
    val value: Double,
)

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
    val propertyId: Int? = null,
    val areaId: Int = 0,
    val updatedAtMillis: Long? = null,
    val sourceTimestampNanos: Long? = null,
    val expectedUpdateIntervalMillis: Long? = null,
    val changedSinceScan: Boolean = false,
    val autoUpdates: Boolean = false,
    val chartable: Boolean = false,
    val decoded: Boolean? = null,
    val sourceReadings: List<ParameterSourceReading> = emptyList(),
)

data class ParameterSourceReading(
    val source: VehicleDataSource,
    val signalId: Int,
    val signalName: String,
    val value: ApiValue,
    val support: ApiSupportStatus,
    val error: String = "",
    val profile: String? = null,
    val areaId: Int = 0,
    val updatedAtMillis: Long? = null,
    val sourceTimestampNanos: Long? = null,
    val autoUpdates: Boolean = false,
    val decoded: Boolean = false,
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
    val selectedVhalProfile: VehicleProfile = VehicleProfile.RAW,
    val carInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carInfoDetail: String = "",
    val functionStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val functionDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val sensors: List<SensorRecord> = emptyList(),
    val sensorHistory: Map<String, List<SensorSample>> = emptyMap(),
    val vehicleInfo: List<VehicleInfoRecord> = emptyList(),
    val functions: List<VehicleFunctionRecord> = emptyList(),
    val logLines: List<String> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val scanStartedAtMillis: Long? = null,
)

internal val SensorRecord.favoriteKey: String
    get() = propertyId?.let { "property:$it:$areaId" }
        ?: "signal:${source.name}:$id:$areaId"

internal val ParameterSourceReading.legacyFavoriteKey: String
    get() = "sensor:${source.name}:$signalId:$areaId"

internal val VehicleInfoRecord.favoriteKey: String
    get() = "vehicle:${source.name}:$id"

internal val VehicleFunctionRecord.favoriteKey: String
    get() = "function:${source.name}:$id"
