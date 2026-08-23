package com.geelydiagnostics.app

import java.util.Locale

internal enum class SensorValueFilter(val title: String) {
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

internal fun filterSensors(
    records: List<SensorRecord>,
    valueFilter: SensorValueFilter,
    query: String,
    favoriteKeys: Set<String>,
): List<SensorRecord> = records.asSequence()
    .filter { record ->
        when (valueFilter) {
            SensorValueFilter.ALL -> record.support.isVisibleAsSupported
            SensorValueFilter.DECODED -> record.support.isVisibleAsSupported && record.isDecoded
            SensorValueFilter.RAW -> record.support.isVisibleAsSupported && !record.isDecoded
            SensorValueFilter.CHANGED -> record.support.isVisibleAsSupported && record.changedSinceScan
            SensorValueFilter.ERRORS -> record.support == ApiSupportStatus.ERROR ||
                record.error.isNotBlank() || record.sourceReadings.any { it.error.isNotBlank() }
            SensorValueFilter.FAVORITES ->
                record.support.isVisibleAsSupported && record.matchesFavorite(favoriteKeys)
        }
    }
    .filter { record -> record.matchesQuery(query) }
    .sortedWith(compareBy<SensorRecord> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id })
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

private val SensorRecord.isDecoded: Boolean
    get() = decoded == true

private fun SensorRecord.matchesQuery(query: String): Boolean = matchesAllTokens(
    query,
    title,
    apiName,
    id.toString(),
    "0x${id.toUInt().toString(16)}",
    value.display,
    value.raw,
    source.label,
    sourceProfile.orEmpty(),
    propertyId?.toString().orEmpty(),
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

internal fun SensorRecord.matchesFavorite(favoriteKeys: Set<String>): Boolean =
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
