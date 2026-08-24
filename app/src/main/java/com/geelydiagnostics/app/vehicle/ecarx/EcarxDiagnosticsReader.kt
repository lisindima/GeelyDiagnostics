package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.model.*

import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.diagnostics.IDiagnostics
import com.ecarx.xui.adaptapi.car.diagnostics.IDtcManager
import com.geelydiagnostics.app.model.DtcRecord
import com.geelydiagnostics.app.model.ReadStatus

internal class EcarxDiagnosticsReader(
    private val sink: EcarxDataListener,
) : EcarxReader {
    private var manager: IDtcManager? = null
    private var watcherRegistered = false
    private var closed = false

    private val watcher = object : IDtcManager.IDtcInfoWatcher {
        override fun onDtcInfosChanged(list: MutableList<IDtcManager.IDtcInfo>?) {
            if (closed) return
            val safeList = list.orEmpty()
            sink.onLog("DTC watcher: ${safeList.size} records")
            sink.onDtcsChanged(safeList.toRecords())
        }
    }

    override fun read(car: ICar) {
        if (closed) return
        sink.onDiagnosticsStatus(ReadStatus.CHECKING, "getDiagnosticManager()")
        sink.onDtcManagerStatus(ReadStatus.CHECKING, "getDtcManager()")
        val diagnostics: IDiagnostics = try {
            car.getDiagnosticManager()
                ?: throw IllegalStateException("getDiagnosticManager() returned null")
        } catch (error: Throwable) {
            sink.onDiagnosticsStatus(ReadStatus.ERROR, describe(error))
            sink.onDtcManagerStatus(ReadStatus.ERROR, "Diagnostics unavailable")
            sink.onLog("getDiagnosticManager(): ${describe(error)}", error)
            return
        }
        sink.onDiagnosticsStatus(ReadStatus.AVAILABLE, "AVAILABLE")
        val current = try {
            diagnostics.getDtcManager()
                ?: throw IllegalStateException("getDtcManager() returned null")
        } catch (error: Throwable) {
            sink.onDtcManagerStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getDtcManager(): ${describe(error)}", error)
            return
        }
        manager = current
        sink.onDtcManagerStatus(ReadStatus.AVAILABLE, "AVAILABLE")
        try {
            val infos = current.getDtcInfos()
                ?: throw IllegalStateException("getDtcInfos() returned null")
            sink.onLog("getDtcInfos(): ${infos.size} records")
            sink.onDtcsChanged(infos.toRecords())
        } catch (error: Throwable) {
            sink.onDtcManagerStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getDtcInfos(): ${describe(error)}", error)
            return
        }
        if (!watcherRegistered) {
            runCatching { current.registerWatcher(watcher) }
                .onSuccess {
                    watcherRegistered = it
                    sink.onLog("DTC watcher registered: $it")
                }
                .onFailure { sink.onLog("DTC watcher registration: ${describe(it)}", it) }
        }
    }

    private fun List<IDtcManager.IDtcInfo>.toRecords(): List<DtcRecord> =
        mapIndexedNotNull { index, info ->
            runCatching {
                DtcRecord(
                    code = info.getDtcCode().orEmpty(),
                    id = info.getDtcId().orEmpty(),
                    ecuType = info.getEcuType(),
                    status = info.getStatus(),
                    tickTime = info.getTicktime(),
                )
            }.onFailure { sink.onLog("DTC[$index] read failed: ${describe(it)}", it) }.getOrNull()
        }

    override fun close() {
        closed = true
        if (watcherRegistered) {
            runCatching { manager?.unregisterWatcher(watcher) }
                .onFailure { sink.onLog("DTC watcher cleanup: ${describe(it)}", it) }
        }
        watcherRegistered = false
        manager = null
    }
}
