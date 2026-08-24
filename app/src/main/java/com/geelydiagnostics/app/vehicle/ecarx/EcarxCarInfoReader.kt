package com.geelydiagnostics.app.vehicle.ecarx

import com.geelydiagnostics.app.model.*

import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.base.ICarInfo
import com.geelydiagnostics.app.model.ApiSupportStatus
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.model.VehicleInfoRecord
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue

internal class EcarxCarInfoReader(
    private val sink: EcarxDataListener,
) : EcarxReader {
    override fun read(car: ICar) {
        sink.onCarInfoStatus(ReadStatus.CHECKING, "getCarInfoManager()")
        val manager = try {
            car.getCarInfoManager() ?: throw IllegalStateException("getCarInfoManager() returned null")
        } catch (error: Throwable) {
            sink.onCarInfoStatus(ReadStatus.ERROR, describe(error))
            sink.onLog("getCarInfoManager(): ${describe(error)}", error)
            return
        }
        val specs = runCatching { reflectSpecs() }.getOrElse { error ->
            sink.onCarInfoStatus(ReadStatus.ERROR, "Car info catalog: ${describe(error)}")
            sink.onLog("Car info catalog: ${describe(error)}", error)
            return
        }
        val records = specs.map { spec -> readValue(manager, spec) }
        sink.onVehicleInfoChanged(records)
        val supported = records.count { it.support.isSupported }
        sink.onCarInfoStatus(ReadStatus.AVAILABLE, "$supported из ${records.size}")
        sink.onLog("Vehicle info: $supported supported of ${records.size}")
    }

    private fun readValue(manager: ICarInfo, spec: CarInfoSpec): VehicleInfoRecord = try {
        val support = manager.isCarInfoSupported(spec.id).toApiSupport()
        val rawValue: Any? = if (support.isSupported) {
            when (spec.kind) {
                CarInfoKind.CONFIG -> manager.getCarInfoConfig(spec.id)
                CarInfoKind.FLOAT -> manager.getCarInfoFloat(spec.id)
                CarInfoKind.INT -> manager.getCarInfoInt(spec.id)
                CarInfoKind.INTS -> manager.getCarInfoInts(spec.id)
                CarInfoKind.MAP -> manager.getCarInfoMap(spec.id)
                CarInfoKind.STRING -> manager.getCarInfoString(spec.id)
            }
        } else {
            null
        }
        VehicleInfoRecord(
            id = spec.id,
            apiName = spec.apiName,
            title = spec.title,
            value = formatValue(spec, rawValue),
            support = support,
            updatedAtMillis = System.currentTimeMillis(),
        )
    } catch (error: Throwable) {
        VehicleInfoRecord(
            id = spec.id,
            apiName = spec.apiName,
            title = spec.title,
            value = VehicleDisplayValue.unavailable,
            support = ApiSupportStatus.ERROR,
            error = describe(error),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun reflectSpecs(): List<CarInfoSpec> = intConstants(ICarInfo::class.java)
        .mapNotNull { (name, id) ->
            val kind = when {
                name.startsWith("CONFIG_INFO_") && !name.startsWith("CONFIG_INFO_VALUE_") ->
                    CarInfoKind.CONFIG
                name.startsWith("FLT_INFO_") -> CarInfoKind.FLOAT
                name.startsWith("INT_INFO_") && name != "INT_INFO_FUEL_TYPES" -> CarInfoKind.INT
                name.startsWith("INTS_INFO_") -> CarInfoKind.INTS
                name.startsWith("MAP_INFO_") -> CarInfoKind.MAP
                name.startsWith("STRING_INFO_") -> CarInfoKind.STRING
                else -> null
            } ?: return@mapNotNull null
            CarInfoSpec(
                id = id,
                apiName = name,
                title = EcarxCarInfoMetadata.field(name)?.title ?: prettyName(
                    name,
                    "CONFIG_INFO_",
                    "FLT_INFO_",
                    "INT_INFO_",
                    "INTS_INFO_",
                    "MAP_INFO_",
                    "STRING_INFO_",
                ),
                kind = kind,
            )
        }
        .distinctBy(CarInfoSpec::id)
        .sortedBy(CarInfoSpec::apiName)

    private fun formatValue(spec: CarInfoSpec, value: Any?): VehicleDisplayValue {
        if (value == null) return VehicleDisplayValue.unavailable
        if (value is IntArray) return VendorValueDecoder.carInfo(spec.apiName, value)
        if (value is Float) return VendorValueDecoder.carInfo(spec.apiName, value)
        if (value !is Int) {
            val raw = value.toString()
            return VehicleDisplayValue(raw.ifBlank { "пусто" }, raw.ifBlank { "\"\"" })
        }
        val configLabel = if (spec.kind == CarInfoKind.CONFIG) CONFIG_VALUES[value] else null
        return VendorValueDecoder.carInfo(spec.apiName, value, configLabel)
    }

    private data class CarInfoSpec(
        val id: Int,
        val apiName: String,
        val title: String,
        val kind: CarInfoKind,
    )

    private enum class CarInfoKind { CONFIG, FLOAT, INT, INTS, MAP, STRING }

    companion object {
        private val CONFIG_VALUES = mapOf(
            ICarInfo.CONFIG_INFO_VALUE_NOT_CONFIG to "Не установлено",
            ICarInfo.CONFIG_INFO_VALUE_CONFIG to "Установлено",
            ICarInfo.CONFIG_INFO_VALUE_PRELOAD to "Предустановлено",
            ICarInfo.CONFIG_INFO_VALUE_FAULT to "Ошибка конфигурации",
            ICarInfo.CONFIG_INFO_VALUE_UNKNOWN to "Неизвестно",
        )
    }
}
