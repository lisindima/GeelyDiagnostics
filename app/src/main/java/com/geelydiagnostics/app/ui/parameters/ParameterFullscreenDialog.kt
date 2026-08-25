package com.geelydiagnostics.app.ui.parameters

import androidx.compose.runtime.Composable
import com.geelydiagnostics.app.ui.catalog.isStale
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
    VehicleParameterDetails(
        parameter = parameter,
        nowMillis = nowMillis,
        updatedSuffix = if (parameter.isStale(nowMillis)) " · УСТАРЕЛО" else "",
    ) {
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
                parameter.sourceReadings.any { it.profile == "AOSP" } ->
                    "по стандартным правилам AOSP"
                else -> "по выбранному профилю автомобиля"
            },
        )
    }
}
