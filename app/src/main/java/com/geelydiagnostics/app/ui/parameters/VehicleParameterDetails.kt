package com.geelydiagnostics.app.ui.parameters

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.geelydiagnostics.app.ui.catalog.formatUpdateTime
import com.geelydiagnostics.app.ui.components.DescriptionBlock
import com.geelydiagnostics.app.ui.components.ValueLine
import com.geelydiagnostics.app.vehicle.property.VehicleParameter

/** Source-aware technical details shared by every unified catalog section. */
@Composable
internal fun VehicleParameterDetails(
    parameter: VehicleParameter,
    nowMillis: Long,
    updatedSuffix: String = "",
    overviewContent: @Composable () -> Unit = {},
) {
    parameter.sourceReadings
        .mapNotNull { reading -> reading.description?.let { reading.badgeLabel to it } }
        .distinct()
        .forEach { (source, description) ->
            DescriptionBlock("Описание $source", description)
        }
    ValueLine("Тип", parameter.valueKind)
    ValueLine(
        "Обновлено",
        formatUpdateTime(parameter.updatedAtMillis, nowMillis) + updatedSuffix,
    )
    overviewContent()
    parameter.sourceReadings.forEach { reading ->
        val label = reading.badgeLabel
        ValueLine("$label · API", reading.signalLabel)
        ValueLine("$label · raw", reading.value.raw)
        reading.details.forEach { detail ->
            ValueLine("$label · ${detail.label}", detail.value)
        }
        reading.sourceTimestampNanos?.let {
            ValueLine("$label · timestamp", "$it нс от запуска системы")
        }
        if (reading.error.isNotBlank()) ValueLine("$label · ошибка", reading.error)
    }
}

@Composable
internal fun VehicleParameterErrors(parameter: VehicleParameter) {
    parameter.sourceReadings.filter { it.error.isNotBlank() }.forEach { reading ->
        Text(
            text = "${reading.source.label}: ${reading.error}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}
