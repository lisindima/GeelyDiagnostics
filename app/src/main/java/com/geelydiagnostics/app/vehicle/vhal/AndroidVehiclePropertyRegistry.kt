package com.geelydiagnostics.app.vehicle.vhal

import android.car.VehiclePropertyIds
import com.geelydiagnostics.app.vehicle.mapping.Operator
import com.geelydiagnostics.app.vehicle.mapping.ReadSignalMapping
import com.geelydiagnostics.app.vehicle.mapping.ReadTransform
import com.geelydiagnostics.app.vehicle.mapping.ReadTransformStep
import com.geelydiagnostics.app.vehicle.mapping.TransformValue
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
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
        identityMapping(
            AospVehiclePropertyIds.id("ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE"),
            "ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE",
            CarPropertyId.ACCELERATOR_POSITION,
        ),
        identityMapping(
            AospVehiclePropertyIds.id("BRAKE_PEDAL_COMPRESSION_PERCENTAGE"),
            "BRAKE_PEDAL_COMPRESSION_PERCENTAGE",
            CarPropertyId.BRAKE_POSITION,
        ),
    ).associateBy(ReadSignalMapping::signalId)

    private val translatedTitles = mapOf(
        "INFO_VIN" to "VIN",
        "INFO_MAKE" to "Производитель",
        "INFO_MODEL" to "Модель",
        "INFO_MODEL_TRIM" to "Комплектация",
        "INFO_MODEL_YEAR" to "Модельный год",
        "INFO_DRIVER_SEAT" to "Место водителя",
        "INFO_EXTERIOR_DIMENSIONS" to "Габариты автомобиля",
        "INFO_FUEL_TYPE" to "Тип топлива",
        "INFO_FUEL_DOOR_LOCATION" to "Расположение лючка топливного бака",
        "INFO_FUEL_CAPACITY" to "Объём топливного бака",
        "INFO_EV_BATTERY_CAPACITY" to "Ёмкость тяговой батареи",
        "INFO_EV_CONNECTOR_TYPE" to "Поддерживаемые зарядные разъёмы",
        "INFO_EV_PORT_LOCATION" to "Расположение зарядного порта",
        "INFO_MULTI_EV_PORT_LOCATIONS" to "Расположение зарядных портов",
        "INFO_VEHICLE_SIZE_CLASS" to "Класс размера автомобиля",
        "GEAR_SELECTION" to "Выбранная передача",
        "CURRENT_GEAR" to "Текущая передача трансмиссии",
        "PARKING_BRAKE_ON" to "Стояночный тормоз",
        "PARKING_BRAKE_AUTO_APPLY" to "Автоматический стояночный тормоз",
        "NIGHT_MODE" to "Ночной режим",
        "ABS_ACTIVE" to "Работа ABS",
        "TRACTION_CONTROL_ACTIVE" to "Работа противобуксовочной системы",
        "ELECTRONIC_STABILITY_CONTROL_STATE" to "Состояние системы стабилизации",
        "ELECTRONIC_STABILITY_CONTROL_ENABLED" to "Система стабилизации",
        "ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE" to "Положение педали акселератора",
        "BRAKE_PEDAL_COMPRESSION_PERCENTAGE" to "Положение педали тормоза",
        "BRAKE_PAD_WEAR_PERCENTAGE" to "Износ тормозных колодок",
        "BRAKE_FLUID_LEVEL_LOW" to "Низкий уровень тормозной жидкости",
        "PERF_VEHICLE_SPEED" to "Скорость автомобиля",
        "PERF_VEHICLE_SPEED_DISPLAY" to "Отображаемая скорость",
        "PERF_ODOMETER" to "Пробег",
        "PERF_STEERING_ANGLE" to "Угол рулевого колеса",
        "PERF_REAR_STEERING_ANGLE" to "Угол задних управляемых колёс",
        "ENGINE_RPM" to "Обороты двигателя",
        "ENGINE_COOLANT_TEMP" to "Температура охлаждающей жидкости",
        "ENGINE_OIL_TEMP" to "Температура моторного масла",
        "ENGINE_OIL_LEVEL" to "Уровень моторного масла",
        "ENGINE_IDLE_AUTO_STOP_ENABLED" to "Автоматическая остановка двигателя",
        "ENV_OUTSIDE_TEMPERATURE" to "Температура снаружи",
        "FUEL_LEVEL" to "Оставшееся топливо",
        "FUEL_LEVEL_LOW" to "Низкий уровень топлива",
        "FUEL_DOOR_OPEN" to "Лючок топливного бака",
        "RANGE_REMAINING" to "Оставшийся запас хода",
        "EV_BATTERY_LEVEL" to "Оставшаяся энергия тяговой батареи",
        "EV_CURRENT_BATTERY_CAPACITY" to "Текущая доступная ёмкость батареи",
        "EV_BATTERY_AVERAGE_TEMPERATURE" to "Средняя температура тяговой батареи",
        "EV_CHARGE_PORT_OPEN" to "Зарядный порт",
        "EV_CHARGE_PORT_CONNECTED" to "Подключение зарядного кабеля",
        "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE" to "Мощность зарядки тяговой батареи",
        "EV_CHARGE_CURRENT_DRAW_LIMIT" to "Ограничение тока зарядки",
        "EV_CHARGE_PERCENT_LIMIT" to "Ограничение уровня зарядки",
        "EV_CHARGE_STATE" to "Состояние зарядки",
        "EV_CHARGE_TIME_REMAINING" to "Оставшееся время зарядки",
        "EV_REGENERATIVE_BRAKING_STATE" to "Рекуперативное торможение",
        "EV_STOPPING_MODE" to "Режим остановки электромобиля",
        "INSTANTANEOUS_EV_EFFICIENCY" to "Мгновенная эффективность электротяги",
        "INSTANTANEOUS_FUEL_ECONOMY" to "Мгновенный расход топлива",
        "IGNITION_STATE" to "Состояние зажигания",
        "SEAT_BELT_BUCKLED" to "Ремень безопасности",
        "SEAT_OCCUPANCY" to "Занятость сиденья",
        "TIRE_PRESSURE" to "Давление в шине",
        "CRITICALLY_LOW_TIRE_PRESSURE" to "Критически низкое давление в шине",
        "DOOR_LOCK" to "Блокировка двери",
        "DOOR_POS" to "Положение двери",
        "WINDOW_LOCK" to "Блокировка стеклоподъёмников",
        "WINDOW_POS" to "Положение стекла",
        "MIRROR_FOLD" to "Складывание зеркала",
        "HEADLIGHTS_STATE" to "Состояние фар",
        "HIGH_BEAM_LIGHTS_STATE" to "Состояние дальнего света",
        "HAZARD_LIGHTS_STATE" to "Аварийная сигнализация",
        "TURN_SIGNAL_LIGHT_STATE" to "Указатели поворота",
        "HVAC_TEMPERATURE_CURRENT" to "Текущая температура климата",
        "HVAC_TEMPERATURE_SET" to "Заданная температура климата",
        "HVAC_FAN_SPEED" to "Скорость вентилятора климата",
        "HVAC_AC_ON" to "Кондиционер",
        "HVAC_POWER_ON" to "Климатическая система",
        "CRUISE_CONTROL_TYPE" to "Тип круиз-контроля",
        "CRUISE_CONTROL_STATE" to "Состояние круиз-контроля",
        "CRUISE_CONTROL_TARGET_SPEED" to "Заданная скорость круиз-контроля",
        "FORWARD_COLLISION_WARNING_STATE" to "Предупреждение о фронтальном столкновении",
        "AUTOMATIC_EMERGENCY_BRAKING_STATE" to "Автоматическое экстренное торможение",
        "LANE_DEPARTURE_WARNING_STATE" to "Предупреждение о выходе из полосы",
        "LANE_KEEP_ASSIST_STATE" to "Удержание в полосе",
        "LANE_CENTERING_ASSIST_STATE" to "Центрирование в полосе",
        "BLIND_SPOT_WARNING_STATE" to "Контроль слепых зон",
        "CROSS_TRAFFIC_MONITORING_WARNING_STATE" to "Контроль поперечного движения",
        "VEHICLE_CURB_WEIGHT" to "Снаряжённая масса",
        "VEHICLE_PASSIVE_SUSPENSION_HEIGHT" to "Высота пассивной подвески",
        "VEHICLE_DRIVING_AUTOMATION_CURRENT_LEVEL" to "Текущий уровень автоматизации",
        "WINDSHIELD_WIPERS_STATE" to "Состояние стеклоочистителей",
        "WINDSHIELD_WIPERS_SWITCH" to "Переключатель стеклоочистителей",
        "WHEEL_TICK" to "Счётчики оборотов колёс",
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
) {
    fun decode(raw: RawVehicleValue): AospDecodedValue =
        AospVehicleValueDecoder.decode(propertyId, apiName, raw)

    fun titleForArea(areaId: Int): String = AospVehicleValueDecoder.areaLabel(propertyId, areaId)
        ?.let { "$title · $it" }
        ?: title
}

private fun Any.toTransformValue(): TransformValue = when (this) {
    is Number -> TransformValue.NumberValue(toDouble())
    else -> TransformValue.StringValue(toString())
}

private fun String.toReadableTitle(): String = lowercase()
    .replace('_', ' ')
    .replaceFirstChar { character -> character.titlecase() }
