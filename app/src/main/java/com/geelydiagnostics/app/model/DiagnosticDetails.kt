package com.geelydiagnostics.app.model

data class DiagnosticApiInfo(
    val name: String,
    val present: Boolean?,
    val signatures: List<String> = emptyList(),
    val detail: String = "",
)

data class PartInfoValue(
    val id: Int,
    val key: String,
    val title: String,
    val value: String? = null,
    val error: String = "",
)

data class EcarxDiagnosticDetails(
    val partInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val partInfoDetail: String = "",
    val parts: List<PartInfoValue> = emptyList(),
    val apis: List<DiagnosticApiInfo> = emptyList(),
)

/** Verified IECU numbers, not MONITOR_TYPE_* IDs. Long acronym expansions are not assumed. */
object EcarxEcuNames {
    // RUXCLauncher/decompiled/jadx/sources/com/ecarx/xui/adaptapi/car/diagnostics/IECU.java
    private val names = mapOf(0 to "UNKNOWN", 1 to "IHU", 2 to "CSD", 3 to "WPC",
        4 to "CCSM", 5 to "AUD", 6 to "TEM2", 7 to "VCM", 8 to "PAC")
    fun name(type: Int): String = names[type] ?: "Блок $type"
}

data class Obd2Capability(
    val propertyId: Int,
    val supported: Boolean? = null,
    val status: ReadStatus = ReadStatus.NOT_CHECKED,
    val detail: String = "",
)

/** HIDL containers are kept intact, including exact int64 timestamps (never converted to Double). */
data class Obd2RawPayload(
    val int32Values: List<Int> = emptyList(),
    val floatValues: List<Double> = emptyList(),
    val int64Values: List<Long> = emptyList(),
    val bytes: List<Int> = emptyList(),
    val stringValue: String = "",
)

data class Obd2Frame(
    val timestampNanos: Long?,
    val dtc: String = "",
    val integers: Map<Int, Int> = emptyMap(),
    val floats: Map<Int, Double> = emptyMap(),
    val raw: Obd2RawPayload? = null,
    val error: String = "",
    val requestedTimestampNanos: Long? = null,
)

data class Obd2Snapshot(
    val backend: String = "",
    val capabilities: List<Obd2Capability> = emptyList(),
    val live: Obd2Frame? = null,
    val freezeTimestamps: List<Long> = emptyList(),
    val freezeFrames: List<Obd2Frame> = emptyList(),
    val autoUpdates: Boolean = false,
    val detail: String = "",
)
