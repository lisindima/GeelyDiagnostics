package com.geelydiagnostics.app

import androidx.compose.runtime.Composable

internal object SensorsTab {

    @Composable
    fun Content(state: AppUiState) {
        val supported = state.sensors
            .filter { it.support.isVisibleAsSupported }
            .sortedBy(SensorRecord::title)
        CatalogScreen(
            status = state.sensorStatus,
            detail = state.sensorDetail,
            title = "Live Data",
            subtitle = "Показаны только сенсоры, поддержку которых подтвердила текущая ГУ. Значения raw, без неподтверждённых единиц измерения.",
            totalCount = state.sensors.size,
            supportedCount = supported.size,
            emptyText = "Поддерживаемые сенсоры пока не найдены.",
            rows = supported.chunked(2),
        ) { row ->
            TwoColumnRow(row) { sensor -> SensorCard(sensor) }
        }
    }

    @Composable
    private fun SensorCard(sensor: SensorRecord) {
        DataCard(
            title = sensor.title,
            apiName = sensor.apiName,
            id = sensor.id,
            support = sensor.support,
        ) {
            ValueLine("Значение", sensor.value)
            ValueLine("Тип", sensor.valueKind)
            if (sensor.error.isNotBlank()) ValueLine("Ошибка", sensor.error)
        }
    }
}
