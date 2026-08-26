package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.model.*
import java.io.Closeable
import java.util.concurrent.Executors

/** Independent of the raw catalog scan. Freeze frames are read once, by exact INFO timestamps. */
internal class Obd2Reader(
    private val gateway: Obd2Gateway,
    private val publish: (Obd2Snapshot) -> Unit,
    private val log: (String) -> Unit,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Obd2Reader").apply { isDaemon = true } }
    @Volatile private var closed = false
    private var state = Obd2Snapshot(backend = gateway.backend)
    private var liveGeneration = 0L

    fun start(configs: List<VhalPropertyConfig>) { executor.execute { scan(configs) } }

    internal fun scan(configs: List<VhalPropertyConfig>) {
        if (closed) return
        try {
            update { copy(capabilities = gateway.discover(configs)) }
            synchronized(this) { state.capabilities }.forEach {
                log("${Obd2Properties.name(it.propertyId)}: supported=${it.supported}; ${it.detail}")
            }
            if (supports(Obd2Properties.LIVE)) {
                val subscribed = runCatching { gateway.subscribeLive { frame ->
                    synchronized(this) { liveGeneration++; updateLive(frame) }
                } }.onFailure { log("OBD2 live subscription: $it") }.getOrDefault(false)
                update { copy(autoUpdates = subscribed) }
                val generation = synchronized(this) { liveGeneration }
                val live = runCatching { gateway.readLive() }
                synchronized(this) {
                    if (generation == liveGeneration) updateLive(live.getOrElse {
                        Obd2Frame(null, error = it.toString())
                    })
                }
            }
            if (supports(Obd2Properties.INFO)) {
                val timestamps = runCatching { gateway.readTimestamps().distinct() }
                timestamps.onSuccess { values ->
                    update { copy(freezeTimestamps = values) }
                    status(Obd2Properties.INFO, ReadStatus.AVAILABLE, "${values.size} сохранённых кадров")
                    if (supports(Obd2Properties.FREEZE)) {
                        if (values.isEmpty()) status(Obd2Properties.FREEZE, ReadStatus.AVAILABLE, "Сохранённых кадров нет")
                        values.take(MAX_FREEZE_FRAMES).forEach { timestamp ->
                            if (closed) return
                            val frame = (runCatching { gateway.readFreeze(timestamp) }
                                .getOrElse { Obd2Frame(timestamp, error = it.toString()) }
                                ?: Obd2Frame(timestamp, error = "Кадр уже недоступен"))
                                .copy(requestedTimestampNanos = timestamp)
                            update { copy(freezeFrames = freezeFrames + frame) }
                        }
                        if (values.isNotEmpty()) {
                            val failed = synchronized(this) { state.freezeFrames.count { it.error.isNotEmpty() } }
                            status(Obd2Properties.FREEZE, when (failed) {
                                0 -> ReadStatus.AVAILABLE
                                minOf(values.size, MAX_FREEZE_FRAMES) -> ReadStatus.ERROR
                                else -> ReadStatus.PARTIAL
                            },
                                "Прочитано ${minOf(values.size, MAX_FREEZE_FRAMES)} из ${values.size}; ошибок $failed")
                        }
                    }
                }.onFailure {
                    status(Obd2Properties.INFO, ReadStatus.ERROR, it.toString())
                    if (supports(Obd2Properties.FREEZE)) status(Obd2Properties.FREEZE, ReadStatus.NOT_CHECKED,
                        "Не прочитано: не удалось получить timestamps из INFO")
                }
            } else if (supports(Obd2Properties.FREEZE)) {
                status(Obd2Properties.FREEZE, ReadStatus.NOT_CHECKED, "Не прочитано: INFO недоступен, timestamp неизвестен")
            }
        } catch (error: Throwable) {
            update { copy(detail = error.toString(), capabilities = Obd2Properties.readable.map {
                Obd2Capability(it, status = ReadStatus.ERROR, detail = error.toString())
            }) }
            log("OBD2 discovery: $error")
        }
    }

    @Synchronized private fun supports(id: Int) = !closed && state.capabilities.any { it.propertyId == id && it.supported == true }

    @Synchronized private fun updateLive(frame: Obd2Frame?) {
        update { copy(live = frame) }
        status(Obd2Properties.LIVE, if (frame?.error.isNullOrEmpty()) ReadStatus.AVAILABLE else ReadStatus.ERROR,
            frame?.error?.takeIf { it.isNotEmpty() } ?: if (frame == null) "Кадр пока не получен" else
                "${frame.integers.size + frame.floats.size} значений")
    }

    private fun status(id: Int, status: ReadStatus, detail: String) {
        update { copy(capabilities = capabilities.map {
            if (it.propertyId == id) it.copy(status = status, detail = detail) else it
        }) }
        // Do not log every live frame; snapshots/export retain current data.
        if (id != Obd2Properties.LIVE || status == ReadStatus.ERROR) log("${Obd2Properties.name(id)}: $detail")
    }

    @Synchronized private fun update(block: Obd2Snapshot.() -> Obd2Snapshot) {
        if (!closed) { state = state.block(); publish(state) }
    }

    override fun close() {
        closed = true
        executor.shutdownNow()
        gateway.close()
    }

    companion object { const val MAX_FREEZE_FRAMES = 32 }
}
