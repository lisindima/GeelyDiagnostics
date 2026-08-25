package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertyKey
import com.geelydiagnostics.app.vehicle.property.CarPropertyPresentations
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleSourceReading
import com.geelydiagnostics.app.vehicle.property.key

/** Single source of truth for normalized parameters and standalone source-specific readings. */
internal class UnifiedParameterCache {
    private val normalized = linkedMapOf<NormalizedKey, LinkedHashMap<CarPropertyKey, CarPropertySnapshot>>()
    private val standalone = linkedMapOf<CarPropertyKey, CarPropertySnapshot>()
    private val changed = linkedSetOf<CarPropertyKey>()

    fun replaceSource(
        source: VehiclePropertySource,
        values: List<CarPropertySnapshot>,
        section: VehicleDataSection? = null,
    ) {
        fun CarPropertyKey.matchesScope(): Boolean =
            this.source == source && (section == null || this.section == section)
        normalized.values.forEach { candidates -> candidates.keys.removeAll { it.matchesScope() } }
        normalized.entries.removeAll { it.value.isEmpty() }
        standalone.keys.removeAll { it.matchesScope() }
        changed.removeAll { it.matchesScope() }
        require(section == null || values.all { it.section == section }) {
            "Snapshot section does not match replacement scope $section"
        }
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
        val standaloneParameters = standalone.values.map { merge(listOf(it)) }
        return (normalizedParameters + standaloneParameters).sortedWith(
            compareBy<VehicleParameter> { it.title.lowercase() }
                .thenBy { it.propertyId?.rawValue ?: Int.MAX_VALUE }
                .thenBy(VehicleParameter::areaId),
        )
    }

    fun clear() {
        normalized.clear()
        standalone.clear()
        changed.clear()
    }

    private fun put(value: CarPropertySnapshot): CarPropertySnapshot? {
        val propertyId = value.propertyId
        return if (propertyId == null) {
            standalone.put(value.key, value)
        } else {
            normalized.getOrPut(NormalizedKey(value.section, propertyId, value.areaId)) { linkedMapOf() }
                .put(value.key, value)
        }
    }

    private fun merge(values: List<CarPropertySnapshot>): VehicleParameter {
        val primary = values.asSequence()
            .filter { it.readableDecoded }
            .maxWithOrNull(primaryReadingComparator)
            ?: values.asSequence()
                .filter { it.status == VehiclePropertyStatus.AVAILABLE && it.rawValue != null }
                .maxWithOrNull(primaryReadingComparator)
            ?: values.maxWithOrNull(primaryReadingComparator)
            ?: error("Cannot merge an empty parameter group")
        val readings = values
            .sortedWith(
                compareBy<CarPropertySnapshot> { it.key != primary.key }
                    .thenBy { it.source != VehiclePropertySource.VHAL }
                    .thenBy(CarPropertySnapshot::sourceSignalId),
            )
            .map { it.toSourceReading() }
        val presentation = primary.propertyId?.let(CarPropertyPresentations::get)
        val numeric = primary.value is CarValue.IntValue || primary.value is CarValue.FloatValue
        return VehicleParameter(
            section = primary.section,
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
        get() = decoded && status == VehiclePropertyStatus.AVAILABLE && rawValue != null

    private fun CarPropertySnapshot.toSourceReading() = VehicleSourceReading(
        source = source,
        signalId = sourceSignalId,
        signalName = sourceSignalName,
        value = VehicleDisplayValue(displayValue, rawValue?.text ?: "—"),
        status = status,
        description = sourceDescription,
        error = error,
        profile = profileKey,
        areaId = areaId,
        updatedAtMillis = receivedAtMillis,
        sourceTimestampNanos = sourceTimestampNanos,
        autoUpdates = autoUpdates,
        decoded = readableDecoded,
        modeLabel = modeLabel,
        details = details,
    )

    private data class NormalizedKey(
        val section: VehicleDataSection,
        val propertyId: CarPropertyId,
        val areaId: Int,
    )

    companion object {
        private val primaryReadingComparator = compareBy<CarPropertySnapshot> {
            it.receivedAtMillis
        }.thenBy {
            it.sourceTimestampNanos ?: Long.MIN_VALUE
        }.thenBy {
            it.source == VehiclePropertySource.VHAL
        }
    }
}

private fun Int.identity(source: VehiclePropertySource): String = if (source == VehiclePropertySource.VHAL) {
    "0x${toUInt().toString(16).padStart(8, '0')}"
} else {
    toString()
}
