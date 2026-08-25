package com.geelydiagnostics.app.vehicle.ecarx

import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.base.ICarFunction
import com.ecarx.xui.adaptapi.car.vehicle.IVehicle
import com.geelydiagnostics.app.model.ApiSupportStatus
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleDataSection
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyDetail
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus

internal class EcarxFunctionReader(
    private val sink: EcarxDataListener,
) : EcarxReader {
    override fun read(car: ICar) {
        sink.onFunctionStatus(ReadStatus.CHECKING, "getICarFunction()")
        val manager: ICarFunction = try {
            car.getICarFunction() ?: throw IllegalStateException("getICarFunction() returned null")
        } catch (error: Throwable) {
            sink.onFunctionStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getICarFunction(): ${describe(error)}", error)
            return
        }
        val specs = runCatching { reflectSpecs() }.getOrElse { error ->
            sink.onFunctionStatus(ReadStatus.ERROR, "Function catalog: ${describe(error)}")
            sink.onLog("Function catalog: ${describe(error)}", error)
            return
        }
        val records = specs.map { spec -> readValue(manager, spec) }
        sink.onParameterSnapshot(
            VehiclePropertySource.ECARX,
            VehicleDataSection.CAPABILITY,
            records,
        )
        val supported = records.count { it.status == VehiclePropertyStatus.AVAILABLE }
        sink.onFunctionStatus(ReadStatus.AVAILABLE, "$supported из ${records.size}")
        sink.onLog("Vehicle functions: $supported supported of ${records.size}")
    }

    private fun readValue(manager: ICarFunction, spec: FunctionSpec): CarPropertySnapshot = try {
        val support = manager.isFunctionSupported(spec.id).toApiSupport()
        var rawValue: Int? = null
        var supportedValues = ""
        var supportedRawValues: IntArray? = null
        var zones = ""
        val readErrors = mutableListOf<String>()
        if (support.isSupported) {
            runCatching { manager.getFunctionValue(spec.id) }
                .onSuccess { rawValue = it }
                .onFailure { readErrors += "value: ${describe(it)}" }
            runCatching { manager.getSupportedFunctionValue(spec.id) }
                .onSuccess {
                    supportedRawValues = it
                    supportedValues = it?.joinToString().orEmpty()
                }
                .onFailure { readErrors += "values: ${describe(it)}" }
            runCatching { manager.getSupportedFunctionZones(spec.id) }
                .onSuccess { zones = it?.joinToString().orEmpty() }
                .onFailure { readErrors += "zones: ${describe(it)}" }
        }
        val decodedValue = rawValue?.let {
                VendorValueDecoder.function(spec.apiName, it, supportedRawValues)
            } ?: if (support.isSupported) {
                VehicleDisplayValue(display = support.displayLabel, raw = "—")
            } else {
                VehicleDisplayValue.unavailable
            }
        snapshot(
            spec = spec,
            value = decodedValue,
            support = support,
            supportedValues = supportedValues,
            zones = zones,
            error = readErrors.joinToString("; "),
        )
    } catch (error: Throwable) {
        snapshot(
            spec = spec,
            value = VehicleDisplayValue.unavailable,
            support = ApiSupportStatus.ERROR,
            error = describe(error),
        )
    }

    private fun snapshot(
        spec: FunctionSpec,
        value: VehicleDisplayValue,
        support: ApiSupportStatus,
        supportedValues: String = "",
        zones: String = "",
        error: String = "",
    ) = CarPropertySnapshot(
        section = VehicleDataSection.CAPABILITY,
        propertyId = null,
        value = value.toCarValue(),
        displayValue = value.display,
        rawValue = value.toRawVehicleValue(),
        status = support.toPropertyStatus(),
        source = VehiclePropertySource.ECARX,
        sourceSignalId = spec.id,
        sourceSignalName = spec.apiName,
        sourceTitle = spec.title,
        receivedAtMillis = System.currentTimeMillis(),
        valueKind = "function/int",
        modeLabel = support.displayLabel.uppercase(),
        details = buildList {
            if (supportedValues.isNotBlank()) add(VehiclePropertyDetail("Допустимые raw", supportedValues))
            if (zones.isNotBlank()) add(VehiclePropertyDetail("Зоны raw", zones))
        },
        decoded = EcarxFunctionMetadata.fields[spec.apiName] != null,
        error = error,
    )

    private fun reflectSpecs(): List<FunctionSpec> =
        intConstants(IVehicle::class.java, "SETTING_FUNC_")
            .filter { (_, id) -> id >= MIN_FUNCTION_ID && id and 0xff == 0 }
            .map { (name, id) ->
                FunctionSpec(
                    id = id,
                    apiName = name,
                    title = EcarxFunctionMetadata.fields[name]?.title
                        ?: prettyName(name, "SETTING_FUNC_"),
                )
            }
            .distinctBy(FunctionSpec::id)
            .sortedBy(FunctionSpec::apiName)

    private data class FunctionSpec(val id: Int, val apiName: String, val title: String)

    companion object {
        private const val MIN_FUNCTION_ID = 0x20000000
    }
}
