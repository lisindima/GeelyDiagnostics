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
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue

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
            title = "Возможности автомобиля",
            subtitle = "Возможности, о которых сообщает штатный каталог. Текущее значение показано, если API его возвращает.",
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
                idText = "ECARX ID ${function.id}",
                value = function.cardValue,
                sourceLabels = listOf(function.source.label),
                modeLabel = function.supportLabel.uppercase(),
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
            idLabel = "ECARX ID ${function.id}",
            value = function.cardValue,
            sourceLabels = listOf(function.source.label),
            modeLabel = function.supportLabel.uppercase(),
            modeIsHighlighted = function.support == ApiSupportStatus.ACTIVE,
            footerText = formatUpdateTime(function.updatedAtMillis, nowMillis),
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onClick = onClick,
        ) {
            FunctionMetadata(function)
        }
    }

    @Composable
    private fun FunctionDetails(function: VehicleFunctionRecord, nowMillis: Long) {
        ValueLine("Доступность", function.supportLabel)
        ValueLine("Обновлено", formatUpdateTime(function.updatedAtMillis, nowMillis))
        FunctionMetadata(function)
    }

    @Composable
    private fun FunctionMetadata(function: VehicleFunctionRecord) {
        if (function.supportedValues.isNotBlank()) {
            ValueLine("Допустимые raw", function.supportedValues)
        }
        if (function.zones.isNotBlank()) ValueLine("Зоны raw", function.zones)
        if (function.error.isNotBlank()) ValueLine("Примечание", function.error)
    }

    private val VehicleFunctionRecord.supportLabel: String
        get() = when (support) {
            ApiSupportStatus.ACTIVE -> "Доступна"
            ApiSupportStatus.NOT_ACTIVE -> "Поддерживается · неактивна"
            ApiSupportStatus.NOT_AVAILABLE -> "Не поддерживается"
            ApiSupportStatus.ERROR -> "Ошибка проверки"
            ApiSupportStatus.UNKNOWN -> "Состояние неизвестно"
        }

    private val VehicleFunctionRecord.cardValue: VehicleDisplayValue
        get() = if (value == VehicleDisplayValue.unavailable && support.isVisibleAsSupported) {
            VehicleDisplayValue(display = supportLabel, raw = value.raw)
        } else {
            value
        }
}
