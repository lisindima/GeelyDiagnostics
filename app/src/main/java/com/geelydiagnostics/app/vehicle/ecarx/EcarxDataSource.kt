package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.model.*

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ecarx.xui.adaptapi.binder.IConnectable
import com.ecarx.xui.adaptapi.car.Car
import com.ecarx.xui.adaptapi.car.ICar
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.source.VehicleParameterDataSource
import java.util.concurrent.Executors

/** Owns the ECARX connection; independent readers own each read-only API surface. */
internal class EcarxDataSource(
    context: Context,
    private val sink: EcarxDataListener,
) : VehicleParameterDataSource {
    override val source: VehiclePropertySource = VehiclePropertySource.ECARX

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "EcarxReadOnly").apply { isDaemon = true }
    }
    private val readers: List<EcarxReader> = listOf(
        EcarxDiagnosticsReader(sink),
        EcarxParameterReader(sink),
        EcarxCarInfoReader(sink),
        EcarxFunctionReader(sink),
    )

    @Volatile
    private var closed = false
    @Volatile
    private var connected = false
    private var car: ICar? = null
    private var connectable: IConnectable? = null

    private val connectWatcher = object : IConnectable.IConnectWatcher {
        override fun onConnected() {
            if (closed) return
            val isNewConnection = !connected
            connected = true
            sink.onLog("IConnectable.onConnected()")
            sink.onCarStatus(ReadStatus.AVAILABLE, "CONNECTED")
            if (isNewConnection) submitRefresh("connection callback")
        }

        override fun onDisConnected() {
            if (closed) return
            connected = false
            sink.onLog("IConnectable.onDisConnected()")
            sink.onCarStatus(ReadStatus.ERROR, "DISCONNECTED")
        }
    }

    override fun start() {
        sink.onCarStatus(ReadStatus.CHECKING, "Car.create()")
        sink.onDiagnosticsStatus(ReadStatus.CHECKING)
        sink.onDtcManagerStatus(ReadStatus.CHECKING)
        sink.onParameterStatus(source, ReadStatus.CHECKING)
        sink.onCarInfoStatus(ReadStatus.CHECKING)
        sink.onFunctionStatus(ReadStatus.CHECKING)
        executor.execute(::initialize)
    }

    private fun initialize() {
        if (closed) return
        sink.onLog(
            "Read-only scan started on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
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
            runCatching { createdCar.registerConnectWatcher(connectWatcher) }
                .onSuccess { sink.onLog("registerConnectWatcher(): OK; waiting for callbacks") }
                .onFailure { sink.onLog("registerConnectWatcher(): ${describe(it)}", it) }
        } else {
            sink.onLog("ICar does not implement IConnectable; reading immediately")
        }
        refreshAll("initial attempt")
    }

    private fun submitRefresh(trigger: String) {
        if (closed) return
        runCatching { executor.execute { refreshAll(trigger) } }
    }

    private fun refreshAll(trigger: String) {
        if (closed) return
        val connectedCar = car ?: return
        sink.onLog("Read-only refresh: $trigger")
        readers.forEach { reader ->
            if (!closed) reader.read(connectedCar)
        }
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
        sink.onParameterStatus(source, ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onCarInfoStatus(ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onFunctionStatus(ReadStatus.ERROR, "ECARX Car unavailable")
        sink.onLog(detail, error)
    }

    override fun close() {
        closed = true
        readers.forEach { runCatching { it.close() } }
        runCatching { connectable?.unregisterConnectWatcher() }
            .onFailure { sink.onLog("Connect watcher cleanup: ${describe(it)}", it) }
        executor.shutdownNow()
        connectable = null
        car = null
    }
}
