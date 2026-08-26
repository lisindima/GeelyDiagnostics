package com.geelydiagnostics.app.vehicle.repository

import com.geelydiagnostics.app.model.DtcRecord
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.ecarx.EcarxDataListener
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehicleDiscoveryProgress
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import java.io.Closeable

/** Reject late callbacks/results from a closed scan, atomically with repository restart. */
internal class VehicleSourceSession(
    private val sink: EcarxDataListener,
    private val monitor: Any,
) : EcarxDataListener, Closeable {
    private var active = true

    private fun deliver(action: () -> Unit) = synchronized(monitor) { if (active) action() }

    override fun onDiagnosticDetails(details: com.geelydiagnostics.app.model.EcarxDiagnosticDetails) =
        deliver { sink.onDiagnosticDetails(details) }
    override fun onObd2Snapshot(snapshot: com.geelydiagnostics.app.model.Obd2Snapshot) =
        deliver { sink.onObd2Snapshot(snapshot) }

    override fun onParameterValue(value: CarPropertySnapshot) = deliver { sink.onParameterValue(value) }
    override fun onParameterSnapshot(source: VehiclePropertySource, section: VehicleDataSection?, values: List<CarPropertySnapshot>) =
        deliver { sink.onParameterSnapshot(source, section, values) }
    override fun onParameterStatus(source: VehiclePropertySource, status: ReadStatus, detail: String) =
        deliver { sink.onParameterStatus(source, status, detail) }
    override fun onParameterDiscovery(source: VehiclePropertySource, progress: VehicleDiscoveryProgress) =
        deliver { sink.onParameterDiscovery(source, progress) }
    override fun onParameterLog(source: VehiclePropertySource, message: String, error: Throwable?) =
        deliver { sink.onParameterLog(source, message, error) }
    override fun onCarStatus(status: ReadStatus, detail: String) = deliver { sink.onCarStatus(status, detail) }
    override fun onDiagnosticsStatus(status: ReadStatus, detail: String) = deliver { sink.onDiagnosticsStatus(status, detail) }
    override fun onDtcManagerStatus(status: ReadStatus, detail: String) = deliver { sink.onDtcManagerStatus(status, detail) }
    override fun onCarInfoStatus(status: ReadStatus, detail: String) = deliver { sink.onCarInfoStatus(status, detail) }
    override fun onFunctionStatus(status: ReadStatus, detail: String) = deliver { sink.onFunctionStatus(status, detail) }
    override fun onDtcsChanged(dtcs: List<DtcRecord>) = deliver { sink.onDtcsChanged(dtcs) }
    override fun onLog(message: String, error: Throwable?) = deliver { sink.onLog(message, error) }

    override fun close() = synchronized(monitor) { active = false }
}
