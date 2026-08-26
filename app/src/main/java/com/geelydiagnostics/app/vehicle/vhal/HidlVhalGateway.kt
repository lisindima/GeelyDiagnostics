package com.geelydiagnostics.app.vehicle.vhal

import android.hardware.automotive.vehicle.V2_0.IVehicleCallback
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reflective Android 11 HIDL boundary. No mutating operation is exposed by [VhalGateway]. */
internal class HidlVhalGateway(
    private val log: (String, Throwable?) -> Unit,
) : VhalGateway {
    private var vehicleClass: Class<*>? = null
    private var service: Any? = null
    private var callback: IVehicleCallback? = null
    private val subscribedIds = linkedSetOf<Int>()

    override fun connect() {
        val type = Class.forName(VEHICLE_CLASS)
        val connected = type.methods
            .firstOrNull { it.name == "getService" && it.parameterCount == 0 }
            ?.invoke(null)
            ?: throw IllegalStateException("IVehicle.getService() returned null")
        vehicleClass = type
        service = connected
        log("VHAL gateway: ${connected.javaClass.name}", null)
    }

    override fun readConfigs(): List<VhalPropertyConfig> {
        val (type, connected) = requireConnected()
        val methods = (type.methods.asSequence() + connected.javaClass.methods.asSequence())
            .filter { it.name == "getAllPropConfigs" }
            .distinctBy { method -> method.parameterTypes.joinToString { it.name } }
            .toList()
        val method = methods.firstOrNull { it.parameterCount == 0 }
            ?: methods.firstOrNull { it.parameterCount == 1 }
            ?: throw NoSuchMethodException(
                "IVehicle.getAllPropConfigs() or getAllPropConfigs(callback); " +
                    "available=${methods.joinToString { it.toGenericString() }.ifBlank { "none" }}",
            )
        var status: Int? = null
        val result = if (method.parameterCount == 0) {
            method.invoke(connected)
        } else {
            invokeCallback(method, connected) { args ->
                status = (args.firstOrNull() as? Number)?.toInt()
                args.lastOrNull { it is List<*> }
            }
        }
        if (status != null && status != STATUS_OK) {
            throw IllegalStateException("getAllPropConfigs status=$status")
        }
        val rawConfigs = result as? List<*>
            ?: throw IllegalStateException("getAllPropConfigs() did not return a list")
        val parsed = rawConfigs.mapNotNull(::parseConfig)
        val configs = parsed.groupBy(VhalPropertyConfig::propertyId)
            .map { (_, duplicates) -> duplicates.merge() }
            .sortedBy(VhalPropertyConfig::propertyId)
        log(
            "VHAL configs via getAllPropConfigs/${method.parameterCount}: " +
                "${rawConfigs.size} received, ${parsed.size} parsed, ${configs.size} unique",
            null,
        )
        return configs
    }

    override fun read(propertyId: Int, areaId: Int): VhalPropertyValue {
        val (type, connected) = requireConnected()
        val valueClass = Class.forName(VALUE_CLASS)
        val request = valueClass.getDeclaredConstructor().newInstance().also { value ->
            valueClass.getField("prop").setInt(value, propertyId)
            valueClass.getField("areaId").setInt(value, areaId)
        }
        val method = (type.methods.asSequence() + connected.javaClass.methods.asSequence())
            .firstOrNull {
                it.name == "get" && it.parameterCount == 2 &&
                    it.parameterTypes[0].isAssignableFrom(valueClass)
            } ?: throw NoSuchMethodException("IVehicle.get(value, callback)")

        var status: Int? = null
        val returned = invokeCallback(method, connected, request) { args ->
            status = (args.firstOrNull() as? Number)?.toInt()
            args.getOrNull(1)
        }
        if (status != STATUS_OK) throw IllegalStateException("VHAL status=$status")
        if (returned == null) throw IllegalStateException("VHAL returned null")
        val propertyStatus = runCatching {
            returned.javaClass.getField("status").getInt(returned)
        }.getOrDefault(STATUS_OK)
        if (propertyStatus != STATUS_OK) {
            throw IllegalStateException("Vehicle property status=$propertyStatus")
        }
        return parseValue(returned)
    }

    override fun subscribe(
        configs: List<VhalPropertyConfig>,
        onValue: (VhalPropertyValue) -> Unit,
    ): Set<Int> {
        val (type, connected) = requireConnected()
        val dynamicConfigs = configs.asSequence()
            .filter(VhalPropertyConfig::dynamic)
            .filter { it.propertyId !in subscribedIds }
            .distinctBy(VhalPropertyConfig::propertyId)
            .toList()
        if (dynamicConfigs.isEmpty()) return emptySet()

        val vehicleCallback = callback ?: object : IVehicleCallback.Stub() {
            override fun onPropertyEvent(values: ArrayList<VehiclePropValue>) {
                values.forEach { value ->
                    runCatching { parseValue(value) }
                        .onSuccess(onValue)
                        .onFailure { log("VHAL event skipped: ${describe(it)}", it) }
                }
            }

            override fun onPropertySet(value: VehiclePropValue) = Unit

            override fun onPropertySetError(errorCode: Int, propertyId: Int, areaId: Int) {
                log(
                    "VHAL callback error=$errorCode property=${propertyId.hex()} area=${areaId.hex()}",
                    null,
                )
            }
        }
        callback = vehicleCallback
        val subscribe = (type.methods.asSequence() + connected.javaClass.methods.asSequence())
            .firstOrNull { method ->
                method.name == "subscribe" && method.parameterCount == 2 &&
                    method.parameterTypes[0].isInstance(vehicleCallback)
            } ?: throw NoSuchMethodException("IVehicle.subscribe(callback, options)")
        val optionsClass = Class.forName(SUBSCRIBE_OPTIONS_CLASS)
        val options = dynamicConfigs.mapTo(ArrayList<Any>(dynamicConfigs.size)) { config ->
            optionsClass.getDeclaredConstructor().newInstance().also { option ->
                optionsClass.getField("propId").setInt(option, config.propertyId)
                optionsClass.getField("sampleRate").setFloat(option, HIDL_SAMPLE_RATE)
                optionsClass.getField("flags").setInt(option, SUBSCRIBE_FLAG_EVENTS_FROM_CAR)
            }
        }

        val batchStatus = subscribe.status(connected, vehicleCallback, options)
        val successful = if (batchStatus == STATUS_OK) {
            dynamicConfigs.map(VhalPropertyConfig::propertyId)
        } else {
            log(
                "VHAL batch subscription status=$batchStatus; retrying individually",
                null,
            )
            dynamicConfigs.zip(options).mapNotNull { (config, option) ->
                val result = runCatching {
                    subscribe.status(connected, vehicleCallback, arrayListOf(option))
                }.onFailure { log("VHAL subscribe ${config.propertyId.hex()}: ${describe(it)}", it) }
                    .getOrNull()
                config.propertyId.takeIf { result == STATUS_OK }?.also { subscribedIds += it }
            }
        }
        if (successful.isEmpty()) {
            throw IllegalStateException("IVehicle.subscribe rejected all properties (batch=$batchStatus)")
        }
        callback = vehicleCallback
        subscribedIds += successful
        log("VHAL subscriptions: ${successful.size}/${dynamicConfigs.size}", null)
        return successful.toSet()
    }

    private fun parseConfig(value: Any?): VhalPropertyConfig? {
        if (value == null) return null
        return runCatching {
            val type = value.javaClass
            val areas = (type.getField("areaConfigs").get(value) as? List<*>)
                .orEmpty()
                .mapNotNull { area -> area?.let { it.javaClass.getField("areaId").getInt(it) } }
                .distinct()
                .ifEmpty { listOf(0) }
            VhalPropertyConfig(
                propertyId = type.getField("prop").getInt(value),
                access = type.getField("access").getInt(value),
                changeMode = type.getField("changeMode").getInt(value),
                areaIds = areas,
            )
        }.onFailure { log("VHAL config skipped: ${describe(it)}", it) }.getOrNull()
    }

    private fun parseValue(property: Any): VhalPropertyValue {
        val type = property.javaClass
        val propertyId = type.getField("prop").getInt(property)
        val areaId = type.getField("areaId").getInt(property)
        val timestamp = runCatching { type.getField("timestamp").getLong(property) }.getOrNull()
        val status = runCatching { type.getField("status").getInt(property) }.getOrDefault(STATUS_OK)
        if (status != STATUS_OK) return VhalPropertyValue(
            propertyId, areaId, RawVehicleValue("—"), timestamp, "Vehicle property status=$status",
        )
        val rawContainer = type.getField("value").get(property)
            ?: throw IllegalStateException("VehiclePropValue.value is null")
        return VhalPropertyValue(
            propertyId = propertyId,
            areaId = areaId,
            raw = extractRaw(rawContainer, propertyId),
            sourceTimestampNanos = timestamp,
        )
    }

    private fun extractRaw(container: Any, propertyId: Int): RawVehicleValue {
        val int32 = numberList(container, "int32Values")
        val floats = numberList(container, "floatValues")
        val int64 = numberList(container, "int64Values")
        val bytes = numberList(container, "bytes").map { it.toInt() and 0xff }
        val string = runCatching {
            container.javaClass.getField("stringValue").get(container) as? String
        }.getOrNull().orEmpty()

        val preferred = when (propertyId and PROPERTY_TYPE_MASK) {
            TYPE_STRING -> return RawVehicleValue(string)
            TYPE_FLOAT -> floats.firstOrNull()
            TYPE_INT32, TYPE_BOOLEAN -> int32.firstOrNull()
            TYPE_INT64 -> int64.firstOrNull()
            TYPE_FLOAT_VECTOR -> return RawVehicleValue.vector(floats)
            TYPE_INT32_VECTOR -> return RawVehicleValue.vector(int32)
            TYPE_INT64_VECTOR -> return RawVehicleValue.vector(int64)
            TYPE_BYTES -> return RawVehicleValue.vector(bytes)
            else -> null
        }
        if (preferred != null) return RawVehicleValue.number(preferred)

        val populated = buildList {
            if (string.isNotEmpty()) add(string)
            if (int32.isNotEmpty()) add(int32.formatted())
            if (floats.isNotEmpty()) add(floats.formatted())
            if (int64.isNotEmpty()) add(int64.formatted())
            if (bytes.isNotEmpty()) add(bytes.formatted())
        }
        if (populated.isEmpty()) throw IllegalStateException("VHAL value containers are empty")
        val onlyNumbers = int32.ifEmpty { floats.ifEmpty { int64.ifEmpty { bytes } } }
        if (populated.size == 1 && onlyNumbers.size == 1 && string.isEmpty()) {
            return RawVehicleValue.number(onlyNumbers.first())
        }
        return RawVehicleValue(populated.joinToString(" · "))
    }

    private fun numberList(container: Any, field: String): List<Number> = runCatching {
        (container.javaClass.getField(field).get(container) as? List<*>)
            .orEmpty()
            .filterIsInstance<Number>()
    }.getOrDefault(emptyList())

    private fun invokeCallback(
        method: Method,
        receiver: Any,
        vararg leadingArguments: Any,
        extract: (Array<out Any?>) -> Any?,
    ): Any? {
        val callbackType = method.parameterTypes.last()
        val latch = CountDownLatch(1)
        var result: Any? = null
        val resultCallback = Proxy.newProxyInstance(
            callbackType.classLoader ?: javaClass.classLoader,
            arrayOf(callbackType),
        ) { proxy, callbackMethod, args ->
            when (callbackMethod.name) {
                "onValues" -> {
                    result = extract(args.orEmpty())
                    latch.countDown()
                    null
                }
                "toString" -> "VhalReadResultCallback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        method.invoke(receiver, *leadingArguments, resultCallback)
        if (!latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw IllegalStateException("${method.name} callback timeout")
        }
        return result
    }

    private fun requireConnected(): Pair<Class<*>, Any> =
        (vehicleClass ?: error("VHAL gateway is not connected")) to
            (service ?: error("VHAL gateway is not connected"))

    private fun Method.status(receiver: Any, callback: IVehicleCallback, options: ArrayList<Any>): Int =
        (invoke(receiver, callback, options) as? Number)?.toInt() ?: STATUS_OK

    override fun close() {
        val connected = service
        val vehicleCallback = callback
        if (connected != null && vehicleCallback != null) {
            val unsubscribe = connected.javaClass.methods.firstOrNull {
                it.name == "unsubscribe" && it.parameterCount == 2
            }
            if (unsubscribe == null) {
                log("VHAL unsubscribe unavailable", null)
            } else {
                subscribedIds.forEach { propertyId ->
                    runCatching { unsubscribe.invoke(connected, vehicleCallback, propertyId) }
                        .onFailure { log("VHAL unsubscribe ${propertyId.hex()}: ${describe(it)}", it) }
                }
            }
        }
        subscribedIds.clear()
        callback = null
        service = null
        vehicleClass = null
    }

    companion object {
        private const val VEHICLE_CLASS = "android.hardware.automotive.vehicle.V2_0.IVehicle"
        private const val VALUE_CLASS = "android.hardware.automotive.vehicle.V2_0.VehiclePropValue"
        private const val SUBSCRIBE_OPTIONS_CLASS =
            "android.hardware.automotive.vehicle.V2_0.SubscribeOptions"
        private const val STATUS_OK = 0
        private const val SUBSCRIBE_FLAG_EVENTS_FROM_CAR = 1
        private const val HIDL_SAMPLE_RATE = 0f
        private const val CALLBACK_TIMEOUT_SECONDS = 2L
        private const val PROPERTY_TYPE_MASK = 0x00ff0000
        private const val TYPE_STRING = 0x00100000
        private const val TYPE_BOOLEAN = 0x00200000
        private const val TYPE_INT32 = 0x00400000
        private const val TYPE_INT32_VECTOR = 0x00410000
        private const val TYPE_INT64 = 0x00500000
        private const val TYPE_INT64_VECTOR = 0x00510000
        private const val TYPE_FLOAT = 0x00600000
        private const val TYPE_FLOAT_VECTOR = 0x00610000
        private const val TYPE_BYTES = 0x00700000
    }
}

private fun List<VhalPropertyConfig>.merge(): VhalPropertyConfig {
    val first = first()
    return first.copy(
        access = fold(0) { result, config -> result or config.access },
        changeMode = maxOf { it.changeMode },
        areaIds = flatMap(VhalPropertyConfig::areaIds).distinct(),
    )
}

private fun RawVehicleValue.Companion.number(value: Number): RawVehicleValue = RawVehicleValue(
    text = formatVhalNumber(value),
    number = value.toStableVhalDouble(),
)

private fun RawVehicleValue.Companion.vector(values: List<Number>): RawVehicleValue = RawVehicleValue(
    text = values.formatted(),
    numbers = values.map(Number::toStableVhalDouble),
)

private fun List<Number>.formatted(): String =
    joinToString(prefix = "[", postfix = "]") { value ->
        when (value) {
            is Float, is Double -> formatVhalNumber(value)
            else -> value.toLong().toString()
        }
    }

private fun Int.hex(): String = "0x${toUInt().toString(16).padStart(8, '0')}"

private fun describe(error: Throwable): String = generateSequence(error) { it.cause }
    .take(5)
    .joinToString(" <- ") { cause ->
        cause.javaClass.name + (cause.message?.takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
    }
