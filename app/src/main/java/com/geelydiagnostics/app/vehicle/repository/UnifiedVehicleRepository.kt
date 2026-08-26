package com.geelydiagnostics.app.vehicle.repository

import android.content.Context
import android.util.Log
import com.geelydiagnostics.app.model.DtcRecord
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.ecarx.EcarxDataListener
import com.geelydiagnostics.app.vehicle.ecarx.EcarxDataSource
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehicleDiscoveryProgress
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.source.VehicleParameterDataSource
import com.geelydiagnostics.app.vehicle.vhal.VhalDataSource
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class VehicleRepositoryState(
    val carStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carDetail: String = "",
    val ecarxParameterStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val ecarxParameterDetail: String = "",
    val vhalStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val vhalDetail: String = "",
    val vhalDiscovery: VehicleDiscoveryProgress = VehicleDiscoveryProgress(),
    val carInfoStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val carInfoDetail: String = "",
    val functionStatus: ReadStatus = ReadStatus.NOT_CHECKED,
    val functionDetail: String = "",
    val diagnostics: DiagnosticsState = DiagnosticsState(),
    val logLines: List<String> = emptyList(),
    val scanStartedAtMillis: Long? = null,
)

internal class UnifiedVehicleRepository(
    context: Context,
) : Closeable, EcarxDataListener {
    private val appContext = context.applicationContext
    private val diagnostics = DiagnosticsRepository()
    private val sources = mutableListOf<VehicleParameterDataSource>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val mutableState = MutableStateFlow(VehicleRepositoryState())
    private val parameterStore = UnifiedParameterStore()
    private val freshnessExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "VehicleSourceSelection").apply { isDaemon = true }
    }
    private var freshnessTask: ScheduledFuture<*>? = null
    private var session: VehicleSourceSession? = null

    val state: StateFlow<VehicleRepositoryState> = mutableState.asStateFlow()

    fun observe(
        propertyId: CarPropertyId,
        areaId: Int = 0,
        section: VehicleDataSection = VehicleDataSection.PARAMETER,
    ): Flow<VehicleParameter?> = parameterStore.observe(propertyId, areaId, section)

    fun observe(key: String): Flow<VehicleParameter?> = parameterStore.observe(key)

    fun observeParameters(): StateFlow<List<VehicleParameter>> = parameterStore.parameters

    @Synchronized
    fun start(profile: VehicleProfile, vhalBackend: VhalGatewayBackend) {
        freshnessTask?.cancel(false)
        session?.close()
        val newSession = VehicleSourceSession(this, this).also { session = it }
        val previousLog = mutableState.value.logLines
        sources.forEach { runCatching { it.close() } }
        sources.clear()
        diagnostics.reset()
        parameterStore.clear()
        mutableState.value = VehicleRepositoryState(
            carStatus = ReadStatus.CHECKING,
            ecarxParameterStatus = ReadStatus.CHECKING,
            vhalStatus = ReadStatus.CHECKING,
            carInfoStatus = ReadStatus.CHECKING,
            functionStatus = ReadStatus.CHECKING,
            diagnostics = diagnostics.snapshot(),
            logLines = previousLog,
            scanStartedAtMillis = System.currentTimeMillis(),
        )
        onSystemLog("=== Новый опрос источников ===")
        // Re-evaluate cached freshness, never poll vehicle APIs. Equal StateFlow values are suppressed.
        freshnessTask = freshnessExecutor.scheduleWithFixedDelay(
            { parameterStore.refreshSelection() }, 1, 1, TimeUnit.SECONDS,
        )
        try {
            sources += EcarxDataSource(appContext, newSession).also { it.start() }
        } catch (error: Throwable) {
            onCarStatus(ReadStatus.ERROR, describe(error))
            onDiagnosticsStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onDtcManagerStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onParameterStatus(
                VehiclePropertySource.ECARX,
                ReadStatus.ERROR,
                "ECARX API unavailable",
            )
            onCarInfoStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onFunctionStatus(ReadStatus.ERROR, "ECARX API unavailable")
            appendLog("ECARX", "initialization failed: ${describe(error)}", error)
        }
        try {
            sources += VhalDataSource(appContext, profile, vhalBackend, newSession).also { it.start() }
        } catch (error: Throwable) {
            onParameterStatus(VehiclePropertySource.VHAL, ReadStatus.ERROR, describe(error))
            appendLog("VHAL", "initialization failed: ${describe(error)}", error)
        }
    }

    @Synchronized
    override fun onCarStatus(status: ReadStatus, detail: String) {
        if (status == ReadStatus.ERROR) parameterStore.sourceAvailable(VehiclePropertySource.ECARX, false)
        update { copy(carStatus = status, carDetail = detail) }
    }

    @Synchronized
    override fun onDiagnosticsStatus(status: ReadStatus, detail: String) {
        diagnostics.updateDiagnostics(status, detail)
        update { copy(diagnostics = this@UnifiedVehicleRepository.diagnostics.snapshot()) }
    }

    @Synchronized
    override fun onDtcManagerStatus(status: ReadStatus, detail: String) {
        diagnostics.updateManager(status, detail)
        update { copy(diagnostics = this@UnifiedVehicleRepository.diagnostics.snapshot()) }
    }

    @Synchronized
    override fun onDtcsChanged(dtcs: List<DtcRecord>) {
        diagnostics.updateDtcs(dtcs)
        update { copy(diagnostics = this@UnifiedVehicleRepository.diagnostics.snapshot()) }
    }

    @Synchronized
    override fun onDiagnosticDetails(details: com.geelydiagnostics.app.model.EcarxDiagnosticDetails) {
        diagnostics.updateDetails(details)
        update { copy(diagnostics = this@UnifiedVehicleRepository.diagnostics.snapshot()) }
    }

    @Synchronized
    override fun onObd2Snapshot(snapshot: com.geelydiagnostics.app.model.Obd2Snapshot) {
        diagnostics.updateObd2(snapshot)
        update { copy(diagnostics = this@UnifiedVehicleRepository.diagnostics.snapshot()) }
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
    override fun onParameterStatus(
        source: VehiclePropertySource,
        status: ReadStatus,
        detail: String,
    ) {
        if (status == ReadStatus.ERROR || status == ReadStatus.AVAILABLE) {
            parameterStore.sourceAvailable(source, status == ReadStatus.AVAILABLE)
        }
        update {
            when (source) {
                VehiclePropertySource.ECARX -> copy(ecarxParameterStatus = status, ecarxParameterDetail = detail)
                VehiclePropertySource.VHAL -> copy(vhalStatus = status, vhalDetail = detail)
                VehiclePropertySource.MOCK -> this
            }
        }
    }

    @Synchronized
    override fun onParameterDiscovery(source: VehiclePropertySource, progress: VehicleDiscoveryProgress) {
        if (source == VehiclePropertySource.VHAL) update { copy(vhalDiscovery = progress) }
    }

    @Synchronized
    override fun onParameterSnapshot(
        source: VehiclePropertySource,
        section: VehicleDataSection?,
        values: List<CarPropertySnapshot>,
    ) {
        parameterStore.replaceSource(source, values, section)
    }

    @Synchronized
    override fun onParameterValue(value: CarPropertySnapshot) {
        parameterStore.update(value)
    }

    override fun onParameterLog(
        source: VehiclePropertySource,
        message: String,
        error: Throwable?,
    ) = appendLog(source.label, message, error)

    @Synchronized
    override fun onLog(message: String, error: Throwable?) = appendLog("ECARX", message, error)

    @Synchronized
    fun onSystemLog(message: String, error: Throwable? = null) = appendLog("СИСТЕМА", message, error)

    @Synchronized
    private fun appendLog(source: String, message: String, error: Throwable?) {
        if (error == null) Log.i(LOG_TAG, message) else Log.e(LOG_TAG, message, error)
        val timestamp = timeFormat.format(Date())
        val taggedMessage = if (message.startsWith(source, ignoreCase = true)) {
            message
        } else {
            "$source · $message"
        }
        update {
            copy(logLines = (logLines + "$timestamp  $taggedMessage").takeLast(MAX_LOG_LINES))
        }
    }

    @Synchronized
    fun clearLog() = update { copy(logLines = emptyList()) }

    private fun update(block: VehicleRepositoryState.() -> VehicleRepositoryState) {
        mutableState.value = mutableState.value.block()
    }

    @Synchronized
    override fun close() {
        session?.close()
        freshnessTask?.cancel(false)
        freshnessExecutor.shutdownNow()
        sources.forEach { runCatching { it.close() } }
        sources.clear()
    }

    companion object {
        private const val MAX_LOG_LINES = 300
        private const val LOG_TAG = "GeelyDiagnostics"
    }
}

private fun describe(error: Throwable): String = error.javaClass.name +
    (error.message?.let { ": $it" } ?: "")
