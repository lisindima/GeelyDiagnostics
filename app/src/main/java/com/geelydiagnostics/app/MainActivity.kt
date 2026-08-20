package com.geelydiagnostics.app

import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), ReadOnlySink {

    private var uiState by mutableStateOf(AppUiState())
    private val clients = mutableListOf<Closeable>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiState = uiState.copy(selectedVhalProfile = loadVhalProfile())
        setContent {
            GeelyDiagnosticsApp(
                state = uiState,
                onRefresh = ::startReadOnlyScan,
                onVhalProfileSelected = ::selectVhalProfile,
                onClearLog = { uiState = uiState.copy(logLines = emptyList()) },
            )
        }
        startReadOnlyScan()
    }

    private fun startReadOnlyScan() {
        closeClients()
        uiState = AppUiState(
            carStatus = ReadStatus.CHECKING,
            diagnosticsStatus = ReadStatus.CHECKING,
            dtcManagerStatus = ReadStatus.CHECKING,
            sensorStatus = ReadStatus.CHECKING,
            vhalStatus = ReadStatus.CHECKING,
            carInfoStatus = ReadStatus.CHECKING,
            functionStatus = ReadStatus.CHECKING,
            selectedVhalProfile = uiState.selectedVhalProfile,
            logLines = uiState.logLines,
        )
        onLog("=== New multi-source read-only scan ===")
        try {
            clients += EcarxReadOnlyClient(applicationContext, this).also { it.start() }
        } catch (error: Throwable) {
            onCarStatus(ReadStatus.ERROR, describe(error))
            onDiagnosticsStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onDtcManagerStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onSensorStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onCarInfoStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onFunctionStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onLog("Initialization failed: ${describe(error)}", error)
        }
        try {
            clients += VhalReadOnlyClient(applicationContext, uiState.selectedVhalProfile, this).also { it.start() }
        } catch (error: Throwable) {
            onVhalStatus(ReadStatus.ERROR, describe(error))
            onLog("VHAL initialization failed: ${describe(error)}", error)
        }
    }

    override fun onDestroy() {
        closeClients()
        super.onDestroy()
    }

    private fun selectVhalProfile(profile: VhalProfile) {
        if (profile == uiState.selectedVhalProfile) return
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(KEY_VHAL_PROFILE, profile.name)
            .apply()
        uiState = uiState.copy(selectedVhalProfile = profile)
        startReadOnlyScan()
    }

    private fun loadVhalProfile(): VhalProfile {
        val saved = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .getString(KEY_VHAL_PROFILE, null)
        return VhalProfile.entries.firstOrNull { it.name == saved } ?: VhalProfile.G426
    }

    private fun closeClients() {
        clients.forEach { client -> runCatching { client.close() } }
        clients.clear()
    }

    override fun onCarStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(carStatus = status, carDetail = detail)
    }

    override fun onDiagnosticsStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(diagnosticsStatus = status, diagnosticsDetail = detail)
    }

    override fun onDtcManagerStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(dtcManagerStatus = status, dtcManagerDetail = detail)
    }

    override fun onSensorStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(sensorStatus = status, sensorDetail = detail)
    }

    override fun onVhalStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(vhalStatus = status, vhalDetail = detail)
    }

    override fun onCarInfoStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(carInfoStatus = status, carInfoDetail = detail)
    }

    override fun onFunctionStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(functionStatus = status, functionDetail = detail)
    }

    override fun onDtcsChanged(dtcs: List<DtcRecord>) = onMain {
        uiState = uiState.copy(dtcs = dtcs)
    }

    override fun onSensorsChanged(source: VehicleDataSource, sensors: List<SensorRecord>) = onMain {
        uiState = uiState.copy(
            sensors = uiState.sensors.filterNot { it.source == source } + sensors,
        )
    }

    override fun onSensorValueChanged(
        source: VehicleDataSource,
        id: Int,
        value: ApiValue,
        areaId: Int,
    ) = onMain {
        uiState = uiState.copy(
            sensors = uiState.sensors.map { sensor ->
                if (sensor.source == source && sensor.id == id && sensor.areaId == areaId) {
                    sensor.copy(value = value, error = "")
                } else {
                    sensor
                }
            },
        )
    }

    override fun onSensorSupportChanged(source: VehicleDataSource, id: Int, support: ApiSupportStatus) = onMain {
        uiState = uiState.copy(
            sensors = uiState.sensors.map { sensor ->
                if (sensor.source == source && sensor.id == id) sensor.copy(support = support) else sensor
            },
        )
    }

    override fun onVehicleInfoChanged(items: List<VehicleInfoRecord>) = onMain {
        uiState = uiState.copy(vehicleInfo = items)
    }

    override fun onFunctionsChanged(functions: List<VehicleFunctionRecord>) = onMain {
        uiState = uiState.copy(functions = functions)
    }

    override fun onLog(message: String, error: Throwable?) = onMain {
        if (error == null) Log.i(LOG_TAG, message) else Log.e(LOG_TAG, message, error)
        val timestamp = timeFormat.format(Date())
        uiState = uiState.copy(
            logLines = (uiState.logLines + "$timestamp  $message").takeLast(MAX_LOG_LINES),
        )
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else runOnUiThread(action)
    }

    private fun describe(error: Throwable): String =
        error.javaClass.name + (error.message?.let { ": $it" } ?: "")

    companion object {
        private const val MAX_LOG_LINES = 300
        private const val LOG_TAG = "GeelyDiagnostics"
        private const val PREFERENCES = "geely_diagnostics"
        private const val KEY_VHAL_PROFILE = "vhal_profile"
    }
}
