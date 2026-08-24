package com.geelydiagnostics.app.ui.tabs

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.catalog.*
import com.geelydiagnostics.app.ui.components.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

internal object VehicleTab {

    @Composable
    fun Content(state: AppUiState, onFavoriteToggle: (String) -> Unit) {
        var expandedId by rememberSaveable { mutableStateOf<Int?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var selectedFilterIndex by rememberSaveable { mutableIntStateOf(CatalogListFilter.ALL.ordinal) }
        val selectedFilter = CatalogListFilter.entries[selectedFilterIndex]
        val nowMillis = remember(state.vehicleInfo) { System.currentTimeMillis() }
        val supportedCount = state.vehicleInfo.count { it.support.isVisibleAsSupported }
        val filtered = filterVehicleInfo(state.vehicleInfo, selectedFilter, query, state.favoriteKeys)
        CatalogScreen(
            status = state.carInfoStatus,
            detail = state.carInfoDetail,
            title = "Заводские сведения",
            subtitle = "Статические сведения о машине и комплектации. Источник указан на каждой карточке.",
            totalCount = state.vehicleInfo.size,
            supportedCount = supportedCount,
            displayedCount = filtered.size,
            emptyText = "По выбранному фильтру сведения не найдены.",
            rows = filtered.chunked(2),
            controls = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatalogSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Название, API, ID или значение",
                    )
                    CatalogFilterRow(
                        labels = CatalogListFilter.entries.map(CatalogListFilter::title),
                        selectedIndex = selectedFilterIndex,
                        onSelected = { selectedFilterIndex = it },
                    )
                }
            },
        ) { row ->
            TwoColumnRow(row) { item ->
                VehicleInfoCard(
                    item = item,
                    nowMillis = nowMillis,
                    isFavorite = item.favoriteKey in state.favoriteKeys,
                    onFavoriteToggle = { onFavoriteToggle(item.favoriteKey) },
                    onClick = { expandedId = item.id },
                )
            }
        }
        state.vehicleInfo.firstOrNull { it.id == expandedId }?.let { item ->
            FullscreenValueDialog(
                title = item.title,
                apiName = item.apiName,
                idText = "ECARX ID ${item.id}",
                value = item.value,
                sourceLabels = listOf(item.source.label),
                modeLabel = "СТАТИЧЕСКОЕ ЗНАЧЕНИЕ",
                isFavorite = item.favoriteKey in state.favoriteKeys,
                onFavoriteToggle = { onFavoriteToggle(item.favoriteKey) },
                onDismiss = { expandedId = null },
            ) {
                VehicleInfoDetails(item, nowMillis)
            }
        }
    }

    @Composable
    private fun VehicleInfoCard(
        item: VehicleInfoRecord,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        DataCard(
            title = item.title,
            apiName = item.apiName,
            id = item.id,
            idLabel = "ECARX ID ${item.id}",
            value = item.value,
            sourceLabels = listOf(item.source.label),
            modeLabel = "СТАТИЧЕСКИЕ ДАННЫЕ",
            footerText = formatUpdateTime(item.updatedAtMillis, nowMillis),
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            if (item.error.isNotBlank()) ValueLine("Ошибка", item.error)
        }
    }

    @Composable
    private fun VehicleInfoDetails(item: VehicleInfoRecord, nowMillis: Long) {
        ValueLine("Обновлено", formatUpdateTime(item.updatedAtMillis, nowMillis))
        if (item.error.isNotBlank()) ValueLine("Ошибка", item.error)
    }
}
