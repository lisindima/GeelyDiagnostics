package com.geelydiagnostics.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal object VehicleTab {

    @Composable
    fun Content(state: AppUiState) {
        var expandedId by rememberSaveable { mutableStateOf<Int?>(null) }
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
            TwoColumnRow(row) { item ->
                VehicleInfoCard(item, onClick = { expandedId = item.id })
            }
        }
        state.vehicleInfo.firstOrNull { it.id == expandedId }?.let { item ->
            FullscreenValueDialog(
                title = item.title,
                apiName = item.apiName,
                idText = "id ${item.id}",
                value = item.value,
                sourceLabel = item.source.label,
                modeLabel = "ПОДРОБНОЕ ЗНАЧЕНИЕ",
                onDismiss = { expandedId = null },
            ) {
                VehicleInfoDetails(item)
            }
        }
    }

    @Composable
    private fun VehicleInfoCard(item: VehicleInfoRecord, onClick: () -> Unit) {
        DataCard(
            title = item.title,
            apiName = item.apiName,
            id = item.id,
            value = item.value,
            sourceLabel = item.source.label,
            onClick = onClick,
        ) {
            VehicleInfoDetails(item)
        }
    }

    @Composable
    private fun VehicleInfoDetails(item: VehicleInfoRecord) {
        if (item.error.isNotBlank()) ValueLine("Ошибка", item.error)
    }
}
