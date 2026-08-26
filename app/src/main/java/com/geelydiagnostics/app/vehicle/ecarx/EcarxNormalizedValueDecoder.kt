package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.vehicle.property.*

/** Applies only explicit ECARX mappings, then formats the normalized value using the common catalog. */
internal class EcarxNormalizedValueDecoder(catalog: CarPropertyCatalog) {
    private val decoder = MappedPropertyDecoder(catalog)

    fun decode(apiName: String, signalId: Int, raw: RawVehicleValue, receivedAtMillis: Long): CarPropertySnapshot? {
        val mapping = EcarxNormalizedPropertyRegistry.sensorMapping(apiName, signalId) ?: return null
        return decoder.decode(
            mapping, raw, signalId, apiName, 0, null, null, receivedAtMillis, false,
        ).copy(source = VehiclePropertySource.ECARX, mappingOrigin = VehicleMappingOrigin.ECARX)
    }
}

internal val CarPropertySnapshot.hasEcarxSample: Boolean get() = rawValue != null
