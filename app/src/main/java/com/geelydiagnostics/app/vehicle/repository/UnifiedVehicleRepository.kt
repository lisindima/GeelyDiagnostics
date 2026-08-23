package com.geelydiagnostics.app.vehicle.repository

import android.content.Context
import android.util.Log
import com.geelydiagnostics.app.ApiSupportStatus
import com.geelydiagnostics.app.ApiValue
import com.geelydiagnostics.app.DtcRecord
import com.geelydiagnostics.app.EcarxDataListener
import com.geelydiagnostics.app.EcarxDataSource
import com.geelydiagnostics.app.ReadStatus
import com.geelydiagnostics.app.SensorRecord
import com.geelydiagnostics.app.VehicleDataSource
import com.geelydiagnostics.app.VehicleFunctionRecord
import com.geelydiagnostics.app.VehicleInfoRecord
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.CarPropertyPresentations
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.key
import com.geelydiagnostics.app.vehicle.vhal.SourceReadStatus
import com.geelydiagnostics.app.vehicle.vhal.VhalDataListener
import com.geelydiagnostics.app.vehicle.vhal.VhalDataSource
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class VehicleRepositoryState(
    val carStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carDetail: String = "",
    val sensorStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val sensorDetail: String = "",
    val vhalStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val vhalDetail: String = "",
    val carInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carInfoDetail: String = "",
    val functionStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val functionDetail: String = "",
    val diagnostics: DiagnosticsState = DiagnosticsState(),
    val sensors: List<SensorRecord> = emptyList(),
    val vehicleInfo: List<VehicleInfoRecord> = emptyList(),
    val functions: List<VehicleFunctionRecord> = emptyList(),
    val logLines: List<String> = emptyList(),
    val scanStartedAtMillis: Long? = null,
)

internal class UnifiedVehicleRepository(
    context: Context,
    private val onState: (VehicleRepositoryState) -> Unit,
) : Closeable, EcarxDataListener, VhalDataListener {
    private val appContext = context.applicationContext
    private val diagnostics = DiagnosticsRepository()
    private val sources = mutableListOf<Closeable>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var state = VehicleRepositoryState()
    private var vhalValues = emptyMap<com.geelydiagnostics.app.vehicle.property.CarPropertyKey, CarPropertySnapshot>()
    private val changedVhalKeys = mutableSetOf<com.geelydiagnostics.app.vehicle.property.CarPropertyKey>()

    @Synchronized
    fun start(profile: VehicleProfile) {
        sources.forEach { runCatching { it.close() } }
        sources.clear()
        diagnostics.reset()
        vhalValues = emptyMap()
        changedVhalKeys.clear()
        state = VehicleRepositoryState(
            carStatus = ReadStatus.CHECKING,
            sensorStatus = ReadStatus.CHECKING,
            vhalStatus = ReadStatus.CHECKING,
            carInfoStatus = ReadStatus.CHECKING,
            functionStatus = ReadStatus.CHECKING,
            diagnostics = diagnostics.snapshot(),
            logLines = state.logLines,
            scanStartedAtMillis = System.currentTimeMillis(),
        )
        publish()
        onLog("=== New multi-source scan ===")
        try {
            sources += EcarxDataSource(appContext, this).also { it.start() }
        } catch (error: Throwable) {
            onCarStatus(ReadStatus.ERROR, describe(error))
            onDiagnosticsStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onDtcManagerStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onSensorStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onCarInfoStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onFunctionStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onLog("ECARX initialization failed: ${describe(error)}", error)
        }
        try {
            sources += VhalDataSource(appContext, profile, this).also { it.start() }
        } catch (error: Throwable) {
            onVhalStatus(SourceReadStatus.ERROR, describe(error))
            onLog("VHAL initialization failed: ${describe(error)}", error)
        }
    }

    @Synchronized
    override fun onCarStatus(status: ReadStatus, detail: String) = update {
        copy(carStatus = status, carDetail = detail)
    }

    @Synchronized
    override fun onDiagnosticsStatus(status: ReadStatus, detail: String) {
        diagnostics.updateDiagnostics(status, detail)
        update { copy(diagnostics = diagnostics.snapshot()) }
    }

    @Synchronized
    override fun onDtcManagerStatus(status: ReadStatus, detail: String) {
        diagnostics.updateManager(status, detail)
        update { copy(diagnostics = diagnostics.snapshot()) }
    }

    @Synchronized
    override fun onDtcsChanged(dtcs: List<DtcRecord>) {
        diagnostics.updateDtcs(dtcs)
        update { copy(diagnostics = diagnostics.snapshot()) }
    }

    @Synchronized
    override fun onSensorStatus(status: ReadStatus, detail: String) = update {
        copy(sensorStatus = status, sensorDetail = detail)
    }

    @Synchronized
    override fun onCarInfoStatus(status: ReadStatus, detail: String) = update {
        copy(carInfoStatus = status, carInfoDetail = detail)
    }

    @Synchronized
    override fun onFunctionStatus(status: ReadStatus, detail: String) = update {
        copy(functionStatus = status, functionDetail = detail)
    }

    @Synchronized
    override fun onSensorsChanged(source: VehicleDataSource, sensors: List<SensorRecord>) = update {
        copy(sensors = this.sensors.filterNot { it.source == source } + sensors)
    }

    @Synchronized
    override fun onSensorValueChanged(
        source: VehicleDataSource,
        id: Int,
        value: ApiValue,
        areaId: Int,
    ) = update {
        val now = System.currentTimeMillis()
        copy(sensors = sensors.map { sensor ->
            if (sensor.source == source && sensor.id == id && sensor.areaId == areaId) {
                sensor.copy(
                    value = value,
                    error = "",
                    updatedAtMillis = now,
                    changedSinceScan = sensor.changedSinceScan || sensor.value.raw != value.raw,
                )
            } else {
                sensor
            }
        })
    }

    @Synchronized
    override fun onSensorSupportChanged(
        source: VehicleDataSource,
        id: Int,
        support: ApiSupportStatus,
    ) = update {
        copy(sensors = sensors.map { sensor ->
            if (sensor.source == source && sensor.id == id) sensor.copy(support = support) else sensor
        })
    }

    @Synchronized
    override fun onVehicleInfoChanged(items: List<VehicleInfoRecord>) = update {
        copy(vehicleInfo = items)
    }

    @Synchronized
    override fun onFunctionsChanged(functions: List<VehicleFunctionRecord>) = update {
        copy(functions = functions)
    }

    @Synchronized
    override fun onVhalStatus(status: SourceReadStatus, detail: String) = update {
        copy(vhalStatus = status.toReadStatus(), vhalDetail = detail)
    }

    @Synchronized
    override fun onVhalSnapshot(values: List<CarPropertySnapshot>) {
        vhalValues = values.associateBy { it.key }
        publishVhalSensors()
    }

    @Synchronized
    override fun onVhalValue(value: CarPropertySnapshot) {
        val old = vhalValues[value.key]
        vhalValues = vhalValues + (value.key to value)
        if (old?.rawValue?.text != value.rawValue?.text) changedVhalKeys += value.key
        publishVhalSensors()
    }

    override fun onVehicleLog(message: String, error: Throwable?) = onLog(message, error)

    @Synchronized
    override fun onLog(message: String, error: Throwable?) {
        if (error == null) Log.i(LOG_TAG, message) else Log.e(LOG_TAG, message, error)
        val timestamp = timeFormat.format(Date())
        update {
            copy(logLines = (logLines + "$timestamp  $message").takeLast(MAX_LOG_LINES))
        }
    }

    @Synchronized
    fun clearLog() = update { copy(logLines = emptyList()) }

    private fun publishVhalSensors() = update {
        val projected = vhalValues.values.map { value ->
            value.toSensorRecord(changedSinceScan = value.key in changedVhalKeys)
        }
        copy(sensors = sensors.filterNot { it.source == VehicleDataSource.VHAL } + projected)
    }

    private fun CarPropertySnapshot.toSensorRecord(changedSinceScan: Boolean): SensorRecord {
        val presentation = id?.let(CarPropertyPresentations::get)
        val numeric = value is CarValue.IntValue || value is CarValue.FloatValue
        val chartable = numeric && (autoUpdates || presentation?.unit != null)
        return SensorRecord(
            id = sourceSignalId,
            apiName = sourceSignalName,
            title = presentation?.title ?: "VHAL property ${sourceSignalId.hex()}",
            value = ApiValue(displayValue, rawValue?.text ?: "—"),
            valueKind = valueKind,
            support = if (status == VehiclePropertyStatus.ERROR && rawValue == null) {
                ApiSupportStatus.ERROR
            } else {
                ApiSupportStatus.ACTIVE
            },
            error = error,
            source = VehicleDataSource.VHAL,
            sourceProfile = profileKey,
            profilePropertyId = id?.rawValue,
            areaId = areaId,
            updatedAtMillis = receivedAtMillis,
            sourceTimestampNanos = sourceTimestampNanos,
            expectedUpdateIntervalMillis = expectedUpdateIntervalMillis,
            changedSinceScan = changedSinceScan,
            autoUpdates = autoUpdates,
            chartable = chartable,
            decoded = id != null && status == VehiclePropertyStatus.AVAILABLE,
        )
    }

    private fun update(block: VehicleRepositoryState.() -> VehicleRepositoryState) {
        state = state.block()
        publish()
    }

    private fun publish() = onState(state)

    override fun close() {
        sources.forEach { runCatching { it.close() } }
        sources.clear()
    }

    companion object {
        private const val MAX_LOG_LINES = 300
        private const val LOG_TAG = "GeelyDiagnostics"
    }
}

private fun SourceReadStatus.toReadStatus(): ReadStatus = when (this) {
    SourceReadStatus.CHECKING -> ReadStatus.CHECKING
    SourceReadStatus.AVAILABLE -> ReadStatus.AVAILABLE
    SourceReadStatus.ERROR -> ReadStatus.ERROR
}

private fun Int.hex(): String = "0x${toUInt().toString(16).padStart(8, '0')}"

private fun describe(error: Throwable): String = error.javaClass.name +
    (error.message?.let { ": $it" } ?: "")
