package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.ApiSupportStatus
import com.geelydiagnostics.app.ApiValue
import com.geelydiagnostics.app.ParameterSourceReading
import com.geelydiagnostics.app.SensorRecord
import com.geelydiagnostics.app.VehicleDataSource
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertyPresentations

internal object NormalizedParameterMerger {
    fun merge(records: List<SensorRecord>): List<SensorRecord> {
        val mapped = records.filter { it.propertyId != null }
            .groupBy { it.propertyId to it.areaId }
            .values
            .map(::mergePropertySources)
        val unknown = records.filter { it.propertyId == null }.map { record ->
            record.copy(sourceReadings = listOf(record.toSourceReading()))
        }
        return mapped + unknown
    }

    private fun mergePropertySources(records: List<SensorRecord>): SensorRecord {
        val primary = records.firstOrNull {
            it.source == VehicleDataSource.VHAL && it.support.isReadable &&
                it.value != ApiValue.unavailable && it.decoded == true
        } ?: records.firstOrNull {
            it.support.isReadable && it.value != ApiValue.unavailable && it.decoded == true
        } ?: records.firstOrNull { it.support.isReadable && it.value != ApiValue.unavailable }
            ?: records.first()
        val readings = records
            .sortedWith(compareBy<SensorRecord> { it.source != VehicleDataSource.VHAL }.thenBy { it.id })
            .map { it.toSourceReading() }
        val propertyId = requireNotNull(primary.propertyId)
        return primary.copy(
            title = CarPropertyPresentations.get(CarPropertyId(propertyId)).title,
            propertyId = propertyId,
            sourceProfile = readings.firstOrNull { it.source == VehicleDataSource.VHAL }?.profile,
            updatedAtMillis = primary.updatedAtMillis,
            changedSinceScan = primary.changedSinceScan,
            autoUpdates = primary.autoUpdates,
            chartable = primary.chartable,
            decoded = primary.decoded,
            sourceReadings = readings,
        )
    }

    private fun SensorRecord.toSourceReading() = ParameterSourceReading(
        source = source,
        signalId = id,
        signalName = apiName,
        value = value,
        support = support,
        error = error,
        profile = sourceProfile,
        areaId = areaId,
        updatedAtMillis = updatedAtMillis,
        sourceTimestampNanos = sourceTimestampNanos,
        autoUpdates = autoUpdates,
        decoded = decoded == true,
    )

    private val ApiSupportStatus.isReadable: Boolean
        get() = this == ApiSupportStatus.ACTIVE || this == ApiSupportStatus.NOT_ACTIVE
}
