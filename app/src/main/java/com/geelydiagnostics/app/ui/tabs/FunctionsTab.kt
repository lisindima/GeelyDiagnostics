package com.geelydiagnostics.app.ui.tabs

import com.geelydiagnostics.app.model.AppUiState
import com.geelydiagnostics.app.ui.catalog.CatalogListFilter
import com.geelydiagnostics.app.ui.catalog.filterFunctions
import com.geelydiagnostics.app.ui.catalog.formatUpdateTime
import com.geelydiagnostics.app.ui.catalog.rememberCurrentTimeMillis
import com.geelydiagnostics.app.ui.components.CatalogFilterRow
import com.geelydiagnostics.app.ui.components.CatalogScreen
import com.geelydiagnostics.app.ui.components.CatalogSearchField
import com.geelydiagnostics.app.ui.components.DataCard
import com.geelydiagnostics.app.ui.components.FullscreenValueDialog
import com.geelydiagnostics.app.ui.components.SourceStateBadge
import com.geelydiagnostics.app.ui.components.TwoColumnRow
import com.geelydiagnostics.app.ui.components.ValueLine
import com.geelydiagnostics.app.ui.components.aggregateReadStatus
import com.geelydiagnostics.app.ui.components.sourceAttentionDetail
import com.geelydiagnostics.app.ui.parameters.VehicleParameterDetails
import com.geelydiagnostics.app.ui.parameters.VehicleParameterErrors
import com.geelydiagnostics.app.ui.parameters.cardIdLabel
import com.geelydiagnostics.app.ui.parameters.fieldName
import com.geelydiagnostics.app.ui.parameters.selectionKey
import com.geelydiagnostics.app.ui.parameters.sourceLabels
import com.geelydiagnostics.app.ui.theme.AppSpacing
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import com.geelydiagnostics.app.vehicle.property.primaryReading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

internal object FunctionsTab {

    @Composable
    fun Content(
        state: AppUiState,
        onFavoriteToggle: (String) -> Unit,
        onObserveParameter: (VehicleParameter) -> Flow<VehicleParameter?>,
    ) {
        var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var selectedFilterIndex by rememberSaveable { mutableIntStateOf(CatalogListFilter.ALL.ordinal) }
        val selectedFilter = CatalogListFilter.entries[selectedFilterIndex]
        val nowMillis by rememberCurrentTimeMillis()
        val supportedCount = state.functions.count { it.status == VehiclePropertyStatus.AVAILABLE }
        val filtered = filterFunctions(state.functions, selectedFilter, query, state.favoriteKeys)
        val combinedStatus = aggregateReadStatus(listOf(state.functionStatus, state.vhalStatus))
        val combinedDetail = listOfNotNull(
            sourceAttentionDetail("ECARX", state.functionStatus, state.functionDetail),
            sourceAttentionDetail("VHAL", state.vhalStatus, state.vhalDetail),
        ).joinToString(" · ")

        CatalogScreen(
            status = combinedStatus,
            detail = combinedDetail,
            title = "Возможности автомобиля",
            subtitle = "Поддерживаемые возможности ECARX и доступные read-only свойства управления VHAL.",
            totalCount = state.functions.size,
            supportedCount = supportedCount,
            displayedCount = filtered.size,
            emptyText = "По выбранному фильтру функции не найдены.",
            rows = filtered.chunked(2),
            controls = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
                    CatalogSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Название, API, ID, источник или значение",
                    )
                    CatalogFilterRow(
                        labels = CatalogListFilter.entries.map(CatalogListFilter::title),
                        selectedIndex = selectedFilterIndex,
                        onSelected = { selectedFilterIndex = it },
                    )
                }
            },
            statusSupportingContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
                ) {
                    SourceStateBadge("ECARX", state.functionStatus, Modifier.weight(1f))
                    SourceStateBadge("VHAL", state.vhalStatus, Modifier.weight(1f))
                }
            },
        ) { row ->
            TwoColumnRow(row) { function ->
                FunctionCard(
                    function = function,
                    nowMillis = nowMillis,
                    isFavorite = function.favoriteKey in state.favoriteKeys,
                    onFavoriteToggle = { onFavoriteToggle(function.favoriteKey) },
                    onClick = { expandedKey = function.selectionKey },
                )
            }
        }
        state.functions.firstOrNull { it.selectionKey == expandedKey }?.let { selected ->
            val observed by remember(selected.selectionKey) {
                onObserveParameter(selected)
            }.collectAsState(initial = selected)
            val function = observed ?: selected
            FullscreenValueDialog(
                title = function.title,
                apiName = function.fieldName,
                idText = function.cardIdLabel,
                value = function.value,
                sourceLabels = function.sourceLabels,
                modeLabel = function.primaryReading.modeLabel ?: "ДОСТУПНО ЧЕРЕЗ VHAL",
                isFavorite = function.favoriteKey in state.favoriteKeys,
                onFavoriteToggle = { onFavoriteToggle(function.favoriteKey) },
                onDismiss = { expandedKey = null },
            ) {
                VehicleParameterDetails(function, nowMillis)
            }
        }
    }

    @Composable
    private fun FunctionCard(
        function: VehicleParameter,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        val modeLabel = function.primaryReading.modeLabel ?: "ДОСТУПНО ЧЕРЕЗ VHAL"
        DataCard(
            title = function.title,
            apiName = function.fieldName,
            id = function.propertyId?.rawValue ?: function.primaryReading.signalId,
            idLabel = function.cardIdLabel,
            value = function.value,
            sourceLabels = function.sourceLabels,
            modeLabel = modeLabel,
            modeIsHighlighted = function.status == VehiclePropertyStatus.AVAILABLE,
            footerText = formatUpdateTime(function.updatedAtMillis, nowMillis),
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            function.primaryReading.details.take(2).forEach { detail ->
                ValueLine(detail.label, detail.value)
            }
            VehicleParameterErrors(function)
        }
    }
}
