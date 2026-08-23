package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertyKey
import com.geelydiagnostics.app.vehicle.property.CarPropertyPresentations
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleSourceReading
import com.geelydiagnostics.app.vehicle.property.key

/** Single source of truth for normalized parameters and deliberately unknown source signals. */
internal class UnifiedParameterCache {
    private val normalized = linkedMapOf<NormalizedKey, LinkedHashMap<CarPropertyKey, CarPropertySnapshot>>()
    private val unknown = linkedMapOf<CarPropertyKey, CarPropertySnapshot>()
    private val changed = linkedSetOf<CarPropertyKey>()

    fun replaceSource(source: VehiclePropertySource, values: List<CarPropertySnapshot>) {
        normalized.values.forEach { candidates -> candidates.keys.removeAll { it.source == source } }
        normalized.entries.removeAll { it.value.isEmpty() }
        unknown.keys.removeAll { it.source == source }
        changed.removeAll { it.source == source }
        values.forEach(::put)
    }

    fun update(value: CarPropertySnapshot): Boolean {
        val previous = put(value)
        val didChange = previous != null && previous.rawValue?.text != value.rawValue?.text
        if (didChange) changed += value.key
        return didChange
    }

    fun parameters(): List<VehicleParameter> {
        val normalizedParameters = normalized.values.map { merge(it.values.toList()) }
        val unknownParameters = unknown.values.map { merge(listOf(it)) }
        return (normalizedParameters + unknownParameters).sortedWith(
            compareBy<VehicleParameter> { it.title.lowercase() }
                .thenBy { it.propertyId?.rawValue ?: Int.MAX_VALUE }
                .thenBy(VehicleParameter::areaId),
        )
    }

    fun clear() {
        normalized.clear()
        unknown.clear()
        changed.clear()
    }

    private fun put(value: CarPropertySnapshot): CarPropertySnapshot? {
        val propertyId = value.propertyId
        return if (propertyId == null) {
            unknown.put(value.key, value)
        } else {
            normalized.getOrPut(NormalizedKey(propertyId, value.areaId)) { linkedMapOf() }
                .put(value.key, value)
        }
    }

    private fun merge(values: List<CarPropertySnapshot>): VehicleParameter {
        val primary = values.firstOrNull { it.source == VehiclePropertySource.VHAL && it.readableDecoded }
            ?: values.firstOrNull { it.readableDecoded }
            ?: values.firstOrNull { it.status == VehiclePropertyStatus.AVAILABLE && it.rawValue != null }
            ?: values.first()
        val readings = values
            .sortedWith(
                compareBy<CarPropertySnapshot> { it.key != primary.key }
                    .thenBy { it.source != VehiclePropertySource.VHAL }
                    .thenBy(CarPropertySnapshot::sourceSignalId),
            )
            .map(CarPropertySnapshot::toSourceReading)
        val presentation = primary.propertyId?.let(CarPropertyPresentations::get)
        val numeric = primary.value is CarValue.IntValue || primary.value is CarValue.FloatValue
        return VehicleParameter(
            propertyId = primary.propertyId,
            areaId = primary.areaId,
            title = presentation?.title ?: primary.sourceTitle
                ?: "Неизвестный ${primary.source.label}-сигнал ${primary.sourceSignalId.identity(primary.source)}",
            value = VehicleDisplayValue(primary.displayValue, primary.rawValue?.text ?: "—"),
            valueKind = primary.valueKind,
            status = primary.status,
            error = values.map(CarPropertySnapshot::error).filter(String::isNotBlank).distinct()
                .joinToString("; "),
            updatedAtMillis = primary.receivedAtMillis,
            sourceTimestampNanos = primary.sourceTimestampNanos,
            expectedUpdateIntervalMillis = primary.expectedUpdateIntervalMillis,
            changedSinceScan = values.any { it.key in changed },
            autoUpdates = primary.autoUpdates,
            chartable = numeric && (primary.autoUpdates || presentation?.unit != null),
            decoded = primary.readableDecoded,
            sourceReadings = readings,
        )
    }

    private val CarPropertySnapshot.readableDecoded: Boolean
        get() = propertyId != null && status == VehiclePropertyStatus.AVAILABLE && rawValue != null

    private fun CarPropertySnapshot.toSourceReading() = VehicleSourceReading(
        source = source,
        signalId = sourceSignalId,
        signalName = sourceSignalName,
        value = VehicleDisplayValue(displayValue, rawValue?.text ?: "—"),
        status = status,
        error = error,
        profile = profileKey,
        areaId = areaId,
        updatedAtMillis = receivedAtMillis,
        sourceTimestampNanos = sourceTimestampNanos,
        autoUpdates = autoUpdates,
        decoded = readableDecoded,
    )

    private data class NormalizedKey(val propertyId: CarPropertyId, val areaId: Int)
}

private fun Int.identity(source: VehiclePropertySource): String = if (source == VehiclePropertySource.VHAL) {
    "0x${toUInt().toString(16).padStart(8, '0')}"
} else {
    toString()
}
