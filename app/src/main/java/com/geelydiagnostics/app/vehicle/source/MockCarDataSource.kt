package com.geelydiagnostics.app.vehicle.source

import com.geelydiagnostics.app.ReadStatus
import com.geelydiagnostics.app.vehicle.property.CarPropertyKey
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.key

/**
 * In-memory source for unit tests and explicit debug scenarios.
 *
 * It is never selected by the production repository and cannot write to a vehicle. [emit] only
 * simulates a value arriving from a source through the same sink used by VHAL and ECARX.
 */
internal class MockCarDataSource(
    private val sink: VehicleParameterSink,
    initialValues: List<CarPropertySnapshot> = emptyList(),
) : VehicleParameterDataSource {
    override val source: VehiclePropertySource = VehiclePropertySource.MOCK

    private val values = linkedMapOf<CarPropertyKey, CarPropertySnapshot>()
    private var started = false
    private var closed = false

    init {
        initialValues.forEach { value ->
            val validated = value.validated()
            values[validated.key] = validated
        }
    }

    @Synchronized
    override fun start() {
        check(!closed) { "Mock source is closed" }
        if (started) return
        started = true
        sink.onParameterStatus(source, ReadStatus.CHECKING, "Starting in-memory source")
        sink.onParameterSnapshot(source, values.values.toList())
        sink.onParameterStatus(source, ReadStatus.AVAILABLE, "${values.size} mock values")
    }

    @Synchronized
    fun replace(newValues: List<CarPropertySnapshot>) {
        checkActive()
        values.clear()
        newValues.forEach { value ->
            val validated = value.validated()
            values[validated.key] = validated
        }
        sink.onParameterSnapshot(source, values.values.toList())
    }

    @Synchronized
    fun emit(value: CarPropertySnapshot) {
        checkActive()
        val validated = value.validated()
        values[validated.key] = validated
        sink.onParameterValue(validated)
    }

    @Synchronized
    override fun close() {
        closed = true
        started = false
        values.clear()
    }

    private fun checkActive() {
        check(started && !closed) { "Mock source is not active" }
    }

    private fun CarPropertySnapshot.validated(): CarPropertySnapshot {
        require(source == VehiclePropertySource.MOCK) {
            "Mock source accepts only MOCK snapshots, got $source"
        }
        return this
    }
}
