package com.geelydiagnostics.app.vehicle.source

import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import java.io.Closeable

/** Common read-only boundary implemented by every source of vehicle parameters. */
internal interface VehicleParameterDataSource : Closeable {
    val source: VehiclePropertySource
    fun start()
}

/** Source events are raw snapshots; normalization and source selection belong to the repository. */
internal interface VehicleParameterSink {
    fun onParameterStatus(source: VehiclePropertySource, status: ReadStatus, detail: String = "")

    /** [section] scopes a partial catalog replacement; null replaces the source's whole catalog. */
    fun onParameterSnapshot(
        source: VehiclePropertySource,
        section: VehicleDataSection?,
        values: List<CarPropertySnapshot>,
    )
    fun onParameterValue(value: CarPropertySnapshot)
    fun onParameterLog(source: VehiclePropertySource, message: String, error: Throwable? = null)
}
