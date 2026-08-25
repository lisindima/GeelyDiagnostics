package com.geelydiagnostics.app.ui.catalog

import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import com.geelydiagnostics.app.vehicle.property.legacyFavoriteKeys
import java.util.Locale

internal enum class ParameterValueFilter(val title: String) {
    ALL("Все значения"),
    DECODED("Расшифровано"),
    RAW("Без расшифровки"),
    CHANGED("Изменились"),
    ERRORS("Ошибки"),
    FAVORITES("Избранное"),
}

internal fun filterParameters(
    records: List<VehicleParameter>,
    valueFilter: ParameterValueFilter,
    query: String,
    favoriteKeys: Set<String>,
): List<VehicleParameter> = records.asSequence()
    .filter { record ->
        when (valueFilter) {
            ParameterValueFilter.ALL -> record.status == VehiclePropertyStatus.AVAILABLE
            ParameterValueFilter.DECODED -> record.status == VehiclePropertyStatus.AVAILABLE && record.decoded
            ParameterValueFilter.RAW -> record.status == VehiclePropertyStatus.AVAILABLE && !record.decoded
            ParameterValueFilter.CHANGED ->
                record.status == VehiclePropertyStatus.AVAILABLE && record.changedSinceScan
            ParameterValueFilter.ERRORS -> record.status == VehiclePropertyStatus.ERROR ||
                record.error.isNotBlank() || record.sourceReadings.any { it.error.isNotBlank() }
            ParameterValueFilter.FAVORITES ->
                record.status == VehiclePropertyStatus.AVAILABLE && record.matchesFavorite(favoriteKeys)
        }
    }
    .filter { record -> record.matchesQuery(query) }
    .sortedWith(
        compareBy<VehicleParameter> { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.propertyId?.rawValue ?: it.sourceReadings.first().signalId },
    )
    .toList()

private fun VehicleParameter.matchesQuery(query: String): Boolean = matchesAllTokens(
    query,
    title,
    value.display,
    value.raw,
    propertyId?.rawValue?.toString().orEmpty(),
    sourceReadings.joinToString(" ") { reading ->
        listOf(
            reading.source.label,
            reading.signalId.toString(),
            "0x${reading.signalId.toUInt().toString(16)}",
            reading.signalName,
            reading.value.display,
            reading.value.raw,
            reading.profile.orEmpty(),
            reading.modeLabel.orEmpty(),
            reading.details.joinToString(" ") { "${it.label} ${it.value}" },
        ).joinToString(" ")
    },
)

internal fun VehicleParameter.matchesFavorite(favoriteKeys: Set<String>): Boolean =
    favoriteKey in favoriteKeys || legacyFavoriteKeys.any { it in favoriteKeys }

private fun matchesAllTokens(query: String, vararg values: String): Boolean {
    val tokens = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
    if (tokens.isEmpty()) return true
    val haystack = values.joinToString(" ").lowercase(Locale.ROOT)
    return tokens.all(haystack::contains)
}
