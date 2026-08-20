package com.geelydiagnostics.app

import androidx.compose.runtime.Composable

internal object VehicleTab {

    @Composable
    fun Content(state: AppUiState) {
        val supported = state.vehicleInfo
            .filter { it.support.isVisibleAsSupported }
            .sortedBy(VehicleInfoRecord::title)
        CatalogScreen(
            status = state.carInfoStatus,
            detail = state.carInfoDetail,
            title = "Автомобиль и комплектация",
            subtitle = "Данные читаются из ICarInfo самой машины; неподдерживаемые поля скрыты.",
            totalCount = state.vehicleInfo.size,
            supportedCount = supported.size,
            emptyText = "Поддерживаемые сведения об автомобиле пока не найдены.",
            rows = supported.chunked(2),
        ) { row ->
            TwoColumnRow(row) { item -> VehicleInfoCard(item) }
        }
    }

    @Composable
    private fun VehicleInfoCard(item: VehicleInfoRecord) {
        DataCard(
            title = item.title,
            apiName = item.apiName,
            id = item.id,
            value = item.value,
            sourceLabel = item.source.label,
        ) {
            if (item.error.isNotBlank()) ValueLine("Ошибка", item.error)
        }
    }
}
