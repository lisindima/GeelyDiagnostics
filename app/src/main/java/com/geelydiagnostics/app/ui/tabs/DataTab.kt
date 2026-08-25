package com.geelydiagnostics.app.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.geelydiagnostics.app.model.AppUiState
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.ui.catalog.ParameterValueFilter
import com.geelydiagnostics.app.ui.catalog.filterParameters
import com.geelydiagnostics.app.ui.catalog.isStale
import com.geelydiagnostics.app.ui.catalog.rememberCurrentTimeMillis
import com.geelydiagnostics.app.ui.components.CatalogFilterRow
import com.geelydiagnostics.app.ui.components.CatalogSearchField
import com.geelydiagnostics.app.ui.components.CountSummary
import com.geelydiagnostics.app.ui.components.DataCard
import com.geelydiagnostics.app.ui.components.EmptyMessage
import com.geelydiagnostics.app.ui.components.FullscreenValueDialog
import com.geelydiagnostics.app.ui.components.SourceStateBadge
import com.geelydiagnostics.app.ui.components.TwoColumnRow
import com.geelydiagnostics.app.ui.components.aggregateReadStatus
import com.geelydiagnostics.app.ui.components.sourceAttentionDetail
import com.geelydiagnostics.app.ui.parameters.ParameterFullscreenDialog
import com.geelydiagnostics.app.ui.parameters.VehicleParameterDetails
import com.geelydiagnostics.app.ui.parameters.VehicleParameterErrors
import com.geelydiagnostics.app.ui.parameters.cardIdLabel
import com.geelydiagnostics.app.ui.parameters.fieldName
import com.geelydiagnostics.app.ui.parameters.selectionKey
import com.geelydiagnostics.app.ui.parameters.sourceLabels
import com.geelydiagnostics.app.ui.theme.AppSizes
import com.geelydiagnostics.app.ui.theme.AppSpacing
import com.geelydiagnostics.app.ui.theme.AppType
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import com.geelydiagnostics.app.vehicle.property.primaryReading
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend
import kotlinx.coroutines.flow.Flow

internal enum class DataCategory(val title: String) {
    ALL("Все"),
    PARAMETERS("Параметры"),
    VEHICLE("Автомобиль"),
    CAPABILITIES("Возможности"),
}

internal object DataTab {

    @Composable
    fun Content(
        state: AppUiState,
        initialCategory: DataCategory,
        onVhalProfileSelected: (VehicleProfile) -> Unit,
        onVhalBackendSelected: (VhalGatewayBackend) -> Unit,
        onFavoriteToggle: (String) -> Unit,
        onObserveParameter: (VehicleParameter) -> Flow<VehicleParameter?>,
    ) {
        var selectedCategoryIndex by rememberSaveable {
            mutableIntStateOf(initialCategory.ordinal)
        }
        var selectedValueFilterIndex by rememberSaveable {
            mutableIntStateOf(ParameterValueFilter.ALL.ordinal)
        }
        var query by rememberSaveable { mutableStateOf("") }
        var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
        var expandedSectionName by rememberSaveable { mutableStateOf<String?>(null) }
        var showVhalSettings by rememberSaveable { mutableStateOf(false) }

        val category = DataCategory.entries[selectedCategoryIndex]
        val valueFilter = ParameterValueFilter.entries[selectedValueFilterIndex]
        val categoryValues = category.values(state)
        val filtered = filterParameters(
            records = categoryValues,
            valueFilter = valueFilter,
            query = query,
            favoriteKeys = state.favoriteKeys,
        )
        val autoUpdating = filtered.filter(VehicleParameter::autoUpdates)
        val manuallyUpdated = filtered.filterNot(VehicleParameter::autoUpdates)
        val nowMillis by rememberCurrentTimeMillis()

        DataList(
            state = state,
            category = category,
            selectedCategoryIndex = selectedCategoryIndex,
            onCategorySelected = { selectedCategoryIndex = it },
            selectedValueFilterIndex = selectedValueFilterIndex,
            onValueFilterSelected = { selectedValueFilterIndex = it },
            query = query,
            onQueryChange = { query = it },
            filtered = filtered,
            autoUpdating = autoUpdating,
            manuallyUpdated = manuallyUpdated,
            onOpenVhalSettings = { showVhalSettings = true },
            onFavoriteToggle = onFavoriteToggle,
            onParameterSelected = {
                expandedKey = it.selectionKey
                expandedSectionName = it.section.name
            },
            nowMillis = nowMillis,
        )

        state.allVehicleData.firstOrNull {
            it.selectionKey == expandedKey && it.section.name == expandedSectionName
        }?.let { selected ->
            val observed by remember(selected.section, selected.selectionKey) {
                onObserveParameter(selected)
            }.collectAsState(initial = selected)
            VehicleDataDialog(
                parameter = observed ?: selected,
                state = state,
                nowMillis = nowMillis,
                onFavoriteToggle = onFavoriteToggle,
                onDismiss = {
                    expandedKey = null
                    expandedSectionName = null
                },
            )
        }

        if (showVhalSettings) {
            VhalSettingsDialog(
                selectedProfile = state.selectedVhalProfile,
                selectedBackend = state.selectedVhalBackend,
                decodedCount = state.decodedVhalCount,
                totalCount = state.totalVhalCount,
                onProfileSelected = onVhalProfileSelected,
                onBackendSelected = onVhalBackendSelected,
                onDismiss = { showVhalSettings = false },
            )
        }
    }

    @Composable
    private fun DataList(
        state: AppUiState,
        category: DataCategory,
        selectedCategoryIndex: Int,
        onCategorySelected: (Int) -> Unit,
        selectedValueFilterIndex: Int,
        onValueFilterSelected: (Int) -> Unit,
        query: String,
        onQueryChange: (String) -> Unit,
        filtered: List<VehicleParameter>,
        autoUpdating: List<VehicleParameter>,
        manuallyUpdated: List<VehicleParameter>,
        onOpenVhalSettings: () -> Unit,
        onFavoriteToggle: (String) -> Unit,
        onParameterSelected: (VehicleParameter) -> Unit,
        nowMillis: Long,
    ) {
        val ecarxStatus = state.ecarxCatalogStatus
        val ecarxDetail = state.ecarxCatalogDetail
        val combinedStatus = aggregateReadStatus(listOf(ecarxStatus, state.vhalStatus))
        val combinedDetail = listOfNotNull(
            sourceAttentionDetail("ECARX", ecarxStatus, ecarxDetail),
            sourceAttentionDetail("VHAL", state.vhalStatus, state.vhalDetail),
        ).joinToString(" · ")
        val groups = listOf(
            DataGroup("Автообновление", "Новые значения приходят по подписке", autoUpdating),
            DataGroup(
                "Ручное обновление",
                "Статические данные и значения полного опроса",
                manuallyUpdated,
            ),
        ).filter { it.values.isNotEmpty() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = AppSpacing.ScreenTop,
                bottom = AppSpacing.ScreenBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Default),
        ) {
            item {
                DataSourcesCard(
                    state = state,
                    status = combinedStatus,
                    detail = combinedDetail,
                    ecarxStatus = ecarxStatus,
                    onOpenSettings = onOpenVhalSettings,
                )
            }
            item {
                CatalogFilterRow(
                    labels = DataCategory.entries.map(DataCategory::title),
                    selectedIndex = selectedCategoryIndex,
                    onSelected = onCategorySelected,
                )
            }
            item {
                CatalogSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = "Название, API, ID, источник или значение",
                )
            }
            item {
                CatalogFilterRow(
                    labels = ParameterValueFilter.entries.map(ParameterValueFilter::title),
                    selectedIndex = selectedValueFilterIndex,
                    onSelected = onValueFilterSelected,
                )
            }
            item {
                CountSummary(
                    title = category.summaryTitle,
                    count = filtered.size,
                    detail = "Автообновление ${autoUpdating.size} · " +
                        "ручное обновление ${manuallyUpdated.size}",
                )
            }
            if (groups.isEmpty()) {
                item { EmptyMessage(category.emptyText) }
            } else {
                groups.forEach { group ->
                    item {
                        CountSummary(
                            title = group.title,
                            count = group.values.size,
                            detail = group.subtitle,
                            modifier = Modifier.padding(top = AppSpacing.Small),
                        )
                    }
                    group.values.chunked(2).forEach { row ->
                        item {
                            TwoColumnRow(row) { parameter ->
                                UnifiedDataCard(
                                    parameter = parameter,
                                    nowMillis = nowMillis,
                                    isFavorite = parameter.favoriteKey in state.favoriteKeys,
                                    onFavoriteToggle = {
                                        onFavoriteToggle(parameter.favoriteKey)
                                    },
                                    onClick = { onParameterSelected(parameter) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DataSourcesCard(
        state: AppUiState,
        status: ReadStatus,
        detail: String,
        ecarxStatus: ReadStatus,
        onOpenSettings: () -> Unit,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.CardContent),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Источники данных",
                            fontSize = AppType.CardTitle,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Единый каталог · ${state.selectedVhalProfile.key} · " +
                                state.selectedVhalBackend.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppType.Supporting,
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = "Настройки VHAL",
                            fontSize = AppType.Supporting,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
                ) {
                    SourceStateBadge("ECARX", ecarxStatus, Modifier.weight(1f))
                    SourceStateBadge("VHAL", state.vhalStatus, Modifier.weight(1f))
                }
                if (status == ReadStatus.PARTIAL || status == ReadStatus.ERROR) {
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = AppType.Supporting,
                    )
                }
            }
        }
    }

    @Composable
    private fun UnifiedDataCard(
        parameter: VehicleParameter,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        val stale = parameter.isStale(nowMillis)
        DataCard(
            title = parameter.title,
            apiName = parameter.fieldName,
            id = parameter.propertyId?.rawValue ?: parameter.primaryReading.signalId,
            idLabel = parameter.cardIdLabel,
            value = parameter.value,
            sourceLabels = parameter.sourceLabels,
            modeLabel = parameter.cardModeLabel,
            footerText = if (stale) "УСТАРЕЛО" else null,
            footerIsError = stale,
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            VehicleParameterErrors(parameter)
        }
    }

    @Composable
    private fun VehicleDataDialog(
        parameter: VehicleParameter,
        state: AppUiState,
        nowMillis: Long,
        onFavoriteToggle: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val favorite = parameter.favoriteKey in state.favoriteKeys
        if (parameter.section == VehicleDataSection.PARAMETER) {
            ParameterFullscreenDialog(
                parameter = parameter,
                history = state.parameterHistory[parameter.favoriteKey].orEmpty(),
                nowMillis = nowMillis,
                isFavorite = favorite,
                onFavoriteToggle = { onFavoriteToggle(parameter.favoriteKey) },
                onDismiss = onDismiss,
            )
        } else {
            FullscreenValueDialog(
                title = parameter.title,
                apiName = parameter.fieldName,
                idText = parameter.cardIdLabel,
                value = parameter.value,
                sourceLabels = parameter.sourceLabels,
                modeLabel = parameter.cardModeLabel,
                isFavorite = favorite,
                onFavoriteToggle = { onFavoriteToggle(parameter.favoriteKey) },
                onDismiss = onDismiss,
            ) {
                VehicleParameterDetails(parameter, nowMillis)
            }
        }
    }

    private data class DataGroup(
        val title: String,
        val subtitle: String,
        val values: List<VehicleParameter>,
    )
}

private val AppUiState.allVehicleData: List<VehicleParameter>
    get() = parameters + vehicleInfo + functions

private val AppUiState.totalVhalCount: Int
    get() = allVehicleData.flatMap(VehicleParameter::sourceReadings)
        .count { it.source == VehiclePropertySource.VHAL }

private val AppUiState.decodedVhalCount: Int
    get() = allVehicleData.flatMap(VehicleParameter::sourceReadings)
        .count { it.source == VehiclePropertySource.VHAL && it.decoded }

private fun DataCategory.values(state: AppUiState): List<VehicleParameter> = when (this) {
    DataCategory.ALL -> state.allVehicleData
    DataCategory.PARAMETERS -> state.parameters
    DataCategory.VEHICLE -> state.vehicleInfo
    DataCategory.CAPABILITIES -> state.functions
}

private val AppUiState.ecarxCatalogStatus: ReadStatus
    get() = aggregateReadStatus(
        listOf(ecarxParameterStatus, carInfoStatus, functionStatus),
    )

private val AppUiState.ecarxCatalogDetail: String
    get() = listOfNotNull(
        sourceAttentionDetail("параметры", ecarxParameterStatus, ecarxParameterDetail),
        sourceAttentionDetail("автомобиль", carInfoStatus, carInfoDetail),
        sourceAttentionDetail("возможности", functionStatus, functionDetail),
    ).joinToString(" · ")

private val DataCategory.summaryTitle: String
    get() = when (this) {
        DataCategory.ALL -> "Все данные"
        DataCategory.PARAMETERS -> "Параметры"
        DataCategory.VEHICLE -> "Автомобиль"
        DataCategory.CAPABILITIES -> "Возможности"
    }

private val DataCategory.emptyText: String
    get() = when (this) {
        DataCategory.ALL -> "По выбранному фильтру данные не найдены."
        DataCategory.PARAMETERS -> "По выбранному фильтру параметры не найдены."
        DataCategory.VEHICLE -> "По выбранному фильтру сведения не найдены."
        DataCategory.CAPABILITIES -> "По выбранному фильтру возможности не найдены."
    }

private val VehicleParameter.cardModeLabel: String
    get() = when (section) {
        VehicleDataSection.PARAMETER -> if (autoUpdates) {
            "АВТООБНОВЛЕНИЕ"
        } else {
            "РУЧНОЕ ОБНОВЛЕНИЕ"
        }
        VehicleDataSection.VEHICLE_INFO -> primaryReading.modeLabel ?: "СТАТИЧЕСКИЕ ДАННЫЕ"
        VehicleDataSection.CAPABILITY -> primaryReading.modeLabel ?: "ДОСТУПНО ЧЕРЕЗ VHAL"
    }
