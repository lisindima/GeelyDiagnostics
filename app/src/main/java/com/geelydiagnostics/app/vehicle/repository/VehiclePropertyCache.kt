package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.CarPropertyKey
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.key

/** In-memory cache keyed by source signal and area; unknown properties are first-class entries. */
internal class VehiclePropertyCache {
    private val values = linkedMapOf<CarPropertyKey, CarPropertySnapshot>()
    private val changed = linkedSetOf<CarPropertyKey>()

    fun replace(snapshot: List<CarPropertySnapshot>) {
        values.clear()
        snapshot.forEach { value -> values[value.key] = value }
        changed.clear()
    }

    /** Returns true only when the raw source value differs from the cached value. */
    fun update(value: CarPropertySnapshot): Boolean {
        val previous = values.put(value.key, value)
        val didChange = previous != null && previous.rawValue?.text != value.rawValue?.text
        if (didChange) changed += value.key
        return didChange
    }

    fun values(): List<CarPropertySnapshot> = values.values.toList()

    fun changedSinceSnapshot(key: CarPropertyKey): Boolean = key in changed

    fun clear() {
        values.clear()
        changed.clear()
    }
}
