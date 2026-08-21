package com.geelydiagnostics.app

import android.hardware.automotive.vehicle.V2_0.IVehicleCallback
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue
import java.io.Closeable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * VHAL boundary. HIDL supplies the property catalog, initial values, and change callbacks.
 */
internal class VhalReadOnlyClient(
    private val profile: VhalProfile,
    private val sink: ReadOnlySink,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "VhalReadOnly").apply { isDaemon = true }
    }

    @Volatile
    private var closed = false
    private var vehicleService: Any? = null
    private var vehicleCallback: IVehicleCallback? = null
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
                        chartable = config.isChartable(specs[config.propertyId]),
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

            val callbackCount = subscribeToChanges(vehicleClass, service, configs)
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
        val methods = (vehicleClass.methods.asSequence() + service.javaClass.methods.asSequence())
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
            method.invoke(service)
        } else {
            invokeCallback(method, service) { args ->
                status = (args.firstOrNull() as? Number)?.toInt()
                args.lastOrNull { it is List<*> }
            }
        }
        if (status != null && status != STATUS_OK) {
            throw IllegalStateException("getAllPropConfigs status=$status")
        }
        val rawConfigs = result as? List<*>
            ?: throw IllegalStateException("getAllPropConfigs() did not return a list")
        val parsedConfigs = rawConfigs.mapNotNull(::readConfig)
        val configs = parsedConfigs
            .groupBy(PropertyConfig::propertyId)
            .map { (_, duplicates) -> duplicates.merge() }
            .sortedBy(PropertyConfig::propertyId)
        sink.onLog(
            "VHAL configs via getAllPropConfigs/${method.parameterCount}: " +
                "${rawConfigs.size} received, ${parsedConfigs.size} parsed, ${configs.size} unique",
        )
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
            config.isChartable(spec),
        )
    } else try {
        val returned = getProperty(vehicleClass, service, config.propertyId, areaId)
        recordFor(
            config.propertyId,
            areaId,
            spec,
            extractRaw(returned, spec?.valueType),
            expectedUpdateIntervalMillis = config.expectedUpdateIntervalMillis(),
            chartable = config.isChartable(spec),
        )
    } catch (error: Throwable) {
        recordFor(
            config.propertyId,
            areaId,
            spec,
            null,
            describe(error),
            config.expectedUpdateIntervalMillis(),
            config.isChartable(spec),
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
        chartable: Boolean = false,
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
        chartable = chartable,
    )

    private fun subscribeToChanges(
        vehicleClass: Class<*>,
        service: Any,
        configs: List<PropertyConfig>,
    ): Int {
        if (closed) return 0
        return try {
            val dynamicConfigs = configs.asSequence()
                .filter(PropertyConfig::isReadableAndDynamic)
                .distinctBy(PropertyConfig::propertyId)
                .toList()
            if (dynamicConfigs.isEmpty()) return 0

            val callback = object : IVehicleCallback.Stub() {
                override fun onPropertyEvent(values: ArrayList<VehiclePropValue>) {
                    values.forEach(::queueHidlValue)
                }

                override fun onPropertySet(value: VehiclePropValue) = Unit

                override fun onPropertySetError(errorCode: Int, propertyId: Int, areaId: Int) {
                    sink.onLog(
                        "VHAL callback error=$errorCode property=${hexId(propertyId)} " +
                            "area=${hexId(areaId)}",
                    )
                }
            }
            val subscribe = (vehicleClass.methods.asSequence() + service.javaClass.methods.asSequence())
                .firstOrNull { method ->
                    method.name == "subscribe" && method.parameterCount == 2 &&
                        method.parameterTypes[0].isInstance(callback)
                } ?: throw NoSuchMethodException("IVehicle.subscribe(callback, options)")
            val optionsClass = Class.forName(SUBSCRIBE_OPTIONS_CLASS)
            val options = ArrayList<Any>(dynamicConfigs.size)
            dynamicConfigs.forEach { config ->
                options += optionsClass.getDeclaredConstructor().newInstance().also { option ->
                    optionsClass.getField("propId").setInt(option, config.propertyId)
                    optionsClass.getField("sampleRate").setFloat(option, HIDL_SAMPLE_RATE)
                    optionsClass.getField("flags").setInt(option, SUBSCRIBE_FLAG_EVENTS_FROM_CAR)
                }
            }

            val batchStatus = subscribe.status(service, callback, options)
            val successfulIds = if (batchStatus == STATUS_OK) {
                dynamicConfigs.map(PropertyConfig::propertyId)
            } else {
                sink.onLog(
                    "VHAL HIDL batch subscription status=$batchStatus; " +
                        "retrying ${options.size} properties individually",
                )
                val subscribed = mutableListOf<Int>()
                val failureStatuses = linkedMapOf<Int, Int>()
                dynamicConfigs.zip(options).forEach { (config, option) ->
                    val status = subscribe.status(service, callback, arrayListOf(option))
                    if (status == STATUS_OK) {
                        subscribed += config.propertyId
                    } else if (failureStatuses.size < MAX_SUBSCRIBE_FAILURE_SAMPLES) {
                        failureStatuses[config.propertyId] = status
                    }
                }
                if (failureStatuses.isNotEmpty()) {
                    sink.onLog(
                        "VHAL HIDL subscription failure samples: " +
                            failureStatuses.entries.joinToString { (id, status) ->
                                "${hexId(id)}=$status"
                            },
                    )
                }
                subscribed
            }
            if (successfulIds.isEmpty()) {
                throw IllegalStateException("IVehicle.subscribe rejected all properties (batch=$batchStatus)")
            }

            vehicleService = service
            vehicleCallback = callback
            subscribedIds += successfulIds
            sink.onLog(
                "VHAL HIDL subscription: ${subscribedIds.size}/${dynamicConfigs.size} properties",
            )
            subscribedIds.size
        } catch (error: Throwable) {
            sink.onLog("VHAL HIDL subscription unavailable: ${describe(error)}")
            0
        }
    }

    private fun queueHidlValue(vehiclePropValue: VehiclePropValue) {
        if (closed) return
        executor.execute {
            if (closed) return@execute
            runCatching {
                val type = vehiclePropValue.javaClass
                val propertyId = type.getField("prop").getInt(vehiclePropValue)
                val areaId = type.getField("areaId").getInt(vehiclePropValue)
                val oldRecord = recordsByKey[PropertyKey(propertyId, areaId)]
                    ?: recordsByKey[PropertyKey(propertyId, 0)]
                    ?: return@runCatching
                val spec = oldRecord.profilePropertyId?.let { profilePropertyId ->
                    VhalProfileRegistry.signals(profile).firstOrNull { it.propertyId == profilePropertyId }
                }
                val raw = extractRaw(vehiclePropValue, spec?.valueType)
                val decoded = if (spec != null) VhalProfileRegistry.decode(spec, raw) else ApiValue.raw(raw.text)
                sink.onSensorValueChanged(VehicleDataSource.VHAL, propertyId, decoded, oldRecord.areaId)
                logChangedValue(oldRecord, decoded, "HIDL")
            }.onFailure { sink.onLog("VHAL live value skipped: ${describe(it)}") }
        }
    }

    private fun Method.status(receiver: Any, callback: IVehicleCallback, options: ArrayList<Any>): Int =
        (invoke(receiver, callback, options) as? Number)?.toInt() ?: STATUS_OK

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
        val service = vehicleService
        val callback = vehicleCallback
        if (service != null && callback != null) {
            val unsubscribe = service.javaClass.methods.firstOrNull {
                it.name == "unsubscribe" && it.parameterCount == 2
            }
            if (unsubscribe == null) {
                sink.onLog("VHAL HIDL unsubscribe unavailable")
            } else {
                var failures = 0
                subscribedIds.forEach { propertyId ->
                    if (runCatching { unsubscribe.invoke(service, callback, propertyId) }.isFailure) failures++
                }
                if (failures > 0) sink.onLog("VHAL HIDL unsubscribe failures: $failures")
            }
        }
        subscribedIds.clear()
        vehicleCallback = null
        vehicleService = null
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
        val areaIds: List<Int>,
    ) {
        fun isReadable(): Boolean = access and ACCESS_READ != 0

        fun isReadableAndDynamic(): Boolean = isReadable() && changeMode != CHANGE_MODE_STATIC

        fun expectedUpdateIntervalMillis(): Long? =
            if (changeMode == CHANGE_MODE_CONTINUOUS) STALE_AFTER_MILLIS else null

        fun isChartable(spec: VhalSignalSpec?): Boolean = if (spec == null) {
            changeMode == CHANGE_MODE_CONTINUOUS && propertyTypeIsScalarNumber(propertyId)
        } else {
            spec.unit != null &&
                (spec.valueType == VhalValueType.INT || spec.valueType == VhalValueType.FLOAT)
        }
    }

    private fun List<PropertyConfig>.merge(): PropertyConfig {
        val first = first()
        return first.copy(
            access = fold(0) { result, config -> result or config.access },
            changeMode = maxOf { it.changeMode },
            areaIds = flatMap(PropertyConfig::areaIds).distinct(),
        )
    }

    private object BigDecimalCompat {
        fun format(value: Double): String = if (value.isFinite()) {
            java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        } else {
            value.toString()
        }
    }

    companion object {
        private fun propertyTypeIsScalarNumber(propertyId: Int): Boolean = when (
            propertyId and PROPERTY_TYPE_MASK
        ) {
            TYPE_INT32, TYPE_INT64, TYPE_FLOAT -> true
            else -> false
        }

        private const val VEHICLE_CLASS = "android.hardware.automotive.vehicle.V2_0.IVehicle"
        private const val VALUE_CLASS = "android.hardware.automotive.vehicle.V2_0.VehiclePropValue"
        private const val SUBSCRIBE_OPTIONS_CLASS =
            "android.hardware.automotive.vehicle.V2_0.SubscribeOptions"
        private const val STATUS_OK = 0
        private const val SUBSCRIBE_FLAG_EVENTS_FROM_CAR = 1
        private const val HIDL_SAMPLE_RATE = 0f
        private const val MAX_SUBSCRIBE_FAILURE_SAMPLES = 8
        private const val ACCESS_READ = 1
        private const val CHANGE_MODE_STATIC = 0
        private const val CHANGE_MODE_CONTINUOUS = 2
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
