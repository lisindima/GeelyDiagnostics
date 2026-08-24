package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.model.*

import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.base.ICarFunction
import com.ecarx.xui.adaptapi.car.vehicle.IVehicle
import com.geelydiagnostics.app.model.ApiSupportStatus
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.model.VehicleFunctionRecord
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue

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
        sink.onFunctionsChanged(records)
        val supported = records.count { it.support.isSupported }
        sink.onFunctionStatus(ReadStatus.AVAILABLE, "$supported из ${records.size}")
        sink.onLog("Vehicle functions: $supported supported of ${records.size}")
    }

    private fun readValue(manager: ICarFunction, spec: FunctionSpec): VehicleFunctionRecord = try {
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
        VehicleFunctionRecord(
            id = spec.id,
            apiName = spec.apiName,
            title = spec.title,
            value = rawValue?.let {
                VendorValueDecoder.function(spec.apiName, it, supportedRawValues)
            } ?: VehicleDisplayValue.unavailable,
            supportedValues = supportedValues,
            zones = zones,
            support = support,
            error = readErrors.joinToString("; "),
            updatedAtMillis = System.currentTimeMillis(),
        )
    } catch (error: Throwable) {
        VehicleFunctionRecord(
            id = spec.id,
            apiName = spec.apiName,
            title = spec.title,
            support = ApiSupportStatus.ERROR,
            error = describe(error),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

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
