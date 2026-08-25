package com.geelydiagnostics.app.vehicle.vhal

import android.car.VehiclePropertyIds
import com.geelydiagnostics.app.vehicle.mapping.Operator
import com.geelydiagnostics.app.vehicle.mapping.ReadSignalMapping
import com.geelydiagnostics.app.vehicle.mapping.ReadTransform
import com.geelydiagnostics.app.vehicle.mapping.ReadTransformStep
import com.geelydiagnostics.app.vehicle.mapping.TransformValue
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import java.lang.reflect.Modifier

/** Metadata and semantics-preserving mappings for public Android Automotive properties. */
internal object AndroidVehiclePropertyRegistry {
    const val PROFILE_KEY = "AOSP"

    private val namesById: Map<Int, String> by lazy {
        val runtimeNames = VehiclePropertyIds::class.java.fields
            .asSequence()
            .filter { field ->
                field.type == Int::class.javaPrimitiveType && Modifier.isStatic(field.modifiers)
            }
            .mapNotNull { field -> runCatching { field.getInt(null) to field.name }.getOrNull() }
            .toMap()
        AospVehiclePropertyIds.namesById + runtimeNames
    }

    private val mappingsById = listOf(
        numberMapping(
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            "PERF_VEHICLE_SPEED",
            CarPropertyId.VEHICLE_SPEED,
            Operator.MULTIPLY,
            3.6,
        ),
        numberMapping(
            VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
            "PERF_VEHICLE_SPEED_DISPLAY",
            CarPropertyId.DISPLAY_VEHICLE_SPEED,
            Operator.MULTIPLY,
            3.6,
        ),
        enumMapping(
            VehiclePropertyIds.GEAR_SELECTION,
            "GEAR_SELECTION",
            CarPropertyId.GEAR,
            mapOf(
                0 to "-",
                1 to "N",
                2 to "R",
                4 to "P",
                8 to "D",
                16 to "1",
                32 to "2",
                64 to "3",
                128 to "4",
                256 to "5",
                512 to "6",
                1024 to "7",
                2048 to "8",
                4096 to "9",
            ),
            default = "-",
        ),
        enumMapping(
            VehiclePropertyIds.CURRENT_GEAR,
            "CURRENT_GEAR",
            CarPropertyId.TRANSMISSION_GEAR,
            mapOf(
                0 to 0,
                1 to 0,
                2 to -1,
                4 to 0,
                8 to 0,
                16 to 1,
                32 to 2,
                64 to 3,
                128 to 4,
                256 to 5,
                512 to 6,
                1024 to 7,
                2048 to 8,
                4096 to 9,
                8192 to 10,
            ),
            default = 0,
        ),
        identityMapping(
            VehiclePropertyIds.ENGINE_RPM,
            "ENGINE_RPM",
            CarPropertyId.ENGINE_RPM,
        ),
        identityMapping(
            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
            "ENV_OUTSIDE_TEMPERATURE",
            CarPropertyId.EXTERIOR_TEMPERATURE,
        ),
        numberMapping(
            VehiclePropertyIds.FUEL_LEVEL,
            "FUEL_LEVEL",
            CarPropertyId.REMAINING_FUEL_LITERS,
            Operator.DIVIDE,
            1000.0,
        ),
        numberMapping(
            VehiclePropertyIds.RANGE_REMAINING,
            "RANGE_REMAINING",
            CarPropertyId.REMAINING_RANGE,
            Operator.DIVIDE,
            1000.0,
        ),
        identityMapping(
            VehiclePropertyIds.PERF_STEERING_ANGLE,
            "PERF_STEERING_ANGLE",
            CarPropertyId.STEERING_WHEEL_ANGLE,
        ),
        enumMapping(
            VehiclePropertyIds.PARKING_BRAKE_ON,
            "PARKING_BRAKE_ON",
            CarPropertyId.PARKING_BRAKE,
            mapOf(0 to 0, 1 to 1),
            default = 0,
        ),
    ).associateBy(ReadSignalMapping::signalId)

    private val translatedTitles = mapOf(
        "INFO_VIN" to "VIN",
        "INFO_MAKE" to "Производитель",
        "INFO_MODEL" to "Модель",
        "INFO_MODEL_YEAR" to "Модельный год",
        "INFO_FUEL_CAPACITY" to "Объём топливного бака",
        "INFO_EV_BATTERY_CAPACITY" to "Ёмкость тяговой батареи",
        "GEAR_SELECTION" to "Выбранная передача",
        "CURRENT_GEAR" to "Текущая передача трансмиссии",
        "PARKING_BRAKE_ON" to "Стояночный тормоз",
        "NIGHT_MODE" to "Ночной режим",
        "PERF_VEHICLE_SPEED" to "Скорость автомобиля",
        "PERF_VEHICLE_SPEED_DISPLAY" to "Отображаемая скорость",
        "PERF_ODOMETER" to "Пробег",
        "PERF_STEERING_ANGLE" to "Угол рулевого колеса",
        "ENGINE_RPM" to "Обороты двигателя",
        "ENV_OUTSIDE_TEMPERATURE" to "Температура снаружи",
        "FUEL_LEVEL" to "Оставшееся топливо",
        "FUEL_LEVEL_LOW" to "Низкий уровень топлива",
        "RANGE_REMAINING" to "Оставшийся запас хода",
        "EV_BATTERY_LEVEL" to "Оставшаяся энергия тяговой батареи",
        "EV_CHARGE_PORT_OPEN" to "Зарядный порт",
        "EV_CHARGE_PORT_CONNECTED" to "Подключение зарядного кабеля",
        "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE" to "Мощность зарядки тяговой батареи",
        "IGNITION_STATE" to "Состояние зажигания",
        "SEAT_BELT_BUCKLED" to "Ремень безопасности",
        "TIRE_PRESSURE" to "Давление в шине",
    )

    fun property(propertyId: Int): AndroidVehicleProperty? {
        val apiName = namesById[propertyId] ?: return null
        return AndroidVehicleProperty(
            propertyId = propertyId,
            apiName = apiName,
            title = translatedTitles[apiName] ?: apiName.toReadableTitle(),
            normalizedMapping = mappingsById[propertyId],
            profileKey = PROFILE_KEY,
        )
    }

    private fun identityMapping(
        signalId: Int,
        signalName: String,
        propertyId: CarPropertyId,
    ) = ReadSignalMapping(propertyId, signalId, signalName)

    private fun numberMapping(
        signalId: Int,
        signalName: String,
        propertyId: CarPropertyId,
        operator: Operator,
        operand: Double,
    ) = ReadSignalMapping(
        propertyId = propertyId,
        signalId = signalId,
        signalName = signalName,
        transform = ReadTransform.Pipeline(
            listOf(ReadTransformStep.Arithmetic(operator, operand)),
        ),
    )

    private fun enumMapping(
        signalId: Int,
        signalName: String,
        propertyId: CarPropertyId,
        values: Map<Int, Any>,
        default: Any,
    ) = ReadSignalMapping(
        propertyId = propertyId,
        signalId = signalId,
        signalName = signalName,
        transform = ReadTransform.Pipeline(
            listOf(
                ReadTransformStep.Mapping(
                    values = values.mapKeys { it.key.toString() }
                        .mapValues { (_, value) -> value.toTransformValue() },
                    default = default.toTransformValue(),
                ),
            ),
        ),
    )
}

internal data class AndroidVehicleProperty(
    val propertyId: Int,
    val apiName: String,
    val title: String,
    val normalizedMapping: ReadSignalMapping?,
    val profileKey: String,
)

private fun Any.toTransformValue(): TransformValue = when (this) {
    is Number -> TransformValue.NumberValue(toDouble())
    else -> TransformValue.StringValue(toString())
}

private fun String.toReadableTitle(): String = lowercase()
    .replace('_', ' ')
    .replaceFirstChar { character -> character.titlecase() }
