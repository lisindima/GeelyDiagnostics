package com.geelydiagnostics.app.model

import androidx.compose.runtime.Immutable
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehicleParameterSample
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource

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

val ApiSupportStatus.isVisibleAsSupported: Boolean
    get() = this == ApiSupportStatus.ACTIVE || this == ApiSupportStatus.NOT_ACTIVE

data class DtcRecord(
    val code: String,
    val id: String,
    val ecuType: Int,
    val status: Int,
    val tickTime: Long,
)

data class VehicleInfoRecord(
    val id: Int,
    val apiName: String,
    val title: String,
    val value: VehicleDisplayValue,
    val support: ApiSupportStatus,
    val error: String = "",
    val source: VehiclePropertySource = VehiclePropertySource.ECARX,
    val updatedAtMillis: Long? = null,
)

data class VehicleFunctionRecord(
    val id: Int,
    val apiName: String,
    val title: String,
    val value: VehicleDisplayValue = VehicleDisplayValue.unavailable,
    val supportedValues: String = "",
    val zones: String = "",
    val support: ApiSupportStatus,
    val error: String = "",
    val source: VehiclePropertySource = VehiclePropertySource.ECARX,
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
    val ecarxParameterStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val ecarxParameterDetail: String = "",
    val vhalStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val vhalDetail: String = "",
    val selectedVhalProfile: VehicleProfile = VehicleProfile.RAW,
    val carInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carInfoDetail: String = "",
    val functionStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val functionDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val parameters: List<VehicleParameter> = emptyList(),
    val parameterHistory: Map<String, List<VehicleParameterSample>> = emptyMap(),
    val vehicleInfo: List<VehicleInfoRecord> = emptyList(),
    val functions: List<VehicleFunctionRecord> = emptyList(),
    val logLines: List<String> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val scanStartedAtMillis: Long? = null,
)

internal val VehicleInfoRecord.favoriteKey: String
    get() = "vehicle:${source.name}:$id"

internal val VehicleFunctionRecord.favoriteKey: String
    get() = "function:${source.name}:$id"
