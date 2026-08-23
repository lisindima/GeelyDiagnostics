package com.geelydiagnostics.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.repository.UnifiedVehicleRepository
import com.geelydiagnostics.app.vehicle.repository.VehicleRepositoryState

internal class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(
        AppUiState(
            selectedVhalProfile = loadVhalProfile(application),
            favoriteKeys = loadFavorites(application),
        ),
    )
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val repository = UnifiedVehicleRepository(application, ::onRepositoryState)

    init {
        startScan()
    }

    fun refresh() = startScan()

    fun selectVhalProfile(profile: VehicleProfile) {
        if (profile == uiState.selectedVhalProfile) return
        preferences().edit().putString(KEY_VHAL_PROFILE, profile.name).apply()
        uiState = uiState.copy(selectedVhalProfile = profile)
        startScan()
    }

    fun toggleFavorite(key: String) {
        val favorites = uiState.favoriteKeys.toMutableSet().apply {
            if (!add(key)) remove(key)
        }.toSet()
        preferences().edit().putStringSet(KEY_FAVORITES, favorites).apply()
        uiState = uiState.copy(favoriteKeys = favorites)
    }

    fun clearLog() = repository.clearLog()

    fun onLog(message: String, error: Throwable? = null) = repository.onLog(message, error)

    private fun startScan() {
        repository.start(uiState.selectedVhalProfile)
    }

    private fun onRepositoryState(repositoryState: VehicleRepositoryState) = onMain {
        var history = uiState.sensorHistory
        repositoryState.sensors.forEach { sensor ->
            history = history.withSample(sensor, sensor.updatedAtMillis ?: System.currentTimeMillis())
        }
        uiState = uiState.copy(
            carStatus = repositoryState.carStatus,
            carDetail = repositoryState.carDetail,
            diagnosticsStatus = repositoryState.diagnostics.diagnosticsStatus,
            diagnosticsDetail = repositoryState.diagnostics.diagnosticsDetail,
            dtcManagerStatus = repositoryState.diagnostics.dtcManagerStatus,
            dtcManagerDetail = repositoryState.diagnostics.dtcManagerDetail,
            sensorStatus = repositoryState.sensorStatus,
            sensorDetail = repositoryState.sensorDetail,
            vhalStatus = repositoryState.vhalStatus,
            vhalDetail = repositoryState.vhalDetail,
            carInfoStatus = repositoryState.carInfoStatus,
            carInfoDetail = repositoryState.carInfoDetail,
            functionStatus = repositoryState.functionStatus,
            functionDetail = repositoryState.functionDetail,
            dtcs = repositoryState.diagnostics.dtcs,
            sensors = repositoryState.sensors,
            sensorHistory = history,
            vehicleInfo = repositoryState.vehicleInfo,
            functions = repositoryState.functions,
            logLines = repositoryState.logLines,
            scanStartedAtMillis = repositoryState.scanStartedAtMillis,
        )
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }

    private fun preferences() = getApplication<Application>()
        .getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)

    private fun Map<String, List<SensorSample>>.withSample(
        sensor: SensorRecord,
        timestampMillis: Long,
    ): Map<String, List<SensorSample>> {
        if (!sensor.chartable) return this
        val numericValue = sensor.value.chartNumber() ?: return this
        val key = sensor.favoriteKey
        val sample = SensorSample(timestampMillis = timestampMillis, value = numericValue)
        val existing = get(key).orEmpty()
        if (existing.lastOrNull() == sample) return this
        val oldestAllowed = timestampMillis - SENSOR_HISTORY_WINDOW_MILLIS
        val updated = (existing.asSequence()
            .dropWhile { it.timestampMillis < oldestAllowed }
            .toList() + sample)
            .takeLast(MAX_SENSOR_HISTORY_SAMPLES)
        return this + (key to updated)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    companion object {
        private const val MAX_SENSOR_HISTORY_SAMPLES = 360
        private const val SENSOR_HISTORY_WINDOW_MILLIS = 120_000L
        private const val PREFERENCES = "geely_diagnostics"
        private const val KEY_VHAL_PROFILE = "vhal_profile"
        private const val KEY_FAVORITES = "favorite_keys"

        private fun loadVhalProfile(application: Application): VehicleProfile {
            val saved = application.getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)
                .getString(KEY_VHAL_PROFILE, null)
            return VehicleProfile.entries.firstOrNull { it.name == saved } ?: VehicleProfile.RAW
        }

        private fun loadFavorites(application: Application): Set<String> =
            application.getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)
                .getStringSet(KEY_FAVORITES, emptySet())
                .orEmpty()
                .toSet()
    }
}
