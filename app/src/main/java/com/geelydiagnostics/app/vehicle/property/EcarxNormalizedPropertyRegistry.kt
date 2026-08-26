package com.geelydiagnostics.app.vehicle.property

import com.geelydiagnostics.app.vehicle.ecarx.EcarxSensorMetadata
import com.geelydiagnostics.app.vehicle.mapping.Operator
import com.geelydiagnostics.app.vehicle.mapping.ReadSignalMapping
import com.geelydiagnostics.app.vehicle.mapping.ReadTransform
import com.geelydiagnostics.app.vehicle.mapping.ReadTransformStep
import com.geelydiagnostics.app.vehicle.mapping.TransformValue

/**
 * Explicit, semantics-preserving ECARX sensor mappings.
 *
 * Only exact API names with an unambiguous equivalent in the normalized catalog belong here.
 * Similar names are deliberately not guessed.
 */
internal object EcarxNormalizedPropertyRegistry {
    private val sensorMappings = mapOf(
        "SENSOR_TYPE_CAR_SPEED" to CarPropertyId.VEHICLE_SPEED,
        "SENSOR_TYPE_GEAR" to CarPropertyId.GEAR,
        "SENSOR_TYPE_TEMPERATURE_AMBIENT" to CarPropertyId.EXTERIOR_TEMPERATURE,
        "SENSOR_TYPE_TEMPERATURE_INDOOR" to CarPropertyId.INTERIOR_TEMPERATURE,
        "SENSOR_TYPE_FUEL_LEVEL" to CarPropertyId.REMAINING_FUEL_PERCENT,
        "SENSOR_TYPE_RPM" to CarPropertyId.ENGINE_RPM,
        "SENSOR_TYPE_ENDURANCE_MILEAGE" to CarPropertyId.REMAINING_RANGE,
        "SENSOR_TYPE_ACCELERATOR_DEPTH" to CarPropertyId.ACCELERATOR_POSITION,
        "SENSOR_TYPE_BRAKE_DEPTH" to CarPropertyId.BRAKE_POSITION,
        "SENSOR_TYPE_SAFE_BELT_DRIVER" to CarPropertyId.DRIVER_SEAT_BELT,
        "SENSOR_TYPE_STEERING_WHEEL_ANGLE" to CarPropertyId.STEERING_WHEEL_ANGLE,
        "SENSOR_TYPE_ENDURANCE_MILEAGE_EV" to CarPropertyId.HIGH_VOLTAGE_BATTERY_RANGE,
    )

    fun sensorProperty(apiName: String): CarPropertyId? = sensorMappings[apiName]

    fun sensorMapping(apiName: String, signalId: Int): ReadSignalMapping? {
        val propertyId = sensorProperty(apiName) ?: return null
        val transform = when (apiName) {
            "SENSOR_TYPE_GEAR" -> {
                // Reuse the verified enum table, not translated display text or a default gear.
                val values = EcarxSensorMetadata.fields.getValue(apiName).values.orEmpty()
                    .filter { (raw, label) -> raw != -1 && label.length == 1 }
                    .mapKeys { it.key.toString() }
                    .mapValues { TransformValue.StringValue(it.value) }
                ReadTransform.Pipeline(listOf(ReadTransformStep.Mapping(values, default = null)))
            }
            "SENSOR_TYPE_SAFE_BELT_DRIVER" -> ReadTransform.Pipeline(listOf(
                ReadTransformStep.Mapping(
                    mapOf(
                        "2101761" to TransformValue.NumberValue(1.0),
                        "2101762" to TransformValue.NumberValue(2.0),
                    ),
                    default = null,
                ),
            ))
            else -> {
                val scale = EcarxSensorMetadata.fields[apiName]?.rawToDisplayScale ?: 1f
                if (scale == 1f) ReadTransform.Identity else ReadTransform.Pipeline(listOf(
                    ReadTransformStep.Arithmetic(Operator.MULTIPLY, scale.toString().toDouble()),
                ))
            }
        }
        return ReadSignalMapping(propertyId, signalId, apiName, transform)
    }
}
