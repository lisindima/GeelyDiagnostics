package com.geelydiagnostics.app.vehicle.property

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
}
