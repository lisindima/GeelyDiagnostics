package com.geelydiagnostics.app.vehicle.mapping

import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

enum class VehicleProfile(
    val key: String,
    val vehicle: String,
) {
    RAW("RAW", "Без профиля · только исходные значения"),
    G426("G426", "Boyue Cool / G426"),
    G636("G636", "Boyue L, экспорт"),
    FX11("FX11", "Boyue L"),
    FX12("FX12", "Galaxy L7"),
    FX121_8("FX121_8", "Galaxy L7, система 1.8"),
    FS12("FS12", "Galaxy L6"),
    FS11_A2("FS11_A2", "Xingrui L Hybrid"),
    KX11_A2("KX11_A2", "Xingyue L Hybrid"),
    KX11_22_LSHD("KX11_22_LSHD", "Xingyue L Thor Hybrid 2022"),
    KX11_24("KX11_24", "Xingyue L 2024"),
    KX11_24_TJ("KX11_24_TJ", "Xingyue L Tianji 2024"),
    KX11_25("KX11_25", "Xingyue L 2025"),
}

data class ReadSignalMapping(
    val propertyId: CarPropertyId,
    val signalId: Int,
    val signalName: String,
    val transform: ReadTransform = ReadTransform.Identity,
)

class VehicleProfileMapping(
    val profile: VehicleProfile,
    mappings: List<ReadSignalMapping>,
) {
    val mappings: List<ReadSignalMapping> = mappings.sortedBy { it.propertyId.rawValue }
    private val bySignalId = mappings.associateBy(ReadSignalMapping::signalId)

    init {
        require(profile != VehicleProfile.RAW || mappings.isEmpty()) {
            "RAW profile cannot contain mappings"
        }
        val duplicateProperties = mappings.groupingBy(ReadSignalMapping::propertyId)
            .eachCount().filterValues { it > 1 }.keys
        val duplicateSignals = mappings.groupingBy(ReadSignalMapping::signalId)
            .eachCount().filterValues { it > 1 }.keys
        require(duplicateProperties.isEmpty()) { "Duplicate property mappings: $duplicateProperties" }
        require(duplicateSignals.isEmpty()) { "Duplicate signal mappings: $duplicateSignals" }
    }

    fun forSignal(signalId: Int): ReadSignalMapping? = bySignalId[signalId]

    companion object {
        fun raw(): VehicleProfileMapping = VehicleProfileMapping(VehicleProfile.RAW, emptyList())
    }
}

class JsonVehicleProfileMappingLoader {
    fun load(profile: VehicleProfile, input: InputStream): VehicleProfileMapping {
        require(profile != VehicleProfile.RAW) { "RAW profile does not use a mapping file" }
        val json = input.bufferedReader().use { it.readText() }.trim()
        val array = if (json.startsWith("[")) {
            JSONArray(json)
        } else {
            val root = JSONObject(json)
            root.optJSONArray("mappings") ?: root.optJSONArray("signals")
                ?: throw IllegalArgumentException("Mapping JSON has no mappings array")
        }
        val mappings = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            check(!item.has("writeSignalId") && !item.has("writeTransform")) {
                "Read-only mapping contains write fields at index $index"
            }
            ReadSignalMapping(
                propertyId = CarPropertyId(item.getInt("propertyId")),
                signalId = item.getInt("readSignalId"),
                signalName = item.getString("readSignalName"),
                transform = ReadTransformParser.parse(item.optJSONObject("readTransform")),
            )
        }
        return VehicleProfileMapping(profile, mappings)
    }
}
