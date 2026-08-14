package com.cityray.diagnosticsprobe

enum class ProbeStatus {
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

data class ProbeUiState(
    val carStatus: ProbeStatus = ProbeStatus.NOT_CHECKED,
    val carDetail: String = "",
    val diagnosticsStatus: ProbeStatus = ProbeStatus.NOT_CHECKED,
    val diagnosticsDetail: String = "",
    val dtcManagerStatus: ProbeStatus = ProbeStatus.NOT_CHECKED,
    val dtcManagerDetail: String = "",
    val dtcs: List<DtcRecord> = emptyList(),
    val logLines: List<String> = emptyList(),
)

interface ProbeSink {
    fun onCarStatus(status: ProbeStatus, detail: String = "")
    fun onDiagnosticsStatus(status: ProbeStatus, detail: String = "")
    fun onDtcManagerStatus(status: ProbeStatus, detail: String = "")
    fun onDtcsChanged(dtcs: List<DtcRecord>)
    fun onLog(message: String, error: Throwable? = null)
}
