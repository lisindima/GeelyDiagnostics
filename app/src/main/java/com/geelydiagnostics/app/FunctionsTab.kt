package com.geelydiagnostics.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal object FunctionsTab {

    @Composable
    fun Content(state: AppUiState) {
        var expandedId by rememberSaveable { mutableStateOf<Int?>(null) }
        val supported = state.functions
            .filter { it.support.isVisibleAsSupported }
            .sortedBy(VehicleFunctionRecord::title)
        CatalogScreen(
            status = state.functionStatus,
            detail = state.functionDetail,
            title = "Поддерживаемые функции",
            subtitle = "Только чтение: известные значения расшифрованы, исходный raw сохранён. Управление функциями отсутствует.",
            totalCount = state.functions.size,
            supportedCount = supported.size,
            emptyText = "Поддерживаемые функции пока не найдены.",
            rows = supported.chunked(2),
        ) { row ->
            TwoColumnRow(row) { function ->
                FunctionCard(function, onClick = { expandedId = function.id })
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
                onDismiss = { expandedId = null },
            ) {
                FunctionDetails(function)
            }
        }
    }

    @Composable
    private fun FunctionCard(function: VehicleFunctionRecord, onClick: () -> Unit) {
        DataCard(
            title = function.title,
            apiName = function.apiName,
            id = function.id,
            value = function.value,
            sourceLabel = function.source.label,
            onClick = onClick,
        ) {
            FunctionDetails(function)
        }
    }

    @Composable
    private fun FunctionDetails(function: VehicleFunctionRecord) {
        if (function.supportedValues.isNotBlank()) {
            ValueLine("Допустимые raw", function.supportedValues)
        }
        if (function.zones.isNotBlank()) ValueLine("Зоны raw", function.zones)
        if (function.error.isNotBlank()) ValueLine("Примечание", function.error)
    }
}
