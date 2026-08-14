package com.cityray.diagnostics

enum class DiagnosticsStatus {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    ERROR,
}

data class DtcRecord(
    val code: String,
    val id: String,
    val ecuType: Int,
    val status: Int,
    val tickTime: Long,
)

data class DiagnosticsUiState(
    val carStatus: DiagnosticsStatus = DiagnosticsStatus.NOT_CHECKED,
    val carDetail: String = "",
    val diagnosticsStatus: DiagnosticsStatus = DiagnosticsStatus.NOT_CHECKED,
    val diagnosticsDetail: String = "",
    val dtcManagerStatus: DiagnosticsStatus = DiagnosticsStatus.NOT_CHECKED,
    val dtcManagerDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val logLines: List<String> = emptyList(),
)

interface DiagnosticsSink {
    fun onCarStatus(status: DiagnosticsStatus, detail: String = "")
    fun onDiagnosticsStatus(status: DiagnosticsStatus, detail: String = "")
    fun onDtcManagerStatus(status: DiagnosticsStatus, detail: String = "")
    fun onDtcsChanged(dtcs: List<DtcRecord>)
    fun onLog(message: String, error: Throwable? = null)
}
