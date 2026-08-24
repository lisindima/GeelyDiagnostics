package com.geelydiagnostics.app

import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import com.geelydiagnostics.app.vehicle.property.legacyFavoriteKey
import java.util.Locale

internal enum class ParameterValueFilter(val title: String) {
    ALL("Все значения"),
    DECODED("Нормализовано"),
    RAW("Без расшифровки"),
    CHANGED("Изменились"),
    ERRORS("Ошибки"),
    FAVORITES("Избранное"),
}

internal enum class CatalogListFilter(val title: String) {
    ALL("Все"),
    FAVORITES("Избранное"),
    ERRORS("Ошибки"),
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

internal fun filterVehicleInfo(
    records: List<VehicleInfoRecord>,
    filter: CatalogListFilter,
    query: String,
    favoriteKeys: Set<String>,
): List<VehicleInfoRecord> = records.asSequence()
    .filter { record ->
        when (filter) {
            CatalogListFilter.ALL -> record.support.isVisibleAsSupported
            CatalogListFilter.FAVORITES ->
                record.support.isVisibleAsSupported && record.favoriteKey in favoriteKeys
            CatalogListFilter.ERRORS -> record.support == ApiSupportStatus.ERROR || record.error.isNotBlank()
        }
    }
    .filter { record -> record.matchesQuery(query) }
    .sortedBy { it.title.lowercase(Locale.ROOT) }
    .toList()

internal fun filterFunctions(
    records: List<VehicleFunctionRecord>,
    filter: CatalogListFilter,
    query: String,
    favoriteKeys: Set<String>,
): List<VehicleFunctionRecord> = records.asSequence()
    .filter { record ->
        when (filter) {
            CatalogListFilter.ALL -> record.support.isVisibleAsSupported
            CatalogListFilter.FAVORITES ->
                record.support.isVisibleAsSupported && record.favoriteKey in favoriteKeys
            CatalogListFilter.ERRORS -> record.support == ApiSupportStatus.ERROR || record.error.isNotBlank()
        }
    }
    .filter { record -> record.matchesQuery(query) }
    .sortedBy { it.title.lowercase(Locale.ROOT) }
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
        ).joinToString(" ")
    },
)

internal fun VehicleParameter.matchesFavorite(favoriteKeys: Set<String>): Boolean =
    favoriteKey in favoriteKeys || sourceReadings.any { it.legacyFavoriteKey in favoriteKeys }

private fun VehicleInfoRecord.matchesQuery(query: String): Boolean = matchesAllTokens(
    query,
    title,
    apiName,
    id.toString(),
    value.display,
    value.raw,
    source.label,
)

private fun VehicleFunctionRecord.matchesQuery(query: String): Boolean = matchesAllTokens(
    query,
    title,
    apiName,
    id.toString(),
    value.display,
    value.raw,
    supportedValues,
    zones,
    source.label,
)

private fun matchesAllTokens(query: String, vararg values: String): Boolean {
    val tokens = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
    if (tokens.isEmpty()) return true
    val haystack = values.joinToString(" ").lowercase(Locale.ROOT)
    return tokens.all(haystack::contains)
}
