package com.geelydiagnostics.app.ui.tabs

import com.geelydiagnostics.app.model.AppUiState
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.ui.catalog.CatalogListFilter
import com.geelydiagnostics.app.ui.catalog.filterVehicleInfo
import com.geelydiagnostics.app.ui.catalog.formatUpdateTime
import com.geelydiagnostics.app.ui.catalog.rememberCurrentTimeMillis
import com.geelydiagnostics.app.ui.components.CatalogFilterRow
import com.geelydiagnostics.app.ui.components.CatalogScreen
import com.geelydiagnostics.app.ui.components.CatalogSearchField
import com.geelydiagnostics.app.ui.components.DataCard
import com.geelydiagnostics.app.ui.components.FullscreenValueDialog
import com.geelydiagnostics.app.ui.components.SourceStateBadge
import com.geelydiagnostics.app.ui.components.TwoColumnRow
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

internal object VehicleTab {

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
        val supportedCount = state.vehicleInfo.count {
            it.status == VehiclePropertyStatus.AVAILABLE
        }
        val filtered = filterVehicleInfo(state.vehicleInfo, selectedFilter, query, state.favoriteKeys)
        val combinedStatus = aggregateReadStatus(listOf(state.carInfoStatus, state.vhalStatus))
        val combinedDetail = listOfNotNull(
            sourceAttentionDetail("ECARX", state.carInfoStatus, state.carInfoDetail),
            sourceAttentionDetail("VHAL", state.vhalStatus, state.vhalDetail),
        ).joinToString(" · ")

        CatalogScreen(
            status = combinedStatus,
            detail = combinedDetail,
            title = "Заводские сведения",
            subtitle = "Статические сведения ECARX и стандартные INFO-свойства VHAL. Источники указаны на карточках.",
            totalCount = state.vehicleInfo.size,
            supportedCount = supportedCount,
            displayedCount = filtered.size,
            emptyText = "По выбранному фильтру сведения не найдены.",
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
                SourceStatusRow(state.carInfoStatus, state.vhalStatus)
            },
        ) { row ->
            TwoColumnRow(row) { item ->
                VehicleInfoCard(
                    item = item,
                    nowMillis = nowMillis,
                    isFavorite = item.favoriteKey in state.favoriteKeys,
                    onFavoriteToggle = { onFavoriteToggle(item.favoriteKey) },
                    onClick = { expandedKey = item.selectionKey },
                )
            }
        }
        state.vehicleInfo.firstOrNull { it.selectionKey == expandedKey }?.let { selected ->
            val observed by remember(selected.selectionKey) {
                onObserveParameter(selected)
            }.collectAsState(initial = selected)
            val item = observed ?: selected
            FullscreenValueDialog(
                title = item.title,
                apiName = item.fieldName,
                idText = item.cardIdLabel,
                value = item.value,
                sourceLabels = item.sourceLabels,
                modeLabel = item.primaryReading.modeLabel ?: "СТАТИЧЕСКОЕ ЗНАЧЕНИЕ",
                isFavorite = item.favoriteKey in state.favoriteKeys,
                onFavoriteToggle = { onFavoriteToggle(item.favoriteKey) },
                onDismiss = { expandedKey = null },
            ) {
                VehicleParameterDetails(item, nowMillis)
            }
        }
    }

    @Composable
    private fun VehicleInfoCard(
        item: VehicleParameter,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        DataCard(
            title = item.title,
            apiName = item.fieldName,
            id = item.propertyId?.rawValue ?: item.primaryReading.signalId,
            idLabel = item.cardIdLabel,
            value = item.value,
            sourceLabels = item.sourceLabels,
            modeLabel = item.primaryReading.modeLabel ?: "СТАТИЧЕСКИЕ ДАННЫЕ",
            footerText = formatUpdateTime(item.updatedAtMillis, nowMillis),
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            VehicleParameterErrors(item)
        }
    }

    @Composable
    private fun SourceStatusRow(ecarxStatus: ReadStatus, vhalStatus: ReadStatus) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
        ) {
            SourceStateBadge("ECARX", ecarxStatus, Modifier.weight(1f))
            SourceStateBadge("VHAL", vhalStatus, Modifier.weight(1f))
        }
    }
}
