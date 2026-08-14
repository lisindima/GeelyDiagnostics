package com.cityray.diagnosticsprobe

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ecarx.xui.adaptapi.binder.IConnectable
import com.ecarx.xui.adaptapi.car.Car
import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.diagnostics.IDiagnostics
import com.ecarx.xui.adaptapi.car.diagnostics.IDtcManager
import java.io.Closeable

/**
 * The complete ECARX boundary for the first experiment.
 *
 * Intentionally allowed calls:
 *  - Car.create(Context)
 *  - IConnectable watcher registration/cleanup
 *  - ICar.getDiagnosticManager()
 *  - IDiagnostics.getDtcManager()
 *  - IDtcManager.getDtcInfos()
 *  - IDtcManager watcher registration/cleanup
 *
 * There are no setters, shell commands, DTC clearing calls, CAN commands, or raw signal writes.
 */
class EcarxDiagnosticsClient(
    context: Context,
    private val sink: ProbeSink,
) : Closeable {

    private val appContext = context.applicationContext
    private var car: ICar? = null
    private var connectable: IConnectable? = null
    private var dtcManager: IDtcManager? = null
    private var dtcWatcherRegistered = false
    private var closed = false

    private val connectWatcher = object : IConnectable.IConnectWatcher {
        override fun onConnected() {
            if (closed) return
            sink.onLog("IConnectable.onConnected()")
            sink.onCarStatus(ProbeStatus.AVAILABLE, "CONNECTED")
            probeDiagnostics("connection callback")
        }

        override fun onDisConnected() {
            if (closed) return
            sink.onLog("IConnectable.onDisConnected()")
            sink.onCarStatus(ProbeStatus.ERROR, "DISCONNECTED")
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

    fun start() {
        sink.onCarStatus(ProbeStatus.CHECKING, "Car.create()")
        sink.onDiagnosticsStatus(ProbeStatus.CHECKING)
        sink.onDtcManagerStatus(ProbeStatus.CHECKING)

        sink.onLog("Probe started on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val metrics = appContext.resources.displayMetrics
        val orientation = appContext.resources.configuration.orientation
        sink.onLog(
            "Display: ${metrics.widthPixels}x${metrics.heightPixels}px, " +
                "density=${metrics.density}, orientation=$orientation",
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
        sink.onCarStatus(ProbeStatus.AVAILABLE, "CREATED")
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
            sink.onLog("ICar does not implement IConnectable; probing immediately")
        }

        // GInputBridge also subscribes after Car.create(), without calling connect().
        // This immediate attempt records whether managers are already available.
        probeDiagnostics("initial attempt")
    }

    private fun probeDiagnostics(trigger: String) {
        if (closed) return
        sink.onLog("Diagnostics probe: $trigger")
        sink.onDiagnosticsStatus(ProbeStatus.CHECKING, "getDiagnosticManager()")
        sink.onDtcManagerStatus(ProbeStatus.CHECKING, "getDtcManager()")

        val diagnostics: IDiagnostics = try {
            car?.getDiagnosticManager()
                ?: throw IllegalStateException("getDiagnosticManager() returned null")
        } catch (error: Throwable) {
            val detail = "getDiagnosticManager(): ${describe(error)}"
            sink.onDiagnosticsStatus(ProbeStatus.ERROR, detail)
            sink.onDtcManagerStatus(ProbeStatus.ERROR, "Diagnostics unavailable")
            sink.onLog(detail, error)
            return
        }

        sink.onDiagnosticsStatus(ProbeStatus.AVAILABLE, "AVAILABLE")
        sink.onLog("getDiagnosticManager(): OK (${diagnostics.javaClass.name})")

        val manager: IDtcManager = try {
            diagnostics.getDtcManager()
                ?: throw IllegalStateException("getDtcManager() returned null")
        } catch (error: Throwable) {
            val detail = "getDtcManager(): ${describe(error)}"
            sink.onDtcManagerStatus(ProbeStatus.ERROR, detail)
            sink.onLog(detail, error)
            return
        }

        dtcManager = manager
        sink.onDtcManagerStatus(ProbeStatus.AVAILABLE, "AVAILABLE")
        sink.onLog("getDtcManager(): OK (${manager.javaClass.name})")

        try {
            val infos = manager.getDtcInfos()
                ?: throw IllegalStateException("getDtcInfos() returned null")
            sink.onLog("getDtcInfos(): ${infos.size} records")
            sink.onDtcsChanged(readDtcRecords(infos))
        } catch (error: Throwable) {
            val detail = "getDtcInfos(): ${describe(error)}"
            sink.onDtcManagerStatus(ProbeStatus.ERROR, detail)
            sink.onLog(detail, error)
            return
        }

        if (!dtcWatcherRegistered) {
            try {
                dtcWatcherRegistered = manager.registerWatcher(dtcWatcher)
                sink.onLog("registerWatcher(): $dtcWatcherRegistered")
            } catch (error: Throwable) {
                sink.onLog("registerWatcher(): ${describe(error)}", error)
            }
        }
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

    private fun logPermission(permission: String) {
        val result = appContext.checkSelfPermission(permission)
        val text = if (result == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        sink.onLog("Permission $permission: $text")
    }

    private fun failCar(operation: String, error: Throwable?) {
        val detail = if (error == null) operation else "$operation: ${describe(error)}"
        sink.onCarStatus(ProbeStatus.ERROR, detail)
        sink.onDiagnosticsStatus(ProbeStatus.ERROR, "ECARX Car unavailable")
        sink.onDtcManagerStatus(ProbeStatus.ERROR, "ECARX Car unavailable")
        sink.onLog(detail, error)
    }

    override fun close() {
        closed = true

        if (dtcWatcherRegistered) {
            try {
                dtcManager?.unregisterWatcher(dtcWatcher)
                sink.onLog("unregisterWatcher(): OK")
            } catch (error: Throwable) {
                sink.onLog("unregisterWatcher(): ${describe(error)}", error)
            }
        }

        try {
            connectable?.unregisterConnectWatcher()
        } catch (error: Throwable) {
            sink.onLog("unregisterConnectWatcher(): ${describe(error)}", error)
        }

        dtcWatcherRegistered = false
        dtcManager = null
        connectable = null
        car = null
    }

    private fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }
            .take(5)
            .joinToString(" <- ") { throwable ->
                val name = throwable.javaClass.name
                val message = throwable.message?.takeIf(String::isNotBlank)
                if (message == null) name else "$name: $message"
            }
        return chain.ifBlank { error.javaClass.name }
    }

}
