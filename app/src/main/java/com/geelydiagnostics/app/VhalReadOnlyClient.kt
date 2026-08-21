package com.geelydiagnostics.app

import android.content.Context
import java.io.Closeable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Read-only VHAL boundary. HIDL supplies the complete property catalog and initial values;
 * CarPropertyManager is used only to observe subsequent value changes.
 */
internal class VhalReadOnlyClient(
    private val context: Context,
    private val profile: VhalProfile,
    private val sink: ReadOnlySink,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "VhalReadOnly").apply { isDaemon = true }
    }

    @Volatile
    private var closed = false
    private var car: Any? = null
    private var propertyManager: Any? = null
    private var propertyCallback: Any? = null
    private val subscribedIds = linkedSetOf<Int>()
    private val lastLoggedRawByKey = mutableMapOf<PropertyKey, String>()
    private var recordsByKey = emptyMap<PropertyKey, SensorRecord>()

    fun start() {
        sink.onVhalStatus(ReadStatus.CHECKING, "VHAL: полный каталог; декодер ${profile.key}")
        executor.execute(::readCatalog)
    }

    private fun readCatalog() {
        if (closed) return
        try {
            val vehicleClass = Class.forName(VEHICLE_CLASS)
            val service = vehicleClass.methods
                .firstOrNull { it.name == "getService" && it.parameterCount == 0 }
                ?.invoke(null)
                ?: throw IllegalStateException("IVehicle.getService() returned null")

            sink.onLog("VHAL: service ${service.javaClass.name}; decoder ${profile.key}")
            val configs = readConfigs(vehicleClass, service)
            if (configs.isEmpty()) {
                throw IllegalStateException("getAllPropConfigs() returned an empty catalog")
            }
            val specs = VhalProfileRegistry.signals(profile).associateBy(VhalSignalSpec::readSignalId)
            val pendingRecords = configs.flatMap { config ->
                config.areaIds.map { areaId ->
                    recordFor(
                        propertyId = config.propertyId,
                        areaId = areaId,
                        spec = specs[config.propertyId],
                        raw = null,
                        error = "Значение ещё читается",
                        expectedUpdateIntervalMillis = config.expectedUpdateIntervalMillis(),
                    )
                }
            }
            sink.onSensorsChanged(VehicleDataSource.VHAL, pendingRecords)
            sink.onVhalStatus(
                ReadStatus.CHECKING,
                "Получено ${configs.size} свойств · чтение значений · декодер ${profile.key}",
            )
            sink.onLog("VHAL: ${configs.size} configs received; reading ${pendingRecords.size} values")
            val records = configs.flatMap { config ->
                config.areaIds.map { areaId ->
                    readProperty(vehicleClass, service, config, areaId, specs[config.propertyId])
                }
            }
            if (closed) return
            recordsByKey = records.associateBy { PropertyKey(it.id, it.areaId) }
            records.forEach(::logInitialValue)
            sink.onSensorsChanged(VehicleDataSource.VHAL, records)

            val callbackCount = subscribeToChanges(configs)
            val classifiedRecords = records.map { record ->
                record.copy(autoUpdates = record.id in subscribedIds)
            }
            recordsByKey = classifiedRecords.associateBy { PropertyKey(it.id, it.areaId) }
            sink.onSensorsChanged(VehicleDataSource.VHAL, classifiedRecords)
            val mappedCount = classifiedRecords.count { it.sourceProfile != null }
            val mappingDetail = if (profile == VhalProfile.RAW) {
                "RAW без расшифровки"
            } else {
                "$mappedCount расшифровано ${profile.key}"
            }
            val updateDetail = if (callbackCount > 0) {
                "подписки: $callbackCount"
            } else {
                "подписки недоступны · только стартовый снимок"
            }
            sink.onVhalStatus(
                ReadStatus.AVAILABLE,
                "${records.size} значений · $mappingDetail · $updateDetail",
            )
            sink.onLog(
                "VHAL: ${records.size} values from ${configs.size} configs; " +
                    "$mappingDetail; $callbackCount callback subscriptions; polling disabled",
            )
        } catch (error: Throwable) {
            if (closed) return
            sink.onVhalStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("VHAL: ${describe(error)}", error)
        }
    }

    private fun readConfigs(vehicleClass: Class<*>, service: Any): List<PropertyConfig> {
        val method = vehicleClass.methods.firstOrNull {
            it.name == "getAllPropConfigs" && it.parameterCount == 1
        } ?: throw NoSuchMethodException("IVehicle.getAllPropConfigs(callback)")
        var status: Int? = null
        val result = invokeCallback(method, service) { args ->
            status = (args.firstOrNull() as? Number)?.toInt()
            args.lastOrNull { it is List<*> }
        }
        if (status != null && status != STATUS_OK) {
            throw IllegalStateException("getAllPropConfigs status=$status")
        }
        val rawConfigs = result as? List<*>
            ?: throw IllegalStateException("getAllPropConfigs() did not return a list")
        val configs = rawConfigs.mapNotNull(::readConfig).sortedBy(PropertyConfig::propertyId)
        sink.onLog("VHAL configs: ${rawConfigs.size} received, ${configs.size} parsed")
        return configs
    }

    private fun readConfig(value: Any?): PropertyConfig? {
        if (value == null) return null
        return runCatching {
            val type = value.javaClass
            val areas = (type.getField("areaConfigs").get(value) as? List<*>)
                .orEmpty()
                .mapNotNull { area -> area?.let { it.javaClass.getField("areaId").getInt(it) } }
                .distinct()
                .ifEmpty { listOf(0) }
            PropertyConfig(
                propertyId = type.getField("prop").getInt(value),
                access = type.getField("access").getInt(value),
                changeMode = type.getField("changeMode").getInt(value),
                minSampleRate = runCatching { type.getField("minSampleRate").getFloat(value) }.getOrDefault(0f),
                maxSampleRate = runCatching { type.getField("maxSampleRate").getFloat(value) }.getOrDefault(0f),
                areaIds = areas,
            )
        }.onFailure { sink.onLog("VHAL config skipped: ${describe(it)}") }.getOrNull()
    }

    private fun readProperty(
        vehicleClass: Class<*>,
        service: Any,
        config: PropertyConfig,
        areaId: Int,
        spec: VhalSignalSpec?,
    ): SensorRecord = if (!config.isReadable()) {
        recordFor(
            config.propertyId,
            areaId,
            spec,
            null,
            "Свойство присутствует в VHAL, но не помечено доступным для чтения",
            config.expectedUpdateIntervalMillis(),
        )
    } else try {
        val returned = getProperty(vehicleClass, service, config.propertyId, areaId)
        recordFor(
            config.propertyId,
            areaId,
            spec,
            extractRaw(returned, spec?.valueType),
            expectedUpdateIntervalMillis = config.expectedUpdateIntervalMillis(),
        )
    } catch (error: Throwable) {
        recordFor(
            config.propertyId,
            areaId,
            spec,
            null,
            describe(error),
            config.expectedUpdateIntervalMillis(),
        )
    }

    private fun getProperty(vehicleClass: Class<*>, service: Any, propertyId: Int, areaId: Int): Any {
        val valueClass = Class.forName(VALUE_CLASS)
        val request = valueClass.getDeclaredConstructor().newInstance().also { value ->
            valueClass.getField("prop").setInt(value, propertyId)
            valueClass.getField("areaId").setInt(value, areaId)
        }
        val method = vehicleClass.methods.firstOrNull {
            it.name == "get" && it.parameterCount == 2 && it.parameterTypes[0] == valueClass
        } ?: throw NoSuchMethodException("IVehicle.get(value, callback)")

        var status: Int? = null
        val returned = invokeCallback(method, service, request) { args ->
            status = (args.firstOrNull() as? Number)?.toInt()
            args.getOrNull(1)
        }
        if (status != STATUS_OK) throw IllegalStateException("VHAL status=$status")
        if (returned == null) throw IllegalStateException("VHAL returned null")
        val propertyStatus = runCatching {
            returned.javaClass.getField("status").getInt(returned)
        }.getOrDefault(STATUS_OK)
        if (propertyStatus != STATUS_OK) throw IllegalStateException("Vehicle property status=$propertyStatus")
        return returned
    }

    private fun recordFor(
        propertyId: Int,
        areaId: Int,
        spec: VhalSignalSpec?,
        raw: VhalRawValue?,
        error: String = "",
        expectedUpdateIntervalMillis: Long? = null,
    ) = SensorRecord(
        id = propertyId,
        areaId = areaId,
        apiName = spec?.apiName ?: "VHAL_${hexId(propertyId)}",
        title = spec?.title ?: "VHAL property ${hexId(propertyId)}",
        value = when {
            raw == null -> ApiValue.unavailable
            spec != null -> VhalProfileRegistry.decode(spec, raw)
            else -> ApiValue.raw(raw.text)
        },
        valueKind = spec?.valueType?.label ?: propertyType(propertyId),
        support = ApiSupportStatus.ACTIVE,
        error = error,
        source = VehicleDataSource.VHAL,
        sourceProfile = spec?.profile?.key,
        profilePropertyId = spec?.propertyId,
        updatedAtMillis = System.currentTimeMillis(),
        expectedUpdateIntervalMillis = expectedUpdateIntervalMillis,
    )

    /** CarPropertyManager owns the Binder callback, keeping this reflection-only and read-only. */
    private fun subscribeToChanges(configs: List<PropertyConfig>): Int {
        if (closed) return 0
        return try {
            val carClass = Class.forName(CAR_CLASS)
            val createdCar = carClass.methods.firstOrNull {
                it.name == "createCar" && it.parameterCount == 1 &&
                    Context::class.java.isAssignableFrom(it.parameterTypes[0])
            }?.invoke(null, context)
                ?: throw IllegalStateException("Car.createCar(context) returned null")
            val manager = carClass.getMethod("getCarManager", String::class.java)
                .invoke(createdCar, PROPERTY_SERVICE)
                ?: throw IllegalStateException("CarPropertyManager unavailable")
            val callbackType = Class.forName(PROPERTY_CALLBACK_CLASS)
            val callback = Proxy.newProxyInstance(
                callbackType.classLoader ?: javaClass.classLoader,
                arrayOf(callbackType),
            ) { proxy, method, args ->
                when (method.name) {
                    "onChangeEvent" -> args?.firstOrNull()?.let(::queueLiveValue)
                    "onErrorEvent" -> sink.onLog("VHAL live error: ${args.orEmpty().joinToString()}")
                    "toString" -> "ReadOnlyCarPropertyCallback"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
            val register = manager.javaClass.methods.firstOrNull {
                it.name == "registerCallback" && it.parameterCount == 3
            } ?: throw NoSuchMethodException("CarPropertyManager.registerCallback(callback, id, rate)")

            car = createdCar
            propertyManager = manager
            propertyCallback = callback
            configs.asSequence()
                .filter(PropertyConfig::isReadableAndDynamic)
                .count { config ->
                    val result = runCatching {
                        register.invoke(manager, callback, config.propertyId, config.subscriptionRate())
                    }.onFailure {
                        sink.onLog("VHAL live ${hexId(config.propertyId)}: ${describe(it)}")
                    }
                    val returned = result.getOrNull()
                    val registered = result.isSuccess && (returned !is Boolean || returned)
                    if (registered) subscribedIds += config.propertyId
                    registered
                }
        } catch (error: Throwable) {
            sink.onLog("VHAL live unavailable: ${describe(error)}")
            0
        }
    }

    private fun queueLiveValue(carPropertyValue: Any) {
        if (closed) return
        executor.execute {
            if (closed) return@execute
            runCatching {
                val type = carPropertyValue.javaClass
                val propertyId = (type.getMethod("getPropertyId").invoke(carPropertyValue) as Number).toInt()
                val areaId = (type.getMethod("getAreaId").invoke(carPropertyValue) as Number).toInt()
                val value = type.getMethod("getValue").invoke(carPropertyValue)
                val oldRecord = recordsByKey[PropertyKey(propertyId, areaId)]
                    ?: recordsByKey[PropertyKey(propertyId, 0)]
                    ?: return@runCatching
                val raw = rawFromCarProperty(value)
                val spec = oldRecord.profilePropertyId?.let { profilePropertyId ->
                    VhalProfileRegistry.signals(profile).firstOrNull { it.propertyId == profilePropertyId }
                }
                val decoded = if (spec != null) VhalProfileRegistry.decode(spec, raw) else ApiValue.raw(raw.text)
                sink.onSensorValueChanged(VehicleDataSource.VHAL, propertyId, decoded, oldRecord.areaId)
                logChangedValue(oldRecord, decoded, "live")
            }.onFailure { sink.onLog("VHAL live value skipped: ${describe(it)}") }
        }
    }

    private fun logInitialValue(record: SensorRecord) {
        val key = PropertyKey(record.id, record.areaId)
        lastLoggedRawByKey[key] = record.value.raw
        val result = if (record.error.isBlank()) {
            "display=${logText(record.value.display)} raw=${logText(record.value.raw)}"
        } else {
            "error=${logText(record.error)}"
        }
        sink.onLog("VHAL initial ${record.logIdentity()} $result")
    }

    private fun logChangedValue(record: SensorRecord, value: ApiValue, event: String) {
        val key = PropertyKey(record.id, record.areaId)
        if (lastLoggedRawByKey.put(key, value.raw) == value.raw) return
        sink.onLog(
            "VHAL $event ${record.logIdentity()} " +
                "display=${logText(value.display)} raw=${logText(value.raw)}",
        )
    }

    private fun SensorRecord.logIdentity(): String = buildString {
        append("id=")
        append(hexId(id))
        if (areaId != 0) {
            append(" area=")
            append(hexId(areaId))
        }
        append(" mapping=")
        append(sourceProfile ?: "RAW")
    }

    private fun logText(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(MAX_LOG_VALUE_LENGTH)

    private fun rawFromCarProperty(value: Any?): VhalRawValue {
        if (value == null) return VhalRawValue("null")
        if (value is Number) return VhalRawValue.number(value)
        if (value is Boolean) return VhalRawValue(value.toString(), if (value) 1.0 else 0.0)
        if (value is CharSequence || value is Char) return VhalRawValue(value.toString())
        val values = when (value) {
            is IntArray -> value.toList()
            is LongArray -> value.toList()
            is FloatArray -> value.toList()
            is DoubleArray -> value.toList()
            is ByteArray -> value.map { it.toInt() and 0xff }
            is Array<*> -> value.toList()
            is Iterable<*> -> value.toList()
            else -> return VhalRawValue(value.toString())
        }
        return VhalRawValue(values.joinToString(prefix = "[", postfix = "]") { formatAny(it) })
    }

    private fun invokeCallback(
        method: Method,
        receiver: Any,
        vararg leadingArguments: Any,
        extract: (Array<out Any?>) -> Any?,
    ): Any? {
        val callbackType = method.parameterTypes.last()
        val latch = CountDownLatch(1)
        var result: Any? = null
        val callback = Proxy.newProxyInstance(
            callbackType.classLoader ?: javaClass.classLoader,
            arrayOf(callbackType),
        ) { proxy, callbackMethod, args ->
            when (callbackMethod.name) {
                "onValues" -> {
                    result = extract(args.orEmpty())
                    latch.countDown()
                    null
                }
                "toString" -> "ReadOnlyVhalResultCallback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        method.invoke(receiver, *leadingArguments, callback)
        if (!latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw IllegalStateException("${method.name} callback timeout")
        }
        return result
    }

    private fun extractRaw(property: Any, expectedType: VhalValueType?): VhalRawValue {
        val rawContainer = property.javaClass.getField("value").get(property)
            ?: throw IllegalStateException("VehiclePropValue.value is null")
        val int32 = numberList(rawContainer, "int32Values")
        val floats = numberList(rawContainer, "floatValues")
        val int64 = numberList(rawContainer, "int64Values")
        val bytes = numberList(rawContainer, "bytes").map { it.toInt() and 0xff }
        val string = runCatching {
            rawContainer.javaClass.getField("stringValue").get(rawContainer) as? String
        }.getOrNull().orEmpty()

        if (expectedType == VhalValueType.STRING && string.isNotEmpty()) return VhalRawValue(string)
        val preferred = when (expectedType) {
            VhalValueType.FLOAT -> floats.firstOrNull() ?: int32.firstOrNull() ?: int64.firstOrNull()
            VhalValueType.STRING -> null
            else -> int32.firstOrNull() ?: int64.firstOrNull() ?: floats.firstOrNull() ?: bytes.firstOrNull()
        }
        if (preferred != null && expectedType != null) return VhalRawValue.number(preferred)

        val populated = buildList {
            if (string.isNotEmpty()) add(string)
            if (int32.isNotEmpty()) add(formatList(int32))
            if (floats.isNotEmpty()) add(formatList(floats))
            if (int64.isNotEmpty()) add(formatList(int64))
            if (bytes.isNotEmpty()) add(formatList(bytes))
        }
        if (populated.isEmpty()) throw IllegalStateException("VHAL value containers are empty")
        if (populated.size == 1) {
            val onlyNumbers = int32.ifEmpty { floats.ifEmpty { int64.ifEmpty { bytes } } }
            if (onlyNumbers.size == 1 && string.isEmpty()) return VhalRawValue.number(onlyNumbers.first())
        }
        return VhalRawValue(populated.joinToString(" · "))
    }

    private fun numberList(container: Any, field: String): List<Number> = runCatching {
        (container.javaClass.getField(field).get(container) as? List<*>)
            .orEmpty()
            .filterIsInstance<Number>()
    }.getOrDefault(emptyList())

    private fun formatList(values: List<Number>): String =
        values.joinToString(prefix = "[", postfix = "]") { formatAny(it) }

    private fun formatAny(value: Any?): String = when (value) {
        is Float, is Double -> BigDecimalCompat.format((value as Number).toDouble())
        is Number -> value.toLong().toString()
        else -> value.toString()
    }

    private fun propertyType(propertyId: Int): String = when (propertyId and PROPERTY_TYPE_MASK) {
        TYPE_STRING -> "string"
        TYPE_BOOLEAN -> "boolean"
        TYPE_INT32 -> "int32"
        TYPE_INT32_VEC -> "int32[]"
        TYPE_INT64 -> "int64"
        TYPE_INT64_VEC -> "int64[]"
        TYPE_FLOAT -> "float"
        TYPE_FLOAT_VEC -> "float[]"
        TYPE_BYTES -> "bytes"
        TYPE_MIXED -> "mixed"
        else -> "raw"
    }

    private fun hexId(value: Int): String = String.format(Locale.US, "0x%08X", value)

    override fun close() {
        closed = true
        unsubscribeAll()
        executor.shutdownNow()
    }

    private fun unsubscribeAll() {
        val manager = propertyManager
        val callback = propertyCallback
        if (manager != null && callback != null) {
            val perProperty = manager.javaClass.methods.firstOrNull {
                it.name == "unregisterCallback" && it.parameterCount == 2
            }
            val all = manager.javaClass.methods.firstOrNull {
                it.name == "unregisterCallback" && it.parameterCount == 1
            }
            runCatching {
                if (perProperty != null) {
                    subscribedIds.forEach { perProperty.invoke(manager, callback, it) }
                } else {
                    all?.invoke(manager, callback)
                }
            }
        }
        runCatching { car?.javaClass?.getMethod("disconnect")?.invoke(car) }
        subscribedIds.clear()
        propertyCallback = null
        propertyManager = null
        car = null
    }

    private fun describe(error: Throwable): String = generateSequence(error) { it.cause }
        .take(5)
        .joinToString(" <- ") { cause ->
            cause.javaClass.name + (cause.message?.takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
        }

    private data class PropertyKey(val propertyId: Int, val areaId: Int)

    private data class PropertyConfig(
        val propertyId: Int,
        val access: Int,
        val changeMode: Int,
        val minSampleRate: Float,
        val maxSampleRate: Float,
        val areaIds: List<Int>,
    ) {
        fun isReadable(): Boolean = access and ACCESS_READ != 0

        fun isReadableAndDynamic(): Boolean = isReadable() && changeMode != CHANGE_MODE_STATIC

        fun expectedUpdateIntervalMillis(): Long? =
            if (changeMode == CHANGE_MODE_CONTINUOUS) STALE_AFTER_MILLIS else null

        fun subscriptionRate(): Float {
            if (changeMode != CHANGE_MODE_CONTINUOUS) return SENSOR_RATE_ONCHANGE
            val lower = max(minSampleRate, SENSOR_RATE_ONCHANGE)
            val upper = if (maxSampleRate > 0f) max(maxSampleRate, lower) else LIVE_RATE_HZ
            return min(max(LIVE_RATE_HZ, lower), upper)
        }
    }

    private object BigDecimalCompat {
        fun format(value: Double): String = if (value.isFinite()) {
            java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        } else {
            value.toString()
        }
    }

    companion object {
        private const val VEHICLE_CLASS = "android.hardware.automotive.vehicle.V2_0.IVehicle"
        private const val VALUE_CLASS = "android.hardware.automotive.vehicle.V2_0.VehiclePropValue"
        private const val CAR_CLASS = "android.car.Car"
        private const val PROPERTY_CALLBACK_CLASS =
            "android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback"
        private const val PROPERTY_SERVICE = "property"
        private const val STATUS_OK = 0
        private const val ACCESS_READ = 1
        private const val CHANGE_MODE_STATIC = 0
        private const val CHANGE_MODE_CONTINUOUS = 2
        private const val SENSOR_RATE_ONCHANGE = 0f
        private const val LIVE_RATE_HZ = 5f
        private const val STALE_AFTER_MILLIS = 15_000L
        private const val MAX_LOG_VALUE_LENGTH = 240
        private const val CALLBACK_TIMEOUT_SECONDS = 2L

        private const val PROPERTY_TYPE_MASK = 0x00ff0000
        private const val TYPE_STRING = 0x00100000
        private const val TYPE_BOOLEAN = 0x00200000
        private const val TYPE_INT32 = 0x00400000
        private const val TYPE_INT32_VEC = 0x00410000
        private const val TYPE_INT64 = 0x00500000
        private const val TYPE_INT64_VEC = 0x00510000
        private const val TYPE_FLOAT = 0x00600000
        private const val TYPE_FLOAT_VEC = 0x00610000
        private const val TYPE_BYTES = 0x00700000
        private const val TYPE_MIXED = 0x00e00000
    }
}
