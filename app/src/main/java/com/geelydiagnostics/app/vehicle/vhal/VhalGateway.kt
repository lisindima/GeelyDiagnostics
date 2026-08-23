package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import java.io.Closeable

internal data class VhalPropertyConfig(
    val propertyId: Int,
    val access: Int,
    val changeMode: Int,
    val areaIds: List<Int>,
) {
    val readable: Boolean
        get() = access and ACCESS_READ != 0

    val dynamic: Boolean
        get() = readable && changeMode != CHANGE_MODE_STATIC

    val continuous: Boolean
        get() = changeMode == CHANGE_MODE_CONTINUOUS

    companion object {
        private const val ACCESS_READ = 1
        private const val CHANGE_MODE_STATIC = 0
        private const val CHANGE_MODE_CONTINUOUS = 2
    }
}

internal data class VhalPropertyValue(
    val propertyId: Int,
    val areaId: Int,
    val raw: RawVehicleValue,
    val sourceTimestampNanos: Long?,
)

internal interface VhalGateway : Closeable {
    fun connect()
    fun readConfigs(): List<VhalPropertyConfig>
    fun read(propertyId: Int, areaId: Int): VhalPropertyValue
    fun subscribe(
        configs: List<VhalPropertyConfig>,
        onValue: (VhalPropertyValue) -> Unit,
    ): Set<Int>
}
