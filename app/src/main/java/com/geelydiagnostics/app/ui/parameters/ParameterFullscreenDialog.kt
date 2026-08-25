package com.geelydiagnostics.app.ui.parameters

import androidx.compose.runtime.Composable
import com.geelydiagnostics.app.ui.catalog.formatUpdateTime
import com.geelydiagnostics.app.ui.catalog.isStale
import com.geelydiagnostics.app.ui.components.DescriptionBlock
import com.geelydiagnostics.app.ui.components.FullscreenValueDialog
import com.geelydiagnostics.app.ui.components.ParameterHistoryChart
import com.geelydiagnostics.app.ui.components.ValueLine
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehicleParameterSample

/** Complete fullscreen presentation for one vehicle parameter. */
@Composable
internal fun ParameterFullscreenDialog(
    parameter: VehicleParameter,
    history: List<VehicleParameterSample>,
    nowMillis: Long,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    FullscreenValueDialog(
        title = parameter.title,
        apiName = parameter.fieldName,
        idText = parameter.cardIdLabel,
        value = parameter.value,
        sourceLabels = parameter.sourceLabels,
        modeLabel = if (parameter.autoUpdates) {
            "АВТООБНОВЛЕНИЕ · ПОДПИСКА"
        } else {
            "РУЧНОЕ ОБНОВЛЕНИЕ"
        },
        isFavorite = isFavorite,
        onFavoriteToggle = onFavoriteToggle,
        onDismiss = onDismiss,
        chart = if (parameter.chartable) {
            {
                ParameterHistoryChart(
                    samples = history,
                    isLive = parameter.autoUpdates,
                )
            }
        } else {
            null
        },
    ) {
        ParameterDetails(parameter, nowMillis)
    }
}

@Composable
private fun ParameterDetails(parameter: VehicleParameter, nowMillis: Long) {
    parameter.sourceReadings
        .mapNotNull { reading -> reading.description?.let { reading.badgeLabel to it } }
        .distinct()
        .forEach { (source, description) ->
            DescriptionBlock("Описание $source", description)
        }
    ValueLine("Тип", parameter.valueKind)
    ValueLine(
        "Обновлено",
        formatUpdateTime(parameter.updatedAtMillis, nowMillis) +
            if (parameter.isStale(nowMillis)) " · УСТАРЕЛО" else "",
    )
    ValueLine(
        "Обновление",
        if (parameter.autoUpdates) "автоматически по подписке" else "только вручную",
    )
    if (parameter.changedSinceScan) ValueLine("Состояние", "изменилось после сканирования")
    ValueLine(
        "Расшифровка",
        when {
            !parameter.decoded -> "нет — показано исходное значение"
            parameter.propertyId != null -> "расшифровано и объединено с общим параметром"
            parameter.sourceReadings.any { it.profile == "AOSP" } -> "по стандартным правилам AOSP"
            else -> "по выбранному профилю автомобиля"
        },
    )
    parameter.sourceReadings.forEach { reading ->
        val label = reading.badgeLabel
        ValueLine("$label · сигнал", reading.signalLabel)
        ValueLine("$label · raw", reading.value.raw)
        reading.sourceTimestampNanos?.let {
            ValueLine("$label · timestamp", "$it нс от запуска системы")
        }
        if (reading.error.isNotBlank()) ValueLine("$label · ошибка", reading.error)
    }
}
