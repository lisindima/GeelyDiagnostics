package com.geelydiagnostics.app

import androidx.compose.runtime.Composable

internal object FunctionsTab {

    @Composable
    fun Content(state: AppUiState) {
        val supported = state.functions
            .filter { it.support.isVisibleAsSupported }
            .sortedBy(VehicleFunctionRecord::title)
        CatalogScreen(
            status = state.functionStatus,
            detail = state.functionDetail,
            title = "Поддерживаемые функции",
            subtitle = "Только проверка поддержки и чтение текущих raw-значений. Управление функциями в приложении отсутствует.",
            totalCount = state.functions.size,
            supportedCount = supported.size,
            emptyText = "Поддерживаемые функции пока не найдены.",
            rows = supported.chunked(2),
        ) { row ->
            TwoColumnRow(row) { function -> FunctionCard(function) }
        }
    }

    @Composable
    private fun FunctionCard(function: VehicleFunctionRecord) {
        DataCard(
            title = function.title,
            apiName = function.apiName,
            id = function.id,
            support = function.support,
        ) {
            ValueLine("Текущее raw", function.value.ifBlank { "не получено" })
            if (function.supportedValues.isNotBlank()) {
                ValueLine("Допустимые raw", function.supportedValues)
            }
            if (function.zones.isNotBlank()) ValueLine("Зоны raw", function.zones)
            if (function.error.isNotBlank()) ValueLine("Примечание", function.error)
        }
    }
}
