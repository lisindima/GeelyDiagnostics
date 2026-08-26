package com.geelydiagnostics.app.vehicle.property

import com.geelydiagnostics.app.vehicle.mapping.ReadTransform

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

enum class VehicleMappingOrigin { PROFILE, AOSP, ECARX, NONE }

data class VehicleDiscoveryProgress(
    val mappedBootstrapReady: Boolean = false,
    val rawDiscoveryRunning: Boolean = false,
    val rawDiscoveryCompleted: Boolean = false,
)

/** Logical application section. It is part of signal identity to avoid collisions between APIs. */
enum class VehicleDataSection {
    PARAMETER,
    VEHICLE_INFO,
    CAPABILITY,
}

/** Section assigned by the normalized catalog's stable ID namespaces. */
internal val CarPropertyId.catalogSection: VehicleDataSection
    get() = if (rawValue >= FIRST_CAPABILITY_PROPERTY_ID) {
        VehicleDataSection.CAPABILITY
    } else {
        VehicleDataSection.PARAMETER
    }

private const val FIRST_CAPABILITY_PROPERTY_ID = 30_000

data class VehiclePropertyDetail(
    val label: String,
    val value: String,
)

/**
 * Stable repository value. A property id is present only when this reading can be merged into the
 * app's normalized catalog. AOSP readings may still be decoded while remaining source-specific.
 * Source timestamps and local receive timestamps have different clocks.
 */
data class CarPropertySnapshot(
    val section: VehicleDataSection = VehicleDataSection.PARAMETER,
    val propertyId: CarPropertyId?,
    val value: CarValue?,
    val displayValue: String,
    val rawValue: RawVehicleValue?,
    val status: VehiclePropertyStatus,
    val source: VehiclePropertySource,
    val sourceSignalId: Int,
    val sourceSignalName: String,
    val sourceTitle: String? = null,
    val sourceDescription: String? = null,
    val areaId: Int = 0,
    val profileKey: String? = null,
    val sourceTimestampNanos: Long? = null,
    val receivedAtMillis: Long,
    val autoUpdates: Boolean = false,
    val valueKind: String = "raw",
    val expectedUpdateIntervalMillis: Long? = null,
    val modeLabel: String? = null,
    val details: List<VehiclePropertyDetail> = emptyList(),
    val error: String = "",
    val decoded: Boolean = propertyId != null,
    val backend: String? = null,
    val readTransform: ReadTransform? = null,
    val mappingOrigin: VehicleMappingOrigin = when {
        propertyId == null -> VehicleMappingOrigin.NONE
        source == VehiclePropertySource.ECARX -> VehicleMappingOrigin.ECARX
        profileKey == "AOSP" -> VehicleMappingOrigin.AOSP
        source == VehiclePropertySource.VHAL && profileKey != null && profileKey != "RAW" ->
            VehicleMappingOrigin.PROFILE
        else -> VehicleMappingOrigin.NONE
    },
)

internal val CarValue.numericValue: Double?
    get() = when (this) {
        is CarValue.FloatValue -> value
        is CarValue.IntValue -> value.toDouble()
        else -> null
    }

data class CarPropertyKey(
    val section: VehicleDataSection,
    val source: VehiclePropertySource,
    val sourceSignalId: Int,
    val areaId: Int,
)

val CarPropertySnapshot.key: CarPropertyKey
    get() = CarPropertyKey(section, source, sourceSignalId, areaId)
