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
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.property.VehicleDiscoveryProgress
import com.geelydiagnostics.app.vehicle.property.catalogSection
import com.geelydiagnostics.app.vehicle.source.VehicleParameterDataSource
import com.geelydiagnostics.app.vehicle.source.VehicleParameterSink
import java.util.concurrent.Executors

internal class VhalDataSource private constructor(
    private val profile: VehicleProfile,
    private val backend: VhalGatewayBackend,
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
    private val events = Executors.newSingleThreadExecutor { task ->
        Thread(task, "VhalEvents").apply { isDaemon = true }
    }
    private val reads = VhalReadScheduler(::read, ::unavailable)
    @Volatile private var configsById = emptyMap<Int, VhalPropertyConfig>()
    private val lastRawByKey = mutableMapOf<Pair<Int, Int>, String>()
    private val latestByKey = linkedMapOf<Pair<Int, Int>, CarPropertySnapshot>()
    private val lastEventAtNanos = mutableMapOf<Pair<Int, Int>, Long>()
    @Volatile private var subscribedIds = emptySet<Int>()

    @Volatile
    private var closed = false

    constructor(
        context: Context,
        profile: VehicleProfile,
        backend: VhalGatewayBackend,
        listener: VehicleParameterSink,
    ) : this(
        profile = profile,
        backend = backend,
        listener = listener,
        dependencies = productionDependencies(context, profile, backend, listener),
    )

    internal constructor(
        profile: VehicleProfile,
        listener: VehicleParameterSink,
        mapping: VehicleProfileMapping,
        catalog: CarPropertyCatalog,
        gateway: VhalGateway,
    ) : this(
        profile = profile,
        backend = VhalGatewayBackend.HIDL,
        listener = listener,
        dependencies = Dependencies(mapping, catalog, gateway),
    )

    override fun start() {
        listener.onParameterStatus(
            source,
            ReadStatus.CHECKING,
            "VHAL ${backend.title}: полный каталог · профиль ${profile.key}",
        )
        executor.execute(::load)
    }

    private fun load() {
        if (closed) return
        try {
            gateway.connect()
            if (closed) { gateway.close(); return }
            val configs = gateway.readConfigs()
            if (closed) return
            if (configs.isEmpty()) error("getAllPropConfigs() returned an empty catalog")
            configsById = configs.associateBy(VhalPropertyConfig::propertyId)
            val (mapped, raw) = configs.partition {
                AndroidVehiclePropertyRegistry.property(it.propertyId)?.normalizedMapping != null ||
                    mapping.forSignal(it.propertyId) != null
            }
            subscribe(mapped)
            reads.readAll(mapped, ::queueInitial)
            dispatch {
                listener.onParameterDiscovery(source, VehicleDiscoveryProgress(
                    mappedBootstrapReady = true, rawDiscoveryRunning = raw.isNotEmpty(),
                    rawDiscoveryCompleted = raw.isEmpty(),
                ))
                listener.onParameterStatus(source, ReadStatus.PARTIAL,
                    "${latestByKey.size} значений · ${backend.title} · загрузка остального каталога")
            }
            if (closed) return
            subscribe(raw)
            reads.readAll(raw, ::queueInitial)
            dispatch {
                val classified = latestByKey.values.toList()
                val mappedCount = classified.count { it.propertyId != null }
                val aospMappedCount = classified.count {
                    it.propertyId != null &&
                        it.profileKey == AndroidVehiclePropertyRegistry.PROFILE_KEY
                }
                val profileMappedCount = mappedCount - aospMappedCount
                val rawCount = classified.size - mappedCount
                val mappingDetail = if (mappedCount == 0) {
                    "без нормализации"
                } else {
                    buildList {
                        if (aospMappedCount > 0) add("$aospMappedCount AOSP")
                        if (profileMappedCount > 0) add("$profileMappedCount ${profile.key}")
                        if (rawCount > 0) add("$rawCount raw")
                    }.joinToString(" · ")
                }
                listener.onParameterStatus(
                    source,
                    ReadStatus.AVAILABLE,
                    "${classified.size} значений · ${backend.title} · $mappingDetail · " +
                        "подписки: ${subscribedIds.size}",
                )
                listener.onParameterDiscovery(source, VehicleDiscoveryProgress(
                    mappedBootstrapReady = true, rawDiscoveryCompleted = true,
                ))
            }
        } catch (error: Throwable) {
            dispatch {
                listener.onParameterStatus(source, ReadStatus.ERROR, describe(error))
                listener.onParameterLog(source, describe(error), error)
                listener.onParameterDiscovery(source, VehicleDiscoveryProgress())
            }
        }
    }

    private fun subscribe(configs: List<VhalPropertyConfig>) {
        if (closed || configs.isEmpty()) return
        subscribedIds = subscribedIds + runCatching { gateway.subscribe(configs, ::queueValue) }
            .onFailure { listener.onParameterLog(source, "subscriptions unavailable: ${describe(it)}") }
            .getOrDefault(emptySet())
    }

    private fun dispatch(action: () -> Unit) {
        if (closed) return
        runCatching { events.execute { if (!closed) action() } }
    }

    private fun queueInitial(value: CarPropertySnapshot, readStartedAtNanos: Long) = dispatch {
        val key = value.sourceSignalId to value.areaId
        // A callback arriving during a synchronous read is newer than that read's snapshot.
        if ((lastEventAtNanos[key] ?: Long.MIN_VALUE) > readStartedAtNanos) return@dispatch
        val classified = value.copy(autoUpdates = value.sourceSignalId in subscribedIds)
        latestByKey[key] = classified
        logInitial(classified)
        listener.onParameterValue(classified)
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
        val arrived = System.nanoTime()
        dispatch {
            val config = configsById[value.propertyId] ?: return@dispatch
            val key = value.propertyId to value.areaId
            lastEventAtNanos[key] = arrived
            val decoded = decode(value, config, autoUpdates = true)
            latestByKey[key] = decoded
            logChanged(decoded)
            listener.onParameterValue(decoded)
        }
    }

    private fun decode(
        value: VhalPropertyValue,
        config: VhalPropertyConfig,
        autoUpdates: Boolean,
    ): CarPropertySnapshot {
        value.error?.let { return unavailable(config, value.areaId, it).copy(
            autoUpdates = autoUpdates, sourceTimestampNanos = value.sourceTimestampNanos,
        ) }
        val androidProperty = AndroidVehiclePropertyRegistry.property(value.propertyId)
        val profileMapping = mapping.forSignal(value.propertyId)
        val signalMapping = androidProperty?.normalizedMapping ?: profileMapping
        val usesProfile = signalMapping != null && androidProperty?.normalizedMapping == null
        val decoded = decoder.decode(
            mapping = signalMapping,
            raw = value.raw,
            sourceSignalId = value.propertyId,
            sourceSignalName = signalMapping?.signalName
                ?: androidProperty?.apiName
                ?: "VHAL_${value.propertyId.hex()}",
            areaId = value.areaId,
            profileKey = when {
                usesProfile -> profile.key
                androidProperty != null -> androidProperty.profileKey
                else -> null
            },
            sourceTimestampNanos = value.sourceTimestampNanos,
            receivedAtMillis = System.currentTimeMillis(),
            autoUpdates = autoUpdates,
            sourceTitle = androidProperty?.takeUnless { usesProfile }?.titleForArea(value.areaId),
        )
        val aospValue = androidProperty
            ?.takeIf { signalMapping == null }
            ?.decode(value.raw)
        return decoded.copy(
            section = signalMapping?.propertyId?.catalogSection
                ?: androidProperty?.section
                ?: VehicleDataSection.PARAMETER,
            value = aospValue?.value ?: decoded.value,
            displayValue = aospValue?.displayValue ?: decoded.displayValue,
            decoded = aospValue?.decoded ?: decoded.decoded,
            sourceDescription = androidProperty?.takeUnless { usesProfile }?.description,
            backend = backend.name,
            valueKind = propertyType(value.propertyId),
            expectedUpdateIntervalMillis = if (config.continuous) STALE_AFTER_MILLIS else null,
        )
    }

    private fun unavailable(
        config: VhalPropertyConfig,
        areaId: Int,
        error: String,
    ): CarPropertySnapshot {
        val androidProperty = AndroidVehiclePropertyRegistry.property(config.propertyId)
        val profileMapping = mapping.forSignal(config.propertyId)
        val signalMapping = androidProperty?.normalizedMapping ?: profileMapping
        val usesProfile = signalMapping != null && androidProperty?.normalizedMapping == null
        return CarPropertySnapshot(
            section = signalMapping?.propertyId?.catalogSection
                ?: androidProperty?.section
                ?: VehicleDataSection.PARAMETER,
            propertyId = signalMapping?.propertyId,
            value = null,
            displayValue = "—",
            rawValue = null,
            status = VehiclePropertyStatus.ERROR,
            source = VehiclePropertySource.VHAL,
            sourceSignalId = config.propertyId,
            sourceSignalName = signalMapping?.signalName
                ?: androidProperty?.apiName
                ?: "VHAL_${config.propertyId.hex()}",
            sourceTitle = androidProperty?.takeUnless { usesProfile }?.titleForArea(areaId),
            sourceDescription = androidProperty?.takeUnless { usesProfile }?.description,
            areaId = areaId,
            profileKey = when {
                usesProfile -> profile.key
                androidProperty != null -> androidProperty.profileKey
                else -> null
            },
            receivedAtMillis = System.currentTimeMillis(),
            autoUpdates = false,
            valueKind = propertyType(config.propertyId),
            expectedUpdateIntervalMillis = if (config.continuous) STALE_AFTER_MILLIS else null,
            error = error,
            decoded = false,
            backend = backend.name,
            readTransform = signalMapping?.transform,
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
        reads.close()
        events.shutdownNow()
        executor.shutdownNow()
        gateway.close()
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
            backend: VhalGatewayBackend,
            listener: VehicleParameterSink,
        ): Dependencies {
            val metadata = VehicleMetadataStore(context)
            val log: (String, Throwable?) -> Unit = { message, error ->
                listener.onParameterLog(VehiclePropertySource.VHAL, message, error)
            }
            return Dependencies(
                mapping = metadata.mapping(profile),
                catalog = metadata.properties,
                gateway = when (backend) {
                    VhalGatewayBackend.CAR_PROPERTY_MANAGER -> CarPropertyManagerGateway(context, log)
                    VhalGatewayBackend.HIDL -> HidlVhalGateway(log)
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
