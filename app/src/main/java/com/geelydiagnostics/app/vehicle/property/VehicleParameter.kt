package com.geelydiagnostics.app.vehicle.property

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
    val error: String = "",
    val profile: String? = null,
    val areaId: Int = 0,
    val updatedAtMillis: Long? = null,
    val sourceTimestampNanos: Long? = null,
    val autoUpdates: Boolean = false,
    val decoded: Boolean = false,
)

/**
 * Parameter consumed by the application. The stable property id is the primary identity; source
 * signal ids are retained only in [sourceReadings]. Unknown signals deliberately have no id.
 */
data class VehicleParameter(
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
)

data class VehicleParameterSample(
    val timestampMillis: Long,
    val value: Double,
)

internal val VehicleParameter.primaryReading: VehicleSourceReading
    get() = sourceReadings.first()

internal val VehicleParameter.favoriteKey: String
    get() = propertyId?.let { "property:${it.rawValue}:$areaId" }
        ?: primaryReading.let { "signal:${it.source.name}:${it.signalId}:$areaId" }

internal val VehicleSourceReading.legacyFavoriteKey: String
    get() = "sensor:${source.name}:$signalId:$areaId"
