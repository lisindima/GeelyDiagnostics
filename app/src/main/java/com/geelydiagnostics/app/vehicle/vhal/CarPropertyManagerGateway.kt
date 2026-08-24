package com.geelydiagnostics.app.vehicle.vhal

import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import java.math.BigDecimal

/** Read-only AAOS gateway. CarService selects its AIDL or HIDL VHAL backend. */
@Suppress("DEPRECATION") // Compatibility path shared with Android Automotive 11.
internal class CarPropertyManagerGateway(
    context: Context,
    private val log: (String, Throwable?) -> Unit,
) : VhalGateway {
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private val subscribedIds = linkedSetOf<Int>()

    override fun connect() {
        val connectedCar = Car.createCar(appContext)
            ?: throw IllegalStateException("Car.createCar() returned null")
        if (!connectedCar.isConnected) connectedCar.connect()
        val propertyManager = connectedCar.getCarManager(Car.PROPERTY_SERVICE)
            as? CarPropertyManager
            ?: throw IllegalStateException("CarPropertyManager is unavailable")
        car = connectedCar
        manager = propertyManager
        log("VHAL gateway: CarPropertyManager", null)
    }

    override fun readConfigs(): List<VhalPropertyConfig> {
        val configs = requireManager().propertyList.map { config ->
            VhalPropertyConfig(
                propertyId = config.propertyId,
                access = config.access,
                changeMode = config.changeMode,
                areaIds = config.areaIds.toList().ifEmpty { listOf(0) },
            )
        }.sortedBy(VhalPropertyConfig::propertyId)
        log("CarPropertyManager configs: ${configs.size}", null)
        return configs
    }

    override fun read(propertyId: Int, areaId: Int): VhalPropertyValue {
        val value = requireManager().getProperty<Any>(propertyId, areaId)
            ?: throw IllegalStateException("CarPropertyManager returned null")
        return value.toGatewayValue()
    }

    override fun subscribe(
        configs: List<VhalPropertyConfig>,
        onValue: (VhalPropertyValue) -> Unit,
    ): Set<Int> {
        val propertyManager = requireManager()
        val eventCallback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                runCatching { value.toGatewayValue() }
                    .onSuccess(onValue)
                    .onFailure { log("Car API event skipped: ${describe(it)}", it) }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                log("Car API callback error property=${propertyId.hex()} area=${areaId.hex()}", null)
            }
        }
        val dynamicConfigs = configs.filter(VhalPropertyConfig::dynamic)
        val successful = dynamicConfigs.mapNotNull { config ->
            val rate = if (config.continuous) {
                CarPropertyManager.SENSOR_RATE_NORMAL
            } else {
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            }
            runCatching {
                propertyManager.registerCallback(eventCallback, config.propertyId, rate)
            }.onFailure { error ->
                log("Car API subscribe ${config.propertyId.hex()}: ${describe(error)}", error)
            }.getOrDefault(false).let { registered ->
                config.propertyId.takeIf { registered }
            }
        }
        if (dynamicConfigs.isNotEmpty() && successful.isEmpty()) {
            throw IllegalStateException("CarPropertyManager rejected all subscriptions")
        }
        callback = eventCallback
        subscribedIds += successful
        log("Car API subscriptions: ${successful.size}/${dynamicConfigs.size}", null)
        return successful.toSet()
    }

    override fun close() {
        val propertyManager = manager
        val eventCallback = callback
        if (propertyManager != null && eventCallback != null) {
            runCatching { propertyManager.unregisterCallback(eventCallback) }
                .onFailure { log("Car API unsubscribe: ${describe(it)}", it) }
        }
        subscribedIds.clear()
        callback = null
        manager = null
        runCatching { car?.disconnect() }
        car = null
    }

    private fun requireManager(): CarPropertyManager =
        manager ?: error("CarPropertyManager gateway is not connected")

    private fun CarPropertyValue<*>.toGatewayValue(): VhalPropertyValue {
        if (status != CarPropertyValue.STATUS_AVAILABLE) {
            throw IllegalStateException("Car property status=$status")
        }
        return VhalPropertyValue(
            propertyId = propertyId,
            areaId = areaId,
            raw = value.toRawVehicleValue(),
            sourceTimestampNanos = timestamp,
        )
    }
}

private fun Any?.toRawVehicleValue(): RawVehicleValue = when (this) {
    null -> throw IllegalStateException("Car property value is null")
    is Number -> RawVehicleValue(
        text = formatCarNumber(toDouble()),
        number = toDouble(),
    )
    is Boolean -> RawVehicleValue(if (this) "1" else "0", if (this) 1.0 else 0.0)
    is String -> RawVehicleValue(this)
    is IntArray -> RawVehicleValue(joinToString(prefix = "[", postfix = "]"))
    is LongArray -> RawVehicleValue(joinToString(prefix = "[", postfix = "]"))
    is FloatArray -> RawVehicleValue(
        joinToString(prefix = "[", postfix = "]") { formatCarNumber(it.toDouble()) },
    )
    is DoubleArray -> RawVehicleValue(joinToString(prefix = "[", postfix = "]", transform = ::formatCarNumber))
    is ByteArray -> RawVehicleValue(joinToString(prefix = "[", postfix = "]") { (it.toInt() and 0xff).toString() })
    is Array<*> -> RawVehicleValue(joinToString(prefix = "[", postfix = "]") { it.toCarText() })
    is Iterable<*> -> RawVehicleValue(joinToString(prefix = "[", postfix = "]") { it.toCarText() })
    else -> RawVehicleValue(toString())
}

private fun Any?.toCarText(): String = when (this) {
    is Float -> formatCarNumber(toDouble())
    is Double -> formatCarNumber(this)
    else -> toString()
}

private fun formatCarNumber(value: Double): String = if (value.isFinite()) {
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
} else {
    value.toString()
}

private fun Int.hex(): String = "0x${toUInt().toString(16).padStart(8, '0')}"

private fun describe(error: Throwable): String = error.javaClass.name +
    (error.message?.let { ": $it" } ?: "")
