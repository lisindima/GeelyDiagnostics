package com.geelydiagnostics.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ecarx.xui.adaptapi.FunctionStatus
import com.ecarx.xui.adaptapi.binder.IConnectable
import com.ecarx.xui.adaptapi.car.Car
import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.base.ICarFunction
import com.ecarx.xui.adaptapi.car.base.ICarInfo
import com.ecarx.xui.adaptapi.car.diagnostics.IDiagnostics
import com.ecarx.xui.adaptapi.car.diagnostics.IDtcManager
import com.ecarx.xui.adaptapi.car.sensor.ISensor
import com.ecarx.xui.adaptapi.car.vehicle.IVehicle
import java.io.Closeable
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The only ECARX boundary in the application.
 *
 * It uses getters, support checks and listener registration only. It deliberately has no
 * vehicle setters, DTC clearing, shell/CAN access or diagnostic monitor activation.
 */
class EcarxReadOnlyClient(
    context: Context,
    private val sink: ReadOnlySink,
) : Closeable {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "EcarxReadOnly").apply { isDaemon = true }
    }

    @Volatile
    private var closed = false

    private var car: ICar? = null
    private var connectable: IConnectable? = null
    private var dtcManager: IDtcManager? = null
    private var sensorManager: ISensor? = null
    private var dtcWatcherRegistered = false
    private var sensorListenerRegistered = false
    private var sensorSubscriptionCount = 0
    private var sensorSpecsById: Map<Int, SensorSpec> = emptyMap()

    private val connectWatcher = object : IConnectable.IConnectWatcher {
        override fun onConnected() {
            if (closed) return
            sink.onLog("IConnectable.onConnected()")
            sink.onCarStatus(ReadStatus.AVAILABLE, "CONNECTED")
            submitRefresh("connection callback")
        }

        override fun onDisConnected() {
            if (closed) return
            sink.onLog("IConnectable.onDisConnected()")
            sink.onCarStatus(ReadStatus.ERROR, "DISCONNECTED")
        }
    }

    private val dtcWatcher = object : IDtcManager.IDtcInfoWatcher {
        override fun onDtcInfosChanged(list: MutableList<IDtcManager.IDtcInfo>?) {
            if (closed) return
            val safeList = list.orEmpty()
            sink.onLog("DTC watcher: ${safeList.size} records")
            sink.onDtcsChanged(readDtcRecords(safeList))
        }
    }

    private val sensorListener = object : ISensor.ISensorListener {
        override fun onSensorEventChanged(type: Int, value: Int) {
            if (closed) return
            val spec = sensorSpecsById[type]
            val decoded = spec?.let { VendorValueDecoder.sensor(it.apiName, value) }
                ?: ApiValue.raw(value.toString())
            sink.onSensorValueChanged(VehicleDataSource.ECARX, type, decoded)
        }

        override fun onSensorSupportChanged(type: Int, status: FunctionStatus?) {
            if (!closed) sink.onSensorSupportChanged(VehicleDataSource.ECARX, type, status.toApiSupport())
        }

        override fun onSensorValueChanged(type: Int, value: Float) {
            if (closed) return
            val apiName = sensorSpecsById[type]?.apiName
            val decoded = apiName?.let { VendorValueDecoder.sensor(it, value) }
                ?: ApiValue.raw(formatFloat(value))
            sink.onSensorValueChanged(VehicleDataSource.ECARX, type, decoded)
        }
    }

    fun start() {
        sink.onCarStatus(ReadStatus.CHECKING, "Car.create()")
        sink.onDiagnosticsStatus(ReadStatus.CHECKING)
        sink.onDtcManagerStatus(ReadStatus.CHECKING)
        sink.onSensorStatus(ReadStatus.CHECKING)
        sink.onCarInfoStatus(ReadStatus.CHECKING)
        sink.onFunctionStatus(ReadStatus.CHECKING)
        executor.execute(::initialize)
    }

    private fun initialize() {
        if (closed) return
        sink.onLog("Read-only scan started on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val metrics = appContext.resources.displayMetrics
        val configuration = appContext.resources.configuration
        sink.onLog(
            "Display: ${metrics.widthPixels}x${metrics.heightPixels}px, " +
                "density=${metrics.density}, fontScale=${configuration.fontScale}, " +
                "orientation=${configuration.orientation}",
        )
        logPermission("com.geely.settings.permission.LDSDK_MESSAGE")
        logPermission("com.geely.settings.permission.QDAS_MESSAGE")
        logPermission("geely.oneos.permission.SERVICE")

        val createdCar = try {
            sink.onLog("Car class loader: ${Car::class.java.classLoader}")
            Car.create(appContext)
        } catch (error: Throwable) {
            failCar("Car.create()", error)
            return
        }

        if (createdCar == null) {
            failCar("Car.create() returned null", null)
            return
        }

        car = createdCar
        sink.onCarStatus(ReadStatus.AVAILABLE, "CREATED")
        sink.onLog("Car.create(): OK (${createdCar.javaClass.name})")

        if (createdCar is IConnectable) {
            connectable = createdCar
            try {
                createdCar.registerConnectWatcher(connectWatcher)
                sink.onLog("registerConnectWatcher(): OK; waiting for callbacks")
            } catch (error: Throwable) {
                sink.onLog("registerConnectWatcher(): ${describe(error)}", error)
            }
        } else {
            sink.onLog("ICar does not implement IConnectable; reading immediately")
        }

        refreshAll("initial attempt")
    }

    private fun submitRefresh(trigger: String) {
        if (closed) return
        try {
            executor.execute { refreshAll(trigger) }
        } catch (_: RuntimeException) {
            // Executor can already be shutting down during Activity destruction.
        }
    }

    private fun refreshAll(trigger: String) {
        if (closed) return
        sink.onLog("Read-only refresh: $trigger")
        readDiagnostics()
        readSensors()
        readCarInfo()
        readFunctions()
    }

    private fun readDiagnostics() {
        if (closed) return
        sink.onDiagnosticsStatus(ReadStatus.CHECKING, "getDiagnosticManager()")
        sink.onDtcManagerStatus(ReadStatus.CHECKING, "getDtcManager()")

        val diagnostics: IDiagnostics = try {
            car?.getDiagnosticManager()
                ?: throw IllegalStateException("getDiagnosticManager() returned null")
        } catch (error: Throwable) {
            sink.onDiagnosticsStatus(ReadStatus.ERROR, describe(error))
            sink.onDtcManagerStatus(ReadStatus.ERROR, "Diagnostics unavailable")
            sink.onLog("getDiagnosticManager(): ${describe(error)}", error)
            return
        }

        sink.onDiagnosticsStatus(ReadStatus.AVAILABLE, "AVAILABLE")
        val manager: IDtcManager = try {
            diagnostics.getDtcManager()
                ?: throw IllegalStateException("getDtcManager() returned null")
        } catch (error: Throwable) {
            sink.onDtcManagerStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getDtcManager(): ${describe(error)}", error)
            return
        }

        dtcManager = manager
        sink.onDtcManagerStatus(ReadStatus.AVAILABLE, "AVAILABLE")
        try {
            val infos = manager.getDtcInfos()
                ?: throw IllegalStateException("getDtcInfos() returned null")
            sink.onLog("getDtcInfos(): ${infos.size} records")
            sink.onDtcsChanged(readDtcRecords(infos))
        } catch (error: Throwable) {
            sink.onDtcManagerStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getDtcInfos(): ${describe(error)}", error)
            return
        }

        if (!dtcWatcherRegistered) {
            try {
                dtcWatcherRegistered = manager.registerWatcher(dtcWatcher)
                sink.onLog("DTC watcher registered: $dtcWatcherRegistered")
            } catch (error: Throwable) {
                sink.onLog("DTC watcher registration: ${describe(error)}", error)
            }
        }
    }

    private fun readSensors() {
        sink.onSensorStatus(ReadStatus.CHECKING, "getSensorManager()")
        val manager = try {
            car?.getSensorManager()
                ?: throw IllegalStateException("getSensorManager() returned null")
        } catch (error: Throwable) {
            sink.onSensorStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getSensorManager(): ${describe(error)}", error)
            return
        }
        sensorManager = manager

        val specs = try {
            reflectSensorSpecs()
        } catch (error: Throwable) {
            sink.onSensorStatus(ReadStatus.ERROR, "Sensor catalog: ${describe(error)}")
            sink.onLog("Sensor catalog: ${describe(error)}", error)
            return
        }
        sensorSpecsById = specs.associateBy(SensorSpec::id)
        val records = specs.map { spec -> readSensor(manager, spec) }
        sink.onSensorsChanged(VehicleDataSource.ECARX, records)

        if (!sensorListenerRegistered) {
            records.filter { it.support.isSupported }.forEach { record ->
                try {
                    val registered = if (sensorSpecsById.getValue(record.id).continuous) {
                        manager.registerListener(sensorListener, record.id, ISensor.RATE_UI)
                    } else {
                        manager.registerListener(sensorListener, record.id)
                    }
                    if (registered) sensorSubscriptionCount++
                } catch (error: Throwable) {
                    sink.onLog("Sensor listener ${record.apiName}: ${describe(error)}", error)
                }
            }
            sensorListenerRegistered = sensorSubscriptionCount > 0
        }

        val supported = records.count { it.support.isSupported }
        sink.onSensorStatus(
            ReadStatus.AVAILABLE,
            "$supported из ${records.size}; live-подписок: $sensorSubscriptionCount",
        )
        sink.onLog("Sensors: $supported supported of ${records.size}")
    }

    private fun readSensor(manager: ISensor, spec: SensorSpec): SensorRecord {
        return try {
            val support = manager.isSensorSupported(spec.id).toApiSupport()
            val value = if (support.isSupported) {
                if (spec.continuous) {
                    VendorValueDecoder.sensor(spec.apiName, manager.getSensorLatestValue(spec.id))
                } else {
                    VendorValueDecoder.sensor(spec.apiName, manager.getSensorEvent(spec.id))
                }
            } else {
                ApiValue.unavailable
            }
            SensorRecord(
                id = spec.id,
                apiName = spec.apiName,
                title = spec.title,
                value = value,
                valueKind = if (spec.continuous) "float" else "event/int",
                support = support,
            )
        } catch (error: Throwable) {
            SensorRecord(
                id = spec.id,
                apiName = spec.apiName,
                title = spec.title,
                value = ApiValue.unavailable,
                valueKind = if (spec.continuous) "float" else "event/int",
                support = ApiSupportStatus.ERROR,
                error = describe(error),
            )
        }
    }

    private fun readCarInfo() {
        sink.onCarInfoStatus(ReadStatus.CHECKING, "getCarInfoManager()")
        val manager = try {
            car?.getCarInfoManager()
                ?: throw IllegalStateException("getCarInfoManager() returned null")
        } catch (error: Throwable) {
            sink.onCarInfoStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getCarInfoManager(): ${describe(error)}", error)
            return
        }

        val specs = try {
            reflectCarInfoSpecs()
        } catch (error: Throwable) {
            sink.onCarInfoStatus(ReadStatus.ERROR, "Car info catalog: ${describe(error)}")
            sink.onLog("Car info catalog: ${describe(error)}", error)
            return
        }
        val records = specs.map { spec ->
            try {
                val support = manager.isCarInfoSupported(spec.id).toApiSupport()
                val rawValue: Any? = if (support.isSupported) {
                    when (spec.kind) {
                        CarInfoKind.CONFIG -> manager.getCarInfoConfig(spec.id)
                        CarInfoKind.FLOAT -> manager.getCarInfoFloat(spec.id)
                        CarInfoKind.INT -> manager.getCarInfoInt(spec.id)
                        CarInfoKind.INTS -> manager.getCarInfoInts(spec.id)
                        CarInfoKind.MAP -> manager.getCarInfoMap(spec.id)
                        CarInfoKind.STRING -> manager.getCarInfoString(spec.id)
                    }
                } else {
                    null
                }
                VehicleInfoRecord(
                    id = spec.id,
                    apiName = spec.apiName,
                    title = spec.title,
                    value = formatCarInfoValue(spec, rawValue),
                    support = support,
                )
            } catch (error: Throwable) {
                VehicleInfoRecord(
                    id = spec.id,
                    apiName = spec.apiName,
                    title = spec.title,
                    value = ApiValue.unavailable,
                    support = ApiSupportStatus.ERROR,
                    error = describe(error),
                )
            }
        }
        sink.onVehicleInfoChanged(records)
        val supported = records.count { it.support.isSupported }
        sink.onCarInfoStatus(ReadStatus.AVAILABLE, "$supported из ${records.size}")
        sink.onLog("Vehicle info: $supported supported of ${records.size}")
    }

    private fun readFunctions() {
        sink.onFunctionStatus(ReadStatus.CHECKING, "getICarFunction()")
        val manager: ICarFunction = try {
            car?.getICarFunction()
                ?: throw IllegalStateException("getICarFunction() returned null")
        } catch (error: Throwable) {
            sink.onFunctionStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getICarFunction(): ${describe(error)}", error)
            return
        }

        val specs = try {
            reflectFunctionSpecs()
        } catch (error: Throwable) {
            sink.onFunctionStatus(ReadStatus.ERROR, "Function catalog: ${describe(error)}")
            sink.onLog("Function catalog: ${describe(error)}", error)
            return
        }
        val records = specs.map { spec ->
            try {
                val support = manager.isFunctionSupported(spec.id).toApiSupport()
                var rawValue: Int? = null
                var supportedValues = ""
                var supportedRawValues: IntArray? = null
                var zones = ""
                val readErrors = mutableListOf<String>()
                if (support.isSupported) {
                    runCatching { manager.getFunctionValue(spec.id) }
                        .onSuccess { rawValue = it }
                        .onFailure { readErrors += "value: ${describe(it)}" }
                    runCatching { manager.getSupportedFunctionValue(spec.id) }
                        .onSuccess {
                            supportedRawValues = it
                            supportedValues = it?.joinToString().orEmpty()
                        }
                        .onFailure { readErrors += "values: ${describe(it)}" }
                    runCatching { manager.getSupportedFunctionZones(spec.id) }
                        .onSuccess { zones = it?.joinToString().orEmpty() }
                        .onFailure { readErrors += "zones: ${describe(it)}" }
                }
                VehicleFunctionRecord(
                    id = spec.id,
                    apiName = spec.apiName,
                    title = spec.title,
                    value = rawValue?.let { VendorValueDecoder.function(spec.apiName, it, supportedRawValues) }
                        ?: ApiValue.unavailable,
                    supportedValues = supportedValues,
                    zones = zones,
                    support = support,
                    error = readErrors.joinToString("; "),
                )
            } catch (error: Throwable) {
                VehicleFunctionRecord(
                    id = spec.id,
                    apiName = spec.apiName,
                    title = spec.title,
                    support = ApiSupportStatus.ERROR,
                    error = describe(error),
                )
            }
        }
        sink.onFunctionsChanged(records)
        val supported = records.count { it.support.isSupported }
        sink.onFunctionStatus(ReadStatus.AVAILABLE, "$supported из ${records.size}")
        sink.onLog("Vehicle functions: $supported supported of ${records.size}")
    }

    private fun readDtcRecords(infos: List<IDtcManager.IDtcInfo>): List<DtcRecord> =
        infos.mapIndexedNotNull { index, info ->
            try {
                DtcRecord(
                    code = info.getDtcCode().orEmpty(),
                    id = info.getDtcId().orEmpty(),
                    ecuType = info.getEcuType(),
                    status = info.getStatus(),
                    tickTime = info.getTicktime(),
                )
            } catch (error: Throwable) {
                sink.onLog("DTC[$index] read failed: ${describe(error)}", error)
                null
            }
        }

    private fun reflectSensorSpecs(): List<SensorSpec> =
        intConstants(ISensor::class.java, "SENSOR_TYPE_")
            .map { (name, id) ->
                SensorSpec(
                    id = id,
                    apiName = name,
                    title = EcarxSensorMetadata.fields[name]?.title ?: prettyName(name, "SENSOR_TYPE_"),
                    continuous = id and INFO_TYPE_MASK == SENSOR_TYPE_FLOAT,
                )
            }

    private fun reflectCarInfoSpecs(): List<CarInfoSpec> =
        intConstants(ICarInfo::class.java)
            .mapNotNull { (name, id) ->
                val kind = when {
                    name.startsWith("CONFIG_INFO_") && !name.startsWith("CONFIG_INFO_VALUE_") -> CarInfoKind.CONFIG
                    name.startsWith("FLT_INFO_") -> CarInfoKind.FLOAT
                    name.startsWith("INT_INFO_") && name != "INT_INFO_FUEL_TYPES" -> CarInfoKind.INT
                    name.startsWith("INTS_INFO_") -> CarInfoKind.INTS
                    name.startsWith("MAP_INFO_") -> CarInfoKind.MAP
                    name.startsWith("STRING_INFO_") -> CarInfoKind.STRING
                    else -> null
                } ?: return@mapNotNull null
                CarInfoSpec(
                    id = id,
                    apiName = name,
                    title = EcarxCarInfoMetadata.field(name)?.title
                        ?: prettyName(name, "CONFIG_INFO_", "FLT_INFO_", "INT_INFO_", "INTS_INFO_", "MAP_INFO_", "STRING_INFO_"),
                    kind = kind,
                )
            }
            .distinctBy(CarInfoSpec::id)
            .sortedBy(CarInfoSpec::apiName)

    private fun reflectFunctionSpecs(): List<FunctionSpec> =
        intConstants(IVehicle::class.java, "SETTING_FUNC_")
            .filter { (_, id) -> id >= MIN_FUNCTION_ID && id and 0xff == 0 }
            .map { (name, id) ->
                FunctionSpec(
                    id = id,
                    apiName = name,
                    title = EcarxFunctionMetadata.fields[name]?.title ?: prettyName(name, "SETTING_FUNC_"),
                )
            }
            .distinctBy(FunctionSpec::id)
            .sortedBy(FunctionSpec::apiName)

    private fun intConstants(type: Class<*>, prefix: String? = null): List<Pair<String, Int>> =
        type.fields.asSequence()
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    field.type == Int::class.javaPrimitiveType &&
                    (prefix == null || field.name.startsWith(prefix))
            }
            .map { it.name to it.getInt(null) }
            .sortedBy(Pair<String, Int>::first)
            .toList()

    private fun formatCarInfoValue(spec: CarInfoSpec, value: Any?): ApiValue {
        if (value == null) return ApiValue.unavailable
        if (value is IntArray) {
            return VendorValueDecoder.carInfo(spec.apiName, value)
        }
        if (value is Float) return VendorValueDecoder.carInfo(spec.apiName, value)
        if (value !is Int) {
            val raw = value.toString()
            return ApiValue(display = raw.ifBlank { "пусто" }, raw = raw.ifBlank { "\"\"" })
        }

        val configLabel = if (spec.kind == CarInfoKind.CONFIG) CONFIG_VALUES[value] else null
        return VendorValueDecoder.carInfo(spec.apiName, value, configLabel)
    }

    private fun logPermission(permission: String) {
        val result = appContext.checkSelfPermission(permission)
        val text = if (result == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        sink.onLog("Permission $permission: $text")
    }

    private fun failCar(operation: String, error: Throwable?) {
        val detail = if (error == null) operation else "$operation: ${describe(error)}"
        sink.onCarStatus(ReadStatus.ERROR, detail)
        sink.onDiagnosticsStatus(ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onDtcManagerStatus(ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onSensorStatus(ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onCarInfoStatus(ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onFunctionStatus(ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onLog(detail, error)
    }

    override fun close() {
        closed = true
        if (sensorListenerRegistered) {
            try {
                sensorManager?.unregisterListener(sensorListener)
            } catch (error: Throwable) {
                sink.onLog("Sensor listener cleanup: ${describe(error)}", error)
            }
        }
        if (dtcWatcherRegistered) {
            try {
                dtcManager?.unregisterWatcher(dtcWatcher)
            } catch (error: Throwable) {
                sink.onLog("DTC watcher cleanup: ${describe(error)}", error)
            }
        }
        try {
            connectable?.unregisterConnectWatcher()
        } catch (error: Throwable) {
            sink.onLog("Connect watcher cleanup: ${describe(error)}", error)
        }
        executor.shutdownNow()
        sensorListenerRegistered = false
        sensorSubscriptionCount = 0
        dtcWatcherRegistered = false
        sensorManager = null
        dtcManager = null
        connectable = null
        car = null
    }

    private fun describe(error: Throwable): String =
        generateSequence(error) { it.cause }
            .take(5)
            .joinToString(" <- ") { throwable ->
                val message = throwable.message?.takeIf(String::isNotBlank)
                if (message == null) throwable.javaClass.name else "${throwable.javaClass.name}: $message"
            }
            .ifBlank { error.javaClass.name }

    private data class SensorSpec(
        val id: Int,
        val apiName: String,
        val title: String,
        val continuous: Boolean,
    )

    private data class CarInfoSpec(
        val id: Int,
        val apiName: String,
        val title: String,
        val kind: CarInfoKind,
    )

    private data class FunctionSpec(
        val id: Int,
        val apiName: String,
        val title: String,
    )

    private enum class CarInfoKind { CONFIG, FLOAT, INT, INTS, MAP, STRING }

    companion object {
        private const val INFO_TYPE_MASK = 0xF00000
        private const val SENSOR_TYPE_FLOAT = 0x100000
        private const val MIN_FUNCTION_ID = 0x20000000

        private val CONFIG_VALUES = mapOf(
            ICarInfo.CONFIG_INFO_VALUE_NOT_CONFIG to "Не установлено",
            ICarInfo.CONFIG_INFO_VALUE_CONFIG to "Установлено",
            ICarInfo.CONFIG_INFO_VALUE_PRELOAD to "Предустановлено",
            ICarInfo.CONFIG_INFO_VALUE_FAULT to "Ошибка конфигурации",
            ICarInfo.CONFIG_INFO_VALUE_UNKNOWN to "Неизвестно",
        )

    }
}

private val ApiSupportStatus.isSupported: Boolean
    get() = this == ApiSupportStatus.ACTIVE || this == ApiSupportStatus.NOT_ACTIVE

private fun FunctionStatus?.toApiSupport(): ApiSupportStatus = when (this) {
    FunctionStatus.active -> ApiSupportStatus.ACTIVE
    FunctionStatus.notactive -> ApiSupportStatus.NOT_ACTIVE
    FunctionStatus.notavailable -> ApiSupportStatus.NOT_AVAILABLE
    FunctionStatus.error -> ApiSupportStatus.ERROR
    null -> ApiSupportStatus.UNKNOWN
}

private fun formatFloat(value: Float): String =
    if (value.isFinite()) String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.') else value.toString()

private fun prettyName(value: String, vararg prefixes: String): String {
    var normalized = value
    prefixes.firstOrNull(normalized::startsWith)?.let { normalized = normalized.removePrefix(it) }
    return normalized.lowercase(Locale.US)
        .replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
