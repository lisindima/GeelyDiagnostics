package com.geelydiagnostics.app.vehicle.ecarx

import com.ecarx.xui.adaptapi.FunctionStatus
import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.sensor.ISensor
import com.geelydiagnostics.app.model.ApiSupportStatus
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.EcarxNormalizedPropertyRegistry
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus

internal class EcarxParameterReader(
    private val sink: EcarxDataListener,
) : EcarxReader {
    private var manager: ISensor? = null
    private var listenerRegistered = false
    private val subscribedIds = linkedSetOf<Int>()
    private var specsById: Map<Int, SensorSpec> = emptyMap()
    private val snapshotsById = linkedMapOf<Int, CarPropertySnapshot>()
    private var closed = false

    private val listener = object : ISensor.ISensorListener {
        override fun onSensorEventChanged(type: Int, value: Int) {
            if (closed) return
            val spec = specsById[type]
            val decoded = spec?.let { VendorValueDecoder.sensor(it.apiName, value) }
                ?: VehicleDisplayValue.raw(value.toString())
            publishValue(type, decoded)
        }

        override fun onSensorSupportChanged(type: Int, status: FunctionStatus?) {
            if (!closed) publishSupport(type, status.toApiSupport())
        }

        override fun onSensorValueChanged(type: Int, value: Float) {
            if (closed) return
            val apiName = specsById[type]?.apiName
            val decoded = apiName?.let { VendorValueDecoder.sensor(it, value) }
                ?: VehicleDisplayValue.raw(formatFloat(value))
            publishValue(type, decoded)
        }
    }

    override fun read(car: ICar) {
        if (closed) return
        sink.onParameterStatus(VehiclePropertySource.ECARX, ReadStatus.CHECKING, "getSensorManager()")
        val current = try {
            car.getSensorManager() ?: throw IllegalStateException("getSensorManager() returned null")
        } catch (error: Throwable) {
            sink.onParameterStatus(VehiclePropertySource.ECARX, ReadStatus.ERROR, describe(error))
            sink.onParameterLog(
                VehiclePropertySource.ECARX,
                "getSensorManager(): ${describe(error)}",
                error,
            )
            return
        }
        manager = current
        val specs = runCatching { reflectSpecs() }.getOrElse { error ->
            sink.onParameterStatus(
                VehiclePropertySource.ECARX,
                ReadStatus.ERROR,
                "Sensor catalog: ${describe(error)}",
            )
            sink.onParameterLog(
                VehiclePropertySource.ECARX,
                "Sensor catalog: ${describe(error)}",
                error,
            )
            return
        }
        specsById = specs.associateBy(SensorSpec::id)
        val initial = specs.map { readSensor(current, it) }
        snapshotsById.clear()
        snapshotsById += initial.associateBy(CarPropertySnapshot::sourceSignalId)
        sink.onParameterSnapshot(VehiclePropertySource.ECARX, VehicleDataSection.PARAMETER, initial)

        if (!listenerRegistered) {
            initial.filter { it.status == VehiclePropertyStatus.AVAILABLE }.forEach { snapshot ->
                runCatching {
                    val id = snapshot.sourceSignalId
                    val registered = if (specsById.getValue(id).continuous) {
                        current.registerListener(listener, id, ISensor.RATE_UI)
                    } else {
                        current.registerListener(listener, id)
                    }
                    if (registered) subscribedIds += id
                }.onFailure {
                    sink.onParameterLog(
                        VehiclePropertySource.ECARX,
                        "listener ${snapshot.sourceSignalName}: ${describe(it)}",
                        it,
                    )
                }
            }
            listenerRegistered = subscribedIds.isNotEmpty()
        }

        val classified = initial.map { value ->
            value.copy(autoUpdates = value.sourceSignalId in subscribedIds)
        }
        snapshotsById.clear()
        snapshotsById += classified.associateBy(CarPropertySnapshot::sourceSignalId)
        sink.onParameterSnapshot(VehiclePropertySource.ECARX, VehicleDataSection.PARAMETER, classified)
        val supported = classified.count { it.status == VehiclePropertyStatus.AVAILABLE }
        sink.onParameterStatus(
            VehiclePropertySource.ECARX,
            ReadStatus.AVAILABLE,
            "$supported из ${classified.size}; live-подписок: ${subscribedIds.size}",
        )
        sink.onParameterLog(
            VehiclePropertySource.ECARX,
            "$supported supported of ${classified.size} parameters",
        )
    }

    private fun readSensor(manager: ISensor, spec: SensorSpec): CarPropertySnapshot = try {
        val support = manager.isSensorSupported(spec.id).toApiSupport()
        val value = if (support.isSupported) {
            if (spec.continuous) {
                VendorValueDecoder.sensor(spec.apiName, manager.getSensorLatestValue(spec.id))
            } else {
                VendorValueDecoder.sensor(spec.apiName, manager.getSensorEvent(spec.id))
            }
        } else {
            VehicleDisplayValue.unavailable
        }
        snapshot(spec, value, support.toPropertyStatus())
    } catch (error: Throwable) {
        snapshot(
            spec,
            VehicleDisplayValue.unavailable,
            VehiclePropertyStatus.ERROR,
            describe(error),
        )
    }

    private fun publishValue(type: Int, value: VehicleDisplayValue) {
        val spec = specsById[type] ?: return
        val previous = snapshotsById[type] ?: return
        val updated = snapshot(
            spec = spec,
            value = value,
            status = VehiclePropertyStatus.AVAILABLE,
            error = "",
            autoUpdates = previous.autoUpdates,
        )
        snapshotsById[type] = updated
        sink.onParameterValue(updated)
    }

    private fun publishSupport(type: Int, support: ApiSupportStatus) {
        val previous = snapshotsById[type] ?: return
        val updated = previous.copy(
            status = support.toPropertyStatus(),
            receivedAtMillis = System.currentTimeMillis(),
        )
        snapshotsById[type] = updated
        sink.onParameterValue(updated)
    }

    private fun snapshot(
        spec: SensorSpec,
        value: VehicleDisplayValue,
        status: VehiclePropertyStatus,
        error: String = "",
        autoUpdates: Boolean = false,
    ): CarPropertySnapshot {
        val rawText = value.raw.takeUnless { value == VehicleDisplayValue.unavailable }
        val number = rawText?.toDoubleOrNull()
        val typedValue = when {
            rawText == null -> null
            spec.continuous && number != null -> CarValue.FloatValue(number)
            number != null -> CarValue.IntValue(number.toInt())
            else -> CarValue.StringValue(rawText)
        }
        return CarPropertySnapshot(
            propertyId = EcarxNormalizedPropertyRegistry.sensorProperty(spec.apiName),
            value = typedValue,
            displayValue = value.display,
            rawValue = rawText?.let { RawVehicleValue(it, number) },
            status = status,
            source = VehiclePropertySource.ECARX,
            sourceSignalId = spec.id,
            sourceSignalName = spec.apiName,
            sourceTitle = spec.title,
            receivedAtMillis = System.currentTimeMillis(),
            autoUpdates = autoUpdates,
            valueKind = if (spec.continuous) "float" else "event/int",
            expectedUpdateIntervalMillis = if (spec.continuous) STALE_AFTER_MILLIS else null,
            error = error,
        )
    }

    private fun reflectSpecs(): List<SensorSpec> =
        intConstants(ISensor::class.java, "SENSOR_TYPE_").map { (name, id) ->
            SensorSpec(
                id = id,
                apiName = name,
                title = EcarxSensorMetadata.fields[name]?.title ?: prettyName(name, "SENSOR_TYPE_"),
                continuous = id and INFO_TYPE_MASK == SENSOR_TYPE_FLOAT,
            )
        }

    override fun close() {
        closed = true
        if (listenerRegistered) {
            runCatching { manager?.unregisterListener(listener) }
                .onFailure {
                    sink.onParameterLog(
                        VehiclePropertySource.ECARX,
                        "listener cleanup: ${describe(it)}",
                        it,
                    )
                }
        }
        listenerRegistered = false
        subscribedIds.clear()
        snapshotsById.clear()
        manager = null
    }

    private data class SensorSpec(
        val id: Int,
        val apiName: String,
        val title: String,
        val continuous: Boolean,
    )

    companion object {
        private const val INFO_TYPE_MASK = 0xF00000
        private const val SENSOR_TYPE_FLOAT = 0x100000
        private const val STALE_AFTER_MILLIS = 15_000L
    }
}
