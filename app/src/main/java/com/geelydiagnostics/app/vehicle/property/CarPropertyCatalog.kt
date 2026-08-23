package com.geelydiagnostics.app.vehicle.property

import org.json.JSONArray
import java.io.InputStream

interface CarPropertyCatalog {
    fun definition(id: CarPropertyId): CarPropertyDefinition?
    fun all(): List<CarPropertyDefinition>
}

class JsonCarPropertyCatalog(input: InputStream) : CarPropertyCatalog {
    private val definitions: List<CarPropertyDefinition> = input.bufferedReader().use { reader ->
        parse(reader.readText())
    }
    private val byId = definitions.associateBy(CarPropertyDefinition::id)

    override fun definition(id: CarPropertyId): CarPropertyDefinition? = byId[id]

    override fun all(): List<CarPropertyDefinition> = definitions

    private fun parse(json: String): List<CarPropertyDefinition> {
        val array = JSONArray(json)
        val parsed = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = CarPropertyId(item.getInt("propertyId"))
            val typeName = item.getString("valueType")
            val valueType = runCatching { CarValueType.valueOf(typeName) }
                .getOrElse { throw IllegalArgumentException("Property $id has invalid type $typeName", it) }
            CarPropertyDefinition(
                id = id,
                valueType = valueType,
                description = item.getString("description"),
                decimalPlaces = item.optInt("decimalPlaces").takeIf { item.has("decimalPlaces") },
            )
        }
        val duplicates = parsed.groupingBy(CarPropertyDefinition::id)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) { "Duplicate property ids: $duplicates" }
        return parsed.sortedBy { it.id.rawValue }
    }
}
