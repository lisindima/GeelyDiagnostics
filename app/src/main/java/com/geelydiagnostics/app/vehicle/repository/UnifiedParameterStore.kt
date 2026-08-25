package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Observable normalized parameter state shared by every repository consumer. */
internal class UnifiedParameterStore {
    private val cache = UnifiedParameterCache()
    private val mutableParameters = MutableStateFlow<List<VehicleParameter>>(emptyList())

    val parameters: StateFlow<List<VehicleParameter>> = mutableParameters.asStateFlow()

    @Synchronized
    fun replaceSource(
        source: VehiclePropertySource,
        values: List<CarPropertySnapshot>,
        section: VehicleDataSection? = null,
    ) {
        cache.replaceSource(source, values, section)
        publish()
    }

    @Synchronized
    fun update(value: CarPropertySnapshot): Boolean {
        val changed = cache.update(value)
        publish()
        return changed
    }

    /** Observe one stable normalized property. Area zero represents a global property. */
    fun observe(
        propertyId: CarPropertyId,
        areaId: Int = 0,
        section: VehicleDataSection = VehicleDataSection.PARAMETER,
    ): Flow<VehicleParameter?> =
        parameters
            .map { values ->
                values.firstOrNull { value ->
                    value.section == section && value.propertyId == propertyId && value.areaId == areaId
                }
            }
            .distinctUntilChanged()

    fun observe(key: String): Flow<VehicleParameter?> = parameters
        .map { values -> values.firstOrNull { it.favoriteKey == key } }
        .distinctUntilChanged()

    @Synchronized
    fun clear() {
        cache.clear()
        mutableParameters.value = emptyList()
    }

    private fun publish() {
        mutableParameters.value = cache.parameters()
    }
}
