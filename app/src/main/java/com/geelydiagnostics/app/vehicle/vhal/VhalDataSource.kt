package com.geelydiagnostics.app.vehicle.vhal

import android.content.Context
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.mapping.VehicleMetadataStore
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfileMapping
import com.geelydiagnostics.app.vehicle.property.CarPropertyCatalog
import com.geelydiagnostics.app.vehicle.property.CarPropertyPresentations
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.MappedPropertyDecoder
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.source.VehicleParameterDataSource
import com.geelydiagnostics.app.vehicle.source.VehicleParameterSink
import java.util.concurrent.Executors

internal class VhalDataSource private constructor(
    private val profile: VehicleProfile,
    private val listener: VehicleParameterSink,
    dependencies: Dependencies,
) : VehicleParameterDataSource {
    override val source: VehiclePropertySource = VehiclePropertySource.VHAL
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "VhalDataSource").apply { isDaemon = true }
    }
    private val mapping = dependencies.mapping
    private val decoder = MappedPropertyDecoder(dependencies.catalog)
    private val gateway = dependencies.gateway
    private val configsById = mutableMapOf<Int, VhalPropertyConfig>()
    private val lastRawByKey = mutableMapOf<Pair<Int, Int>, String>()
    private var subscribedIds = emptySet<Int>()

    @Volatile
    private var closed = false

    constructor(
        context: Context,
        profile: VehicleProfile,
        listener: VehicleParameterSink,
        gatewayFactory: ((String, Throwable?) -> Unit) -> VhalGateway = ::HidlVhalGateway,
    ) : this(
        profile = profile,
        listener = listener,
        dependencies = productionDependencies(context, profile, listener, gatewayFactory),
    )

    internal constructor(
        profile: VehicleProfile,
        listener: VehicleParameterSink,
        mapping: VehicleProfileMapping,
        catalog: CarPropertyCatalog,
        gateway: VhalGateway,
    ) : this(profile, listener, Dependencies(mapping, catalog, gateway))

    override fun start() {
        listener.onParameterStatus(
            source,
            ReadStatus.CHECKING,
            "VHAL: полный каталог · профиль ${profile.key}",
        )
        executor.execute(::load)
    }

    private fun load() {
        if (closed) return
        try {
            gateway.connect()
            val configs = gateway.readConfigs()
            if (configs.isEmpty()) error("getAllPropConfigs() returned an empty catalog")
            configsById += configs.associateBy(VhalPropertyConfig::propertyId)
            val initial = configs.flatMap { config ->
                config.areaIds.map { areaId -> read(config, areaId) }
            }
            if (closed) return
            subscribedIds = runCatching {
                gateway.subscribe(configs, ::queueValue)
            }.onFailure { listener.onParameterLog(source, "subscriptions unavailable: ${describe(it)}") }
                .getOrDefault(emptySet())
            val classified = initial.map { value ->
                value.copy(autoUpdates = value.sourceSignalId in subscribedIds)
            }
            classified.forEach(::logInitial)
            listener.onParameterSnapshot(source, classified)
            val mappedCount = classified.count { it.propertyId != null }
            val mappingDetail = if (profile == VehicleProfile.RAW) {
                "RAW без расшифровки"
            } else {
                "$mappedCount расшифровано ${profile.key}"
            }
            listener.onParameterStatus(
                source,
                ReadStatus.AVAILABLE,
                "${classified.size} значений · $mappingDetail · подписки: ${subscribedIds.size}",
            )
        } catch (error: Throwable) {
            if (!closed) {
                listener.onParameterStatus(source, ReadStatus.ERROR, describe(error))
                listener.onParameterLog(source, describe(error), error)
            }
        }
    }

    private fun read(config: VhalPropertyConfig, areaId: Int): CarPropertySnapshot {
        if (!config.readable) {
            return unavailable(
                config,
                areaId,
                "Свойство присутствует в VHAL, но не помечено доступным для чтения",
            )
        }
        return try {
            val value = gateway.read(config.propertyId, areaId)
            decode(value, config, autoUpdates = false)
        } catch (error: Throwable) {
            unavailable(config, areaId, describe(error))
        }
    }

    private fun queueValue(value: VhalPropertyValue) {
        if (closed) return
        runCatching {
            executor.execute {
                if (closed) return@execute
                val config = configsById[value.propertyId] ?: return@execute
                val decoded = decode(value, config, autoUpdates = true)
                logChanged(decoded)
                listener.onParameterValue(decoded)
            }
        }
    }

    private fun decode(
        value: VhalPropertyValue,
        config: VhalPropertyConfig,
        autoUpdates: Boolean,
    ): CarPropertySnapshot {
        val signalMapping = mapping.forSignal(value.propertyId)
        return decoder.decode(
            mapping = signalMapping,
            raw = value.raw,
            sourceSignalId = value.propertyId,
            sourceSignalName = signalMapping?.signalName ?: "VHAL_${value.propertyId.hex()}",
            areaId = value.areaId,
            profileKey = signalMapping?.let { profile.key },
            sourceTimestampNanos = value.sourceTimestampNanos,
            receivedAtMillis = System.currentTimeMillis(),
            autoUpdates = autoUpdates,
        ).copy(
            valueKind = propertyType(value.propertyId),
            expectedUpdateIntervalMillis = if (config.continuous) STALE_AFTER_MILLIS else null,
        )
    }

    private fun unavailable(
        config: VhalPropertyConfig,
        areaId: Int,
        error: String,
    ): CarPropertySnapshot {
        val signalMapping = mapping.forSignal(config.propertyId)
        return CarPropertySnapshot(
            propertyId = signalMapping?.propertyId,
            value = null,
            displayValue = "—",
            rawValue = null,
            status = VehiclePropertyStatus.ERROR,
            source = VehiclePropertySource.VHAL,
            sourceSignalId = config.propertyId,
            sourceSignalName = signalMapping?.signalName ?: "VHAL_${config.propertyId.hex()}",
            areaId = areaId,
            profileKey = signalMapping?.let { profile.key },
            receivedAtMillis = System.currentTimeMillis(),
            autoUpdates = false,
            valueKind = propertyType(config.propertyId),
            expectedUpdateIntervalMillis = if (config.continuous) STALE_AFTER_MILLIS else null,
            error = error,
        )
    }

    private fun logInitial(value: CarPropertySnapshot) {
        val raw = value.rawValue?.text ?: "—"
        lastRawByKey[value.sourceSignalId to value.areaId] = raw
        listener.onParameterLog(
            source,
            "initial ${value.identity()} display=${value.displayValue.logText()} raw=${raw.logText()}",
        )
    }

    private fun logChanged(value: CarPropertySnapshot) {
        val raw = value.rawValue?.text ?: "—"
        val key = value.sourceSignalId to value.areaId
        if (lastRawByKey.put(key, raw) == raw) return
        listener.onParameterLog(
            source,
            "event ${value.identity()} display=${value.displayValue.logText()} raw=${raw.logText()}",
        )
    }

    private fun CarPropertySnapshot.identity(): String = buildString {
        append("id=")
        append(sourceSignalId.hex())
        if (areaId != 0) append(" area=${areaId.hex()}")
        append(" mapping=")
        append(profileKey ?: "RAW")
        propertyId?.let {
            append(" property=")
            append(it.rawValue)
            append(" title=")
            append(CarPropertyPresentations.get(it).title)
        }
    }

    override fun close() {
        closed = true
        gateway.close()
        executor.shutdownNow()
    }

    companion object {
        private const val STALE_AFTER_MILLIS = 15_000L
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

        private fun productionDependencies(
            context: Context,
            profile: VehicleProfile,
            listener: VehicleParameterSink,
            gatewayFactory: ((String, Throwable?) -> Unit) -> VhalGateway,
        ): Dependencies {
            val metadata = VehicleMetadataStore(context)
            return Dependencies(
                mapping = metadata.mapping(profile),
                catalog = metadata.properties,
                gateway = gatewayFactory { message, error ->
                    listener.onParameterLog(VehiclePropertySource.VHAL, message, error)
                },
            )
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
    }

    private data class Dependencies(
        val mapping: VehicleProfileMapping,
        val catalog: CarPropertyCatalog,
        val gateway: VhalGateway,
    )
}

private fun Int.hex(): String = "0x${toUInt().toString(16).padStart(8, '0')}"

private fun String.logText(): String = replace('\n', ' ').replace('\r', ' ').take(240)

private fun describe(error: Throwable): String = generateSequence(error) { it.cause }
    .take(5)
    .joinToString(" <- ") { cause ->
        cause.javaClass.name + (cause.message?.takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
    }
