package com.geelydiagnostics.app.vehicle.property

@JvmInline
value class CarPropertyId(val rawValue: Int) {
    override fun toString(): String = rawValue.toString()

    companion object {
        val VEHICLE_PROFILE = CarPropertyId(10000)
        val VEHICLE_SPEED = CarPropertyId(10001)
        val HIGH_VOLTAGE_BATTERY_SOC = CarPropertyId(10002)
        val GEAR = CarPropertyId(10003)
        val TRANSMISSION_GEAR = CarPropertyId(10004)
        val EXTERIOR_TEMPERATURE = CarPropertyId(10005)
        val INTERIOR_TEMPERATURE = CarPropertyId(10006)
        val REMAINING_FUEL_PERCENT = CarPropertyId(10011)
        val REMAINING_FUEL_LITERS = CarPropertyId(10012)
        val ENGINE_RPM = CarPropertyId(10021)
        val REMAINING_RANGE = CarPropertyId(10022)
        val PARKING_BRAKE = CarPropertyId(10040)
        val ACCELERATOR_POSITION = CarPropertyId(10054)
        val BRAKE_POSITION = CarPropertyId(10055)
        val DRIVER_SEAT_BELT = CarPropertyId(10056)
        val STEERING_WHEEL_ANGLE = CarPropertyId(10058)
        val HIGH_VOLTAGE_BATTERY_RANGE = CarPropertyId(10060)
        val DISPLAY_VEHICLE_SPEED = CarPropertyId(10063)
    }
}

enum class CarValueType {
    BOOLEAN,
    INT,
    FLOAT,
    STRING,
    CHAR,
}

/** Metadata for a property that can be observed by this application. */
data class CarPropertyDefinition(
    val id: CarPropertyId,
    val valueType: CarValueType,
    val description: String,
    val decimalPlaces: Int? = null,
)

sealed interface CarValue {
    data class BooleanValue(val value: Boolean) : CarValue
    data class IntValue(val value: Int) : CarValue
    data class FloatValue(val value: Double) : CarValue
    data class StringValue(val value: String) : CarValue
    data class CharValue(val value: Char) : CarValue
}

data class RawVehicleValue(
    val text: String,
    val number: Double? = null,
    val numbers: List<Double>? = null,
) {
    companion object
}

enum class VehiclePropertyStatus {
    AVAILABLE,
    UNAVAILABLE,
    ERROR,
}

enum class VehiclePropertySource(val label: String) {
    VHAL("VHAL"),
    ECARX("ECARX"),
    MOCK("MOCK"),
}

/**
 * Stable repository value. A property id is present only when this reading can be merged into the
 * app's normalized catalog. AOSP readings may still be decoded while remaining source-specific.
 * Source timestamps and local receive timestamps have different clocks.
 */
data class CarPropertySnapshot(
    val propertyId: CarPropertyId?,
    val value: CarValue?,
    val displayValue: String,
    val rawValue: RawVehicleValue?,
    val status: VehiclePropertyStatus,
    val source: VehiclePropertySource,
    val sourceSignalId: Int,
    val sourceSignalName: String,
    val sourceTitle: String? = null,
    val areaId: Int = 0,
    val profileKey: String? = null,
    val sourceTimestampNanos: Long? = null,
    val receivedAtMillis: Long,
    val autoUpdates: Boolean = false,
    val valueKind: String = "raw",
    val expectedUpdateIntervalMillis: Long? = null,
    val error: String = "",
    val decoded: Boolean = propertyId != null,
)

data class CarPropertyKey(
    val source: VehiclePropertySource,
    val sourceSignalId: Int,
    val areaId: Int,
)

val CarPropertySnapshot.key: CarPropertyKey
    get() = CarPropertyKey(source, sourceSignalId, areaId)
