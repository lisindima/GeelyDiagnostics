package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import java.io.Closeable
import java.math.BigDecimal

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
    val error: String? = null,
    val obd2Payload: com.geelydiagnostics.app.model.Obd2RawPayload? = null,
)

internal interface VhalGateway : Closeable {
    fun obd2Gateway(): Obd2Gateway? = null
    fun connect()
    fun readConfigs(): List<VhalPropertyConfig>
    fun read(propertyId: Int, areaId: Int): VhalPropertyValue
    /** Adds subscriptions without replacing callbacks already installed by a previous batch. */
    fun subscribe(
        configs: List<VhalPropertyConfig>,
        onValue: (VhalPropertyValue) -> Unit,
    ): Set<Int>
}

/** Keeps the shortest decimal form supplied by Float instead of exposing Float-to-Double noise. */
internal fun formatVhalNumber(value: Number): String = when (value) {
    is Float -> if (value.isFinite()) {
        BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    } else {
        value.toString()
    }
    is Double -> if (value.isFinite()) {
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    } else {
        value.toString()
    }
    is Byte, is Short, is Int, is Long -> value.toLong().toString()
    else -> value.toString()
}

/** Numeric form matching [formatVhalNumber], without Float-to-Double representation noise. */
internal fun Number.toStableVhalDouble(): Double = formatVhalNumber(this).toDoubleOrNull() ?: toDouble()
