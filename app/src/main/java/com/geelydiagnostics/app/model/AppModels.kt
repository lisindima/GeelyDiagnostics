package com.geelydiagnostics.app.model

import androidx.compose.runtime.Immutable
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehicleDiscoveryProgress
import com.geelydiagnostics.app.vehicle.property.VehicleParameterSample
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend
import com.geelydiagnostics.app.ui.display.DisplaySafeAreaMode

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

data class DtcRecord(
    val code: String,
    val id: String,
    val ecuType: Int,
    val status: Int,
    val tickTime: Long,
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
    val vhalDiscovery: VehicleDiscoveryProgress = VehicleDiscoveryProgress(),
    val selectedVhalProfile: VehicleProfile = VehicleProfile.RAW,
    val selectedVhalBackend: VhalGatewayBackend = VhalGatewayBackend.CAR_PROPERTY_MANAGER,
    val displaySafeAreaMode: DisplaySafeAreaMode = DisplaySafeAreaMode.AUTO,
    val displaySafeAreaManualBottomPx: Int = 0,
    val showDisplaySafeAreaOverlay: Boolean = false,
    val carInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carInfoDetail: String = "",
    val functionStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val functionDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val ecarxDiagnosticDetails: EcarxDiagnosticDetails = EcarxDiagnosticDetails(),
    val obd2: Obd2Snapshot = Obd2Snapshot(),
    val parameters: List<VehicleParameter> = emptyList(),
    val parameterHistory: Map<String, List<VehicleParameterSample>> = emptyMap(),
    val vehicleInfo: List<VehicleParameter> = emptyList(),
    val functions: List<VehicleParameter> = emptyList(),
    val logLines: List<String> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val scanStartedAtMillis: Long? = null,
)
