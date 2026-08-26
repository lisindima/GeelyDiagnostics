package com.geelydiagnostics.app.ui.viewmodel

import com.geelydiagnostics.app.model.AppUiState
import com.geelydiagnostics.app.ui.components.chartNumber

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.numericValue
import com.geelydiagnostics.app.vehicle.property.VehicleParameterSample
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import com.geelydiagnostics.app.vehicle.property.legacyFavoriteKeys
import com.geelydiagnostics.app.vehicle.repository.UnifiedVehicleRepository
import com.geelydiagnostics.app.vehicle.repository.VehicleRepositoryState
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

internal class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(
        AppUiState(
            selectedVhalProfile = loadVhalProfile(application),
            selectedVhalBackend = loadVhalBackend(application),
            favoriteKeys = loadFavorites(application),
        ),
    )
        private set

    private val repository = UnifiedVehicleRepository(application)

    init {
        viewModelScope.launch {
            repository.state.collect { repositoryState ->
                onRepositoryState(repositoryState)
            }
        }
        viewModelScope.launch {
            repository.observeParameters().collect(::onParametersChanged)
        }
        startScan()
    }

    fun refresh() = startScan()

    fun selectVhalProfile(profile: VehicleProfile) {
        if (profile == uiState.selectedVhalProfile) return
        preferences().edit().putString(KEY_VHAL_PROFILE, profile.name).apply()
        uiState = uiState.copy(selectedVhalProfile = profile)
        startScan()
    }

    fun selectVhalBackend(backend: VhalGatewayBackend) {
        if (backend == uiState.selectedVhalBackend) return
        preferences().edit().putString(KEY_VHAL_BACKEND, backend.name).apply()
        uiState = uiState.copy(selectedVhalBackend = backend)
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

    fun onLog(message: String, error: Throwable? = null) = repository.onSystemLog(message, error)

    fun observeParameter(parameter: VehicleParameter): Flow<VehicleParameter?> =
        parameter.propertyId?.let { propertyId ->
            repository.observe(propertyId, parameter.areaId, parameter.section)
        } ?: repository.observe(parameter.favoriteKey)

    private fun startScan() {
        repository.start(uiState.selectedVhalProfile, uiState.selectedVhalBackend)
    }

    private fun onRepositoryState(repositoryState: VehicleRepositoryState) {
        uiState = uiState.copy(
            carStatus = repositoryState.carStatus,
            carDetail = repositoryState.carDetail,
            diagnosticsStatus = repositoryState.diagnostics.diagnosticsStatus,
            diagnosticsDetail = repositoryState.diagnostics.diagnosticsDetail,
            dtcManagerStatus = repositoryState.diagnostics.dtcManagerStatus,
            dtcManagerDetail = repositoryState.diagnostics.dtcManagerDetail,
            ecarxParameterStatus = repositoryState.ecarxParameterStatus,
            ecarxParameterDetail = repositoryState.ecarxParameterDetail,
            vhalStatus = repositoryState.vhalStatus,
            vhalDetail = repositoryState.vhalDetail,
            vhalDiscovery = repositoryState.vhalDiscovery,
            carInfoStatus = repositoryState.carInfoStatus,
            carInfoDetail = repositoryState.carInfoDetail,
            functionStatus = repositoryState.functionStatus,
            functionDetail = repositoryState.functionDetail,
            dtcs = repositoryState.diagnostics.dtcs,
            logLines = repositoryState.logLines,
            scanStartedAtMillis = repositoryState.scanStartedAtMillis,
        )
    }

    private fun onParametersChanged(allParameters: List<VehicleParameter>) {
        val parameters = allParameters.filter { it.section == VehicleDataSection.PARAMETER }
        var history = if (
            uiState.scanStartedAtMillis != null &&
            parameters.isEmpty()
        ) {
            emptyMap()
        } else {
            uiState.parameterHistory
        }
        parameters.forEach { parameter ->
            history = history.withSample(
                parameter,
                parameter.updatedAtMillis ?: System.currentTimeMillis(),
            )
        }
        val favoriteKeys = migrateParameterFavorites(uiState.favoriteKeys, allParameters)
        if (favoriteKeys != uiState.favoriteKeys) {
            preferences().edit().putStringSet(KEY_FAVORITES, favoriteKeys).apply()
        }
        uiState = uiState.copy(
            parameters = parameters,
            parameterHistory = history,
            vehicleInfo = allParameters.filter { it.section == VehicleDataSection.VEHICLE_INFO },
            functions = allParameters.filter { it.section == VehicleDataSection.CAPABILITY },
            favoriteKeys = favoriteKeys,
        )
    }

    private fun migrateParameterFavorites(
        current: Set<String>,
        parameters: List<VehicleParameter>,
    ): Set<String> {
        val migrated = current.toMutableSet()
        parameters.forEach { parameter ->
            val legacyKeys = parameter.legacyFavoriteKeys
            if (legacyKeys.any(migrated::contains)) {
                migrated.removeAll(legacyKeys.toSet())
                migrated += parameter.favoriteKey
            }
        }
        return migrated
    }

    private fun preferences() = getApplication<Application>()
        .getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)

    private fun Map<String, List<VehicleParameterSample>>.withSample(
        parameter: VehicleParameter,
        timestampMillis: Long,
    ): Map<String, List<VehicleParameterSample>> {
        if (!parameter.chartable) return this
        val numericValue = parameter.normalizedValue?.numericValue
            ?: parameter.value.chartNumber().takeIf { parameter.propertyId == null }
            ?: return this
        val key = parameter.favoriteKey
        val sample = VehicleParameterSample(timestampMillis = timestampMillis, value = numericValue)
        val existing = get(key).orEmpty()
        if (existing.lastOrNull() == sample) return this
        val oldestAllowed = timestampMillis - PARAMETER_HISTORY_WINDOW_MILLIS
        val updated = (existing.asSequence()
            .dropWhile { it.timestampMillis < oldestAllowed }
            .toList() + sample)
            .takeLast(MAX_PARAMETER_HISTORY_SAMPLES)
        return this + (key to updated)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    companion object {
        private const val MAX_PARAMETER_HISTORY_SAMPLES = 360
        private const val PARAMETER_HISTORY_WINDOW_MILLIS = 120_000L
        private const val PREFERENCES = "geely_diagnostics"
        private const val KEY_VHAL_PROFILE = "vhal_profile"
        private const val KEY_VHAL_BACKEND = "vhal_backend"
        private const val KEY_FAVORITES = "favorite_keys"

        private fun loadVhalProfile(application: Application): VehicleProfile {
            val saved = application.getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)
                .getString(KEY_VHAL_PROFILE, null)
            return VehicleProfile.entries.firstOrNull { it.name == saved } ?: VehicleProfile.RAW
        }

        private fun loadVhalBackend(application: Application): VhalGatewayBackend {
            val saved = application.getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)
                .getString(KEY_VHAL_BACKEND, null)
            return VhalGatewayBackend.entries.firstOrNull { it.name == saved }
                ?: VhalGatewayBackend.CAR_PROPERTY_MANAGER
        }

        private fun loadFavorites(application: Application): Set<String> =
            application.getSharedPreferences(PREFERENCES, Application.MODE_PRIVATE)
                .getStringSet(KEY_FAVORITES, emptySet())
                .orEmpty()
                .toSet()
    }
}
