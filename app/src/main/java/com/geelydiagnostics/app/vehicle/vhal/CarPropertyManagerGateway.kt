package com.geelydiagnostics.app.vehicle.vhal

import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue

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

    override fun obd2Gateway(): Obd2Gateway = CarDiagnosticObd2Gateway(
        obtainManager = { requireNotNull(car) { "Car is not connected" }.getCarManager("diagnostic") },
        requireReadPermission = {
            val permission = "android.car.permission.CAR_DIAGNOSTICS"
            if (appContext.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Не выдано разрешение $permission")
            }
        },
    )

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
        val eventCallback = callback ?: object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                runCatching { value.toGatewayValue() }
                    .onSuccess(onValue)
                    .onFailure {
                        log("Car API event failed: ${describe(it)}", it)
                        onValue(VhalPropertyValue(value.propertyId, value.areaId, RawVehicleValue("—"),
                            value.timestamp, describe(it)))
                    }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                log("Car API callback error property=${propertyId.hex()} area=${areaId.hex()}", null)
                onValue(VhalPropertyValue(propertyId, areaId, RawVehicleValue("—"), null,
                    "Car API callback error"))
            }
        }
        callback = eventCallback
        val dynamicConfigs = configs.filter { it.dynamic && it.propertyId !in subscribedIds }
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
                config.propertyId.takeIf { registered }?.also { subscribedIds += it }
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

internal fun Any?.toRawVehicleValue(): RawVehicleValue = when (this) {
    null -> throw IllegalStateException("Car property value is null")
    is Float -> RawVehicleValue(formatVhalNumber(this), toStableVhalDouble())
    is Double -> RawVehicleValue(formatVhalNumber(this), this)
    is Number -> RawVehicleValue(
        text = formatVhalNumber(this),
        number = toStableVhalDouble(),
    )
    is Boolean -> RawVehicleValue(if (this) "1" else "0", if (this) 1.0 else 0.0)
    is String -> RawVehicleValue(this)
    is IntArray -> RawVehicleValue(
        text = joinToString(prefix = "[", postfix = "]"),
        numbers = map { it.toStableVhalDouble() },
    )
    is LongArray -> RawVehicleValue(
        text = joinToString(prefix = "[", postfix = "]"),
        numbers = map { it.toStableVhalDouble() },
    )
    is FloatArray -> RawVehicleValue(
        text = joinToString(prefix = "[", postfix = "]", transform = ::formatVhalNumber),
        numbers = map { it.toStableVhalDouble() },
    )
    is DoubleArray -> RawVehicleValue(
        text = joinToString(prefix = "[", postfix = "]", transform = ::formatVhalNumber),
        numbers = map { it.toStableVhalDouble() },
    )
    is ByteArray -> RawVehicleValue(
        text = joinToString(prefix = "[", postfix = "]") { (it.toInt() and 0xff).toString() },
        numbers = map { (it.toInt() and 0xff).toDouble() },
    )
    is Array<*> -> asIterable().toRawVehicleVector()
    is Iterable<*> -> toRawVehicleVector()
    else -> RawVehicleValue(toString())
}

private fun Any?.toCarText(): String = when (this) {
    is Number -> formatVhalNumber(this)
    else -> toString()
}

private fun Iterable<*>.toRawVehicleVector(): RawVehicleValue {
    val values = toList()
    val numbers = values.filterIsInstance<Number>()
        .takeIf { it.size == values.size }
        ?.map(Number::toStableVhalDouble)
    return RawVehicleValue(
        text = values.joinToString(prefix = "[", postfix = "]") { it.toCarText() },
        numbers = numbers,
    )
}

private fun Int.hex(): String = "0x${toUInt().toString(16).padStart(8, '0')}"

private fun describe(error: Throwable): String = error.javaClass.name +
    (error.message?.let { ": $it" } ?: "")
