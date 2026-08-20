package com.geelydiagnostics.app

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

internal object FunctionsTab {

    @Composable
    fun Content(state: AppUiState, onFavoriteToggle: (String) -> Unit) {
        var expandedId by rememberSaveable { mutableStateOf<Int?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var selectedFilterIndex by rememberSaveable { mutableIntStateOf(CatalogListFilter.ALL.ordinal) }
        val selectedFilter = CatalogListFilter.entries[selectedFilterIndex]
        val nowMillis = remember(state.functions) { System.currentTimeMillis() }
        val supportedCount = state.functions.count { it.support.isVisibleAsSupported }
        val filtered = filterFunctions(state.functions, selectedFilter, query, state.favoriteKeys)
        CatalogScreen(
            status = state.functionStatus,
            detail = state.functionDetail,
            title = "Поддерживаемые функции",
            subtitle = "Только чтение: известные значения расшифрованы, исходный raw сохранён. Управление функциями отсутствует.",
            totalCount = state.functions.size,
            supportedCount = supportedCount,
            displayedCount = filtered.size,
            emptyText = "По выбранному фильтру функции не найдены.",
            rows = filtered.chunked(2),
            controls = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatalogSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Название, API name, ID или значение",
                    )
                    CatalogFilterRow(
                        labels = CatalogListFilter.entries.map(CatalogListFilter::title),
                        selectedIndex = selectedFilterIndex,
                        onSelected = { selectedFilterIndex = it },
                    )
                }
            },
        ) { row ->
            TwoColumnRow(row) { function ->
                FunctionCard(
                    function = function,
                    nowMillis = nowMillis,
                    isFavorite = function.favoriteKey in state.favoriteKeys,
                    onFavoriteToggle = { onFavoriteToggle(function.favoriteKey) },
                    onClick = { expandedId = function.id },
                )
            }
        }
        state.functions.firstOrNull { it.id == expandedId }?.let { function ->
            FullscreenValueDialog(
                title = function.title,
                apiName = function.apiName,
                idText = "id ${function.id}",
                value = function.value,
                sourceLabel = function.source.label,
                modeLabel = "ПОДРОБНОЕ ЗНАЧЕНИЕ",
                isFavorite = function.favoriteKey in state.favoriteKeys,
                onFavoriteToggle = { onFavoriteToggle(function.favoriteKey) },
                onDismiss = { expandedId = null },
            ) {
                FunctionDetails(function, nowMillis)
            }
        }
    }

    @Composable
    private fun FunctionCard(
        function: VehicleFunctionRecord,
        nowMillis: Long,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
    ) {
        DataCard(
            title = function.title,
            apiName = function.apiName,
            id = function.id,
            value = function.value,
            sourceLabel = function.source.label,
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            FunctionDetails(function, nowMillis)
        }
    }

    @Composable
    private fun FunctionDetails(function: VehicleFunctionRecord, nowMillis: Long) {
        ValueLine("Обновлено", formatUpdateTime(function.updatedAtMillis, nowMillis))
        if (function.supportedValues.isNotBlank()) {
            ValueLine("Допустимые raw", function.supportedValues)
        }
        if (function.zones.isNotBlank()) ValueLine("Зоны raw", function.zones)
        if (function.error.isNotBlank()) ValueLine("Примечание", function.error)
    }
}
