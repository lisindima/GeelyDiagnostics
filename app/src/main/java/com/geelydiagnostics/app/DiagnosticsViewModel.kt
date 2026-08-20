package com.geelydiagnostics.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class DiagnosticsViewModel(application: Application) :
    AndroidViewModel(application),
    ReadOnlySink {

    var uiState by mutableStateOf(
        AppUiState(
            selectedVhalProfile = loadVhalProfile(application),
            favoriteKeys = loadFavorites(application),
        ),
    )
        private set

    private val clients = mutableListOf<Closeable>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var backgroundedAtMillis: Long? = null
    private var retryAttempt = 0
    private var retryScheduled = false
    private var retryLimitLogged = false

    private val retryRunnable = Runnable {
        retryScheduled = false
        if (retryAttempt >= RECONNECT_DELAYS_MILLIS.size) {
            onLog("Automatic reconnect limit reached; use Refresh to try again")
            return@Runnable
        }
        retryAttempt++
        onLog("Automatic reconnect attempt $retryAttempt")
        startReadOnlyScan(resetRetryCounter = false)
    }

    init {
        startReadOnlyScan()
    }

    fun refresh() = startReadOnlyScan()

    fun onForeground() {
        val backgroundDuration = backgroundedAtMillis?.let { System.currentTimeMillis() - it }
        backgroundedAtMillis = null
        val sourceFailed = uiState.carStatus == ReadStatus.ERROR || uiState.vhalStatus == ReadStatus.ERROR
        if (sourceFailed || (backgroundDuration != null && backgroundDuration >= FOREGROUND_RESCAN_AFTER_MILLIS)) {
            onLog("App returned to foreground; reconnecting read-only sources")
            startReadOnlyScan()
        }
    }

    fun onBackground() {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    fun selectVhalProfile(profile: VhalProfile) {
        if (profile == uiState.selectedVhalProfile) return
        preferences().edit().putString(KEY_VHAL_PROFILE, profile.name).apply()
        uiState = uiState.copy(selectedVhalProfile = profile)
        startReadOnlyScan()
    }

    fun toggleFavorite(key: String) {
        val favorites = uiState.favoriteKeys.toMutableSet().apply {
            if (!add(key)) remove(key)
        }.toSet()
        preferences().edit().putStringSet(KEY_FAVORITES, favorites).apply()
        uiState = uiState.copy(favoriteKeys = favorites)
    }

    private fun startReadOnlyScan(resetRetryCounter: Boolean = true) {
        cancelScheduledRetry()
        if (resetRetryCounter) {
            retryAttempt = 0
            retryLimitLogged = false
        }
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
            favoriteKeys = uiState.favoriteKeys,
            scanStartedAtMillis = System.currentTimeMillis(),
        )
        onLog("=== New multi-source read-only scan ===")
        val application = getApplication<Application>()
        try {
            clients += EcarxReadOnlyClient(application, this).also { it.start() }
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
            clients += VhalReadOnlyClient(application, uiState.selectedVhalProfile, this)
                .also { it.start() }
        } catch (error: Throwable) {
            onVhalStatus(ReadStatus.ERROR, describe(error))
            onLog("VHAL initialization failed: ${describe(error)}", error)
        }
    }

    private fun scheduleReconnect(source: String) {
        if (retryScheduled) return
        if (retryAttempt >= RECONNECT_DELAYS_MILLIS.size) {
            if (!retryLimitLogged) {
                retryLimitLogged = true
                onLog("Automatic reconnect limit reached; use Refresh to try again")
            }
            return
        }
        val delay = RECONNECT_DELAYS_MILLIS[retryAttempt]
        retryScheduled = true
        onLog("$source unavailable; reconnect scheduled in ${delay / 1_000}s")
        mainHandler.postDelayed(retryRunnable, delay)
    }

    private fun cancelScheduledRetry() {
        mainHandler.removeCallbacks(retryRunnable)
        retryScheduled = false
    }

    private fun closeClients() {
        clients.forEach { client -> runCatching { client.close() } }
        clients.clear()
    }

    override fun onCarStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(carStatus = status, carDetail = detail)
        if (status == ReadStatus.ERROR) scheduleReconnect("ECARX") else maybeFinishReconnect()
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
        if (status == ReadStatus.ERROR) scheduleReconnect("VHAL") else maybeFinishReconnect()
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
        uiState = uiState.copy(sensors = uiState.sensors.filterNot { it.source == source } + sensors)
    }

    override fun onSensorValueChanged(
        source: VehicleDataSource,
        id: Int,
        value: ApiValue,
        areaId: Int,
    ) = onMain {
        val now = System.currentTimeMillis()
        uiState = uiState.copy(
            sensors = uiState.sensors.map { sensor ->
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
            },
        )
    }

    override fun onSensorSupportChanged(
        source: VehicleDataSource,
        id: Int,
        support: ApiSupportStatus,
    ) = onMain {
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

    fun clearLog() {
        uiState = uiState.copy(logLines = emptyList())
    }

    private fun maybeFinishReconnect() {
        if (uiState.carStatus == ReadStatus.AVAILABLE && uiState.vhalStatus == ReadStatus.AVAILABLE) {
            cancelScheduledRetry()
            retryAttempt = 0
            retryLimitLogged = false
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }

    private fun preferences() = getApplication<Application>()
        .getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)

    private fun describe(error: Throwable): String =
        error.javaClass.name + (error.message?.let { ": $it" } ?: "")

    override fun onCleared() {
        cancelScheduledRetry()
        closeClients()
        super.onCleared()
    }

    companion object {
        private const val MAX_LOG_LINES = 300
        private const val LOG_TAG = "GeelyDiagnostics"
        private const val PREFERENCES = "geely_diagnostics"
        private const val KEY_VHAL_PROFILE = "vhal_profile"
        private const val KEY_FAVORITES = "favorite_keys"
        private const val FOREGROUND_RESCAN_AFTER_MILLIS = 15_000L
        private val RECONNECT_DELAYS_MILLIS = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L, 60_000L)

        private fun loadVhalProfile(application: Application): VhalProfile {
            val saved = application.getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)
                .getString(KEY_VHAL_PROFILE, null)
            return VhalProfile.entries.firstOrNull { it.name == saved } ?: VhalProfile.RAW
        }

        private fun loadFavorites(application: Application): Set<String> =
            application.getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)
                .getStringSet(KEY_FAVORITES, emptySet())
                .orEmpty()
                .toSet()
    }
}
