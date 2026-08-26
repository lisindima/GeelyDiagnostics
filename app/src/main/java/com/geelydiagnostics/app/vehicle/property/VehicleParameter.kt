package com.geelydiagnostics.app.vehicle.property

import com.geelydiagnostics.app.vehicle.mapping.ReadTransform

/** Text prepared for presentation while retaining the exact value returned by the source. */
data class VehicleDisplayValue(
    val display: String,
    val raw: String,
) {
    companion object {
        fun raw(value: String): VehicleDisplayValue = VehicleDisplayValue(display = value, raw = value)
        val unavailable = raw("—")
    }
}

/** One low-level reading contributing to a normalized vehicle parameter. */
data class VehicleSourceReading(
    val source: VehiclePropertySource,
    val signalId: Int,
    val signalName: String,
    val value: VehicleDisplayValue,
    val status: VehiclePropertyStatus,
    val description: String? = null,
    val error: String = "",
    val profile: String? = null,
    val areaId: Int = 0,
    val updatedAtMillis: Long? = null,
    val sourceTimestampNanos: Long? = null,
    val autoUpdates: Boolean = false,
    val decoded: Boolean = false,
    val modeLabel: String? = null,
    val details: List<VehiclePropertyDetail> = emptyList(),
    val normalizedValue: CarValue? = null,
    val backend: String? = null,
    val readTransform: ReadTransform? = null,
    val mappingOrigin: VehicleMappingOrigin = VehicleMappingOrigin.NONE,
    val unit: String? = null,
)

/**
 * Parameter consumed by the application. The normalized property id is the primary identity when
 * several sources can be merged; source-specific AOSP and unknown readings deliberately have no id.
 */
data class VehicleParameter(
    val section: VehicleDataSection = VehicleDataSection.PARAMETER,
    val propertyId: CarPropertyId?,
    val areaId: Int,
    val title: String,
    val value: VehicleDisplayValue,
    val valueKind: String,
    val status: VehiclePropertyStatus,
    val error: String = "",
    val updatedAtMillis: Long? = null,
    val sourceTimestampNanos: Long? = null,
    val expectedUpdateIntervalMillis: Long? = null,
    val changedSinceScan: Boolean = false,
    val autoUpdates: Boolean = false,
    val chartable: Boolean = false,
    val decoded: Boolean = false,
    val sourceReadings: List<VehicleSourceReading>,
    val normalizedValue: CarValue? = null,
)

data class VehicleParameterSample(
    val timestampMillis: Long,
    val value: Double,
)

internal val VehicleParameter.primaryReading: VehicleSourceReading
    get() = sourceReadings.first()

internal val VehicleParameter.favoriteKey: String
    get() = when (section) {
        VehicleDataSection.PARAMETER -> propertyId?.let { "property:${it.rawValue}:$areaId" }
            ?: primaryReading.let { "signal:${it.source.name}:${it.signalId}:$areaId" }
        VehicleDataSection.VEHICLE_INFO -> propertyId?.let { "vehicle:property:${it.rawValue}:$areaId" }
            ?: primaryReading.let { "vehicle:signal:${it.source.name}:${it.signalId}:$areaId" }
        VehicleDataSection.CAPABILITY -> propertyId?.let { "function:property:${it.rawValue}:$areaId" }
            ?: primaryReading.let { "function:signal:${it.source.name}:${it.signalId}:$areaId" }
    }

internal val VehicleSourceReading.legacyFavoriteKey: String
    get() = "sensor:${source.name}:$signalId:$areaId"

internal val VehicleParameter.legacyFavoriteKeys: Set<String>
    get() = buildSet {
        sourceReadings.forEach { reading ->
            add(reading.legacyFavoriteKey)
            when (section) {
                VehicleDataSection.PARAMETER -> Unit
                VehicleDataSection.VEHICLE_INFO ->
                    add("vehicle:${reading.source.name}:${reading.signalId}")
                VehicleDataSection.CAPABILITY ->
                    add("function:${reading.source.name}:${reading.signalId}")
            }
        }
        if (section != VehicleDataSection.PARAMETER) {
            add(
                propertyId?.let { "property:${it.rawValue}:$areaId" }
                    ?: primaryReading.let { "signal:${it.source.name}:${it.signalId}:$areaId" },
            )
        }
    }
