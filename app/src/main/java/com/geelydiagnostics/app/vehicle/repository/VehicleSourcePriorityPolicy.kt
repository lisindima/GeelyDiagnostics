package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.*

/** Health first; within the same health tier source provenance wins over callback frequency. */
internal object VehicleSourcePriorityPolicy {
    fun comparator(nowMillis: Long): Comparator<CarPropertySnapshot> =
        compareBy<CarPropertySnapshot> { health(it, nowMillis) }
            .thenBy { priority(it) }
            .thenBy { it.receivedAtMillis }
            .thenBy { it.source.name }
            .thenBy { it.sourceSignalId }

    private fun health(value: CarPropertySnapshot, nowMillis: Long): Int {
        if (value.status != VehiclePropertyStatus.AVAILABLE || value.rawValue == null) return 0
        if (!value.decoded || value.value == null) return 1
        val timeout = value.expectedUpdateIntervalMillis
        // ON_CHANGE and manually refreshed readings do not expire just because they stay unchanged.
        val stale = value.autoUpdates && timeout != null && nowMillis - value.receivedAtMillis > timeout
        return if (stale) 2 else 3
    }

    private fun priority(value: CarPropertySnapshot): Int = if (!value.decoded) 0 else when (value.mappingOrigin) {
        VehicleMappingOrigin.PROFILE -> 300
        VehicleMappingOrigin.ECARX -> 200
        VehicleMappingOrigin.AOSP -> 100
        VehicleMappingOrigin.NONE -> 0
    }
}
