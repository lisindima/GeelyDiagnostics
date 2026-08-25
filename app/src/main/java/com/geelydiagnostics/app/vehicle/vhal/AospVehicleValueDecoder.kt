package com.geelydiagnostics.app.vehicle.vhal

import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class AospDecodedValue(
    val displayValue: String,
    val value: CarValue,
    val decoded: Boolean,
)

/** Formats standard AAOS values without requiring an app-specific normalized CarPropertyId. */
internal object AospVehicleValueDecoder {
    private const val TYPE_MASK = 0x00ff0000
    private const val TYPE_STRING = 0x00100000
    private const val TYPE_BOOLEAN = 0x00200000
    private const val TYPE_INT32 = 0x00400000
    private const val TYPE_INT32_VECTOR = 0x00410000
    private const val TYPE_INT64 = 0x00500000
    private const val TYPE_INT64_VECTOR = 0x00510000
    private const val TYPE_FLOAT = 0x00600000
    private const val TYPE_FLOAT_VECTOR = 0x00610000
    private const val TYPE_BYTES = 0x00700000

    private data class NumberRule(
        val multiplier: Double = 1.0,
        val unit: String,
        val decimalPlaces: Int? = null,
    )

    private val numberRules = buildMap {
        listOf(
            "ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE",
            "BRAKE_PAD_WEAR_PERCENTAGE",
            "BRAKE_PEDAL_COMPRESSION_PERCENTAGE",
            "EV_CHARGE_PERCENT_LIMIT",
        ).forEach { put(it, NumberRule(unit = "%", decimalPlaces = 1)) }
        listOf(
            "ENGINE_COOLANT_TEMP",
            "ENGINE_OIL_TEMP",
            "ENV_OUTSIDE_TEMPERATURE",
            "EV_BATTERY_AVERAGE_TEMPERATURE",
            "HVAC_TEMPERATURE_CURRENT",
            "HVAC_TEMPERATURE_SET",
        ).forEach { put(it, NumberRule(unit = "°C", decimalPlaces = 1)) }
        listOf("ENGINE_RPM", "HVAC_ACTUAL_FAN_SPEED_RPM").forEach {
            put(it, NumberRule(unit = "об/мин", decimalPlaces = 0))
        }
        listOf("PERF_VEHICLE_SPEED", "PERF_VEHICLE_SPEED_DISPLAY", "CRUISE_CONTROL_TARGET_SPEED")
            .forEach { put(it, NumberRule(multiplier = 3.6, unit = "км/ч", decimalPlaces = 1)) }
        listOf("FUEL_LEVEL", "INFO_FUEL_CAPACITY").forEach {
            put(it, NumberRule(multiplier = 0.001, unit = "л", decimalPlaces = 1))
        }
        put("RANGE_REMAINING", NumberRule(multiplier = 0.001, unit = "км", decimalPlaces = 1))
        put("PERF_ODOMETER", NumberRule(unit = "км", decimalPlaces = 1))
        listOf("PERF_STEERING_ANGLE", "PERF_REAR_STEERING_ANGLE").forEach {
            put(it, NumberRule(unit = "°", decimalPlaces = 1))
        }
        listOf("INFO_EV_BATTERY_CAPACITY", "EV_BATTERY_LEVEL", "EV_CURRENT_BATTERY_CAPACITY")
            .forEach { put(it, NumberRule(multiplier = 0.001, unit = "кВт·ч", decimalPlaces = 2)) }
        put(
            "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE",
            NumberRule(multiplier = 0.000001, unit = "кВт", decimalPlaces = 2),
        )
        put("EV_CHARGE_CURRENT_DRAW_LIMIT", NumberRule(unit = "А", decimalPlaces = 1))
        listOf("TIRE_PRESSURE", "CRITICALLY_LOW_TIRE_PRESSURE").forEach {
            put(it, NumberRule(unit = "кПа", decimalPlaces = 1))
        }
        put("VEHICLE_CURB_WEIGHT", NumberRule(unit = "кг", decimalPlaces = 0))
        put("VEHICLE_PASSIVE_SUSPENSION_HEIGHT", NumberRule(unit = "мм", decimalPlaces = 0))
        put("ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE", NumberRule(unit = "мм"))
        put("ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP", NumberRule(unit = "мс"))
        put("WINDSHIELD_WIPERS_PERIOD", NumberRule(unit = "мс"))
        put("INSTANTANEOUS_EV_EFFICIENCY", NumberRule(unit = "км/кВт·ч", decimalPlaces = 2))
        put("INSTANTANEOUS_FUEL_ECONOMY", NumberRule(unit = "л/100 км", decimalPlaces = 2))
        listOf(
            "ULTRASONICS_SENSOR_DETECTION_RANGE",
            "ULTRASONICS_SENSOR_MEASURED_DISTANCE",
            "ULTRASONICS_SENSOR_SUPPORTED_RANGES",
            "ULTRASONICS_SENSOR_POSITION",
        ).forEach { put(it, NumberRule(unit = "мм", decimalPlaces = 0)) }
        listOf("ULTRASONICS_SENSOR_FIELD_OF_VIEW", "ULTRASONICS_SENSOR_ORIENTATION").forEach {
            put(it, NumberRule(unit = "°", decimalPlaces = 1))
        }
    }

    private val enumValues: Map<String, Map<Long, String>> by lazy { mapOf(
        "GEAR_SELECTION" to gearValues,
        "CURRENT_GEAR" to gearValues,
        "IGNITION_STATE" to mapOf(
            0L to "Не определено",
            1L to "Блокировка",
            2L to "Выключено",
            3L to "Аксессуары",
            4L to "Зажигание включено",
            5L to "Запуск двигателя",
        ),
        "ENGINE_OIL_LEVEL" to mapOf(
            0L to "Критически низкий",
            1L to "Низкий",
            2L to "Нормальный",
            3L to "Высокий",
            4L to "Ошибка измерения",
        ),
        "EV_CHARGE_STATE" to mapOf(
            0L to "Неизвестно",
            1L to "Заряжается",
            2L to "Полностью заряжена",
            3L to "Не заряжается",
            4L to "Ошибка зарядки",
        ),
        "EV_REGENERATIVE_BRAKING_STATE" to mapOf(
            0L to "Неизвестно",
            1L to "Выключено",
            2L to "Частично включено",
            3L to "Полностью включено",
        ),
        "EV_STOPPING_MODE" to mapOf(
            0L to "Другой режим",
            1L to "Ползущий режим",
            2L to "Свободный ход",
            3L to "Удержание",
        ),
        "SEAT_OCCUPANCY" to mapOf(0L to "Неизвестно", 1L to "Свободно", 2L to "Занято"),
        "INFO_DRIVER_SEAT" to seatAreas,
        "INFO_FUEL_DOOR_LOCATION" to portLocations,
        "INFO_EV_PORT_LOCATION" to portLocations,
        "INFO_MULTI_EV_PORT_LOCATIONS" to portLocations,
        "INFO_FUEL_TYPE" to fuelTypes,
        "INFO_EV_CONNECTOR_TYPE" to connectorTypes,
        "DISTANCE_DISPLAY_UNITS" to vehicleUnits,
        "FUEL_VOLUME_DISPLAY_UNITS" to vehicleUnits,
        "TIRE_PRESSURE_DISPLAY_UNITS" to vehicleUnits,
        "EV_BATTERY_DISPLAY_UNITS" to vehicleUnits,
        "VEHICLE_SPEED_DISPLAY_UNITS" to vehicleUnits,
        "HVAC_TEMPERATURE_DISPLAY_UNITS" to vehicleUnits,
        "GENERAL_SAFETY_REGULATION_COMPLIANCE" to mapOf(
            0L to "Не требуется",
            1L to "Требуется GSR v1",
        ),
        "VEHICLE_DRIVING_AUTOMATION_CURRENT_LEVEL" to automationLevels,
        "VEHICLE_DRIVING_AUTOMATION_TARGET_LEVEL" to automationLevels,
        "TURN_SIGNAL_STATE" to turnSignals,
        "TURN_SIGNAL_LIGHT_STATE" to turnSignals,
        "TURN_SIGNAL_SWITCH" to turnSignals,
        "WINDSHIELD_WIPERS_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Выключены",
            2L to "Включены",
            3L to "Сервисное положение",
        ),
        "WINDSHIELD_WIPERS_SWITCH" to wiperSwitchValues,
        "CRUISE_CONTROL_TYPE" to mapOf(
            0L to "Другой тип",
            1L to "Обычный круиз-контроль",
            2L to "Адаптивный круиз-контроль",
            3L to "Предиктивный круиз-контроль",
        ),
        "CRUISE_CONTROL_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Включён",
            2L to "Активен",
            3L to "Вмешательство водителя",
            4L to "Приостановлен",
            5L to "Принудительное отключение",
        ),
        "AUTOMATIC_EMERGENCY_BRAKING_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Включено",
            2L to "Срабатывает",
            3L to "Вмешательство водителя",
        ),
        "FORWARD_COLLISION_WARNING_STATE" to warningValues,
        "BLIND_SPOT_WARNING_STATE" to warningValues,
        "ELECTRONIC_STABILITY_CONTROL_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Включено",
            2L to "Срабатывает",
        ),
        "LANE_DEPARTURE_WARNING_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Предупреждений нет",
            2L to "Уход из полосы слева",
            3L to "Уход из полосы справа",
        ),
        "LANE_KEEP_ASSIST_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Включён",
            2L to "Коррекция влево",
            3L to "Коррекция вправо",
            4L to "Вмешательство водителя",
        ),
        "LANE_CENTERING_ASSIST_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Включён",
            2L to "Запрошена активация",
            3L to "Активен",
            4L to "Вмешательство водителя",
            5L to "Принудительное отключение",
        ),
        "CROSS_TRAFFIC_MONITORING_WARNING_STATE" to mapOf(
            0L to "Другое состояние",
            1L to "Предупреждений нет",
            2L to "Спереди слева",
            3L to "Спереди справа",
            4L to "Спереди с обеих сторон",
            5L to "Сзади слева",
            6L to "Сзади справа",
            7L to "Сзади с обеих сторон",
        ),
    ) + lightStateProperties.associateWith { lightStates } +
        lightSwitchProperties.associateWith { lightSwitchStates }
    }

    fun decode(propertyId: Int, apiName: String, raw: RawVehicleValue): AospDecodedValue {
        specialValue(apiName, raw)?.let { return it }
        enumValues[apiName]?.let { values -> return decodeEnum(raw, values) }
        numberRules[apiName]?.let { rule -> return decodeNumber(raw, rule) }
        return when (propertyId and TYPE_MASK) {
            TYPE_BOOLEAN -> decodeBoolean(raw)
            TYPE_STRING -> AospDecodedValue(raw.text, CarValue.StringValue(raw.text), true)
            TYPE_INT32, TYPE_INT64, TYPE_FLOAT -> raw.number?.let { number ->
                AospDecodedValue(formatNumber(number), CarValue.FloatValue(number), true)
            } ?: undecoded(raw)
            TYPE_INT32_VECTOR, TYPE_INT64_VECTOR, TYPE_FLOAT_VECTOR, TYPE_BYTES ->
                raw.numbers?.let { numbers ->
                    AospDecodedValue(
                        displayValue = numbers.joinToString(prefix = "[", postfix = "]", transform = ::formatNumber),
                        value = CarValue.StringValue(raw.text),
                        decoded = true,
                    )
                } ?: undecoded(raw)
            else -> undecoded(raw)
        }
    }

    fun areaLabel(propertyId: Int, areaId: Int): String? {
        if (areaId == 0) return null
        val labels = when (propertyId and AREA_TYPE_MASK) {
            AREA_TYPE_SEAT -> seatAreas
            AREA_TYPE_WHEEL -> wheelAreas
            AREA_TYPE_DOOR -> doorAreas
            AREA_TYPE_MIRROR -> mirrorAreas
            AREA_TYPE_WINDOW -> windowAreas
            else -> emptyMap()
        }
        return decomposeArea(areaId, labels)
            ?: "area 0x${areaId.toUInt().toString(16).padStart(8, '0')}"
    }

    private fun specialValue(apiName: String, raw: RawVehicleValue): AospDecodedValue? = when (apiName) {
        "EPOCH_TIME" -> raw.number?.toLong()?.let { millis ->
            val text = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))
            AospDecodedValue(text, CarValue.StringValue(text), true)
        }
        "EV_CHARGE_TIME_REMAINING" -> raw.number?.toLong()?.let { seconds ->
            val text = formatDuration(seconds)
            AospDecodedValue(text, CarValue.StringValue(text), true)
        }
        "INFO_EXTERIOR_DIMENSIONS" -> raw.numbers?.let { dimensions ->
            val names = listOf(
                "Высота", "Длина", "Ширина", "Ширина с зеркалами",
                "Колёсная база", "Передняя колея", "Задняя колея", "Диаметр разворота",
            )
            val text = dimensions.mapIndexed { index, value ->
                "${names.getOrElse(index) { "Размер ${index + 1}" }}: ${formatNumber(value)} мм"
            }.joinToString(" · ")
            AospDecodedValue(text, CarValue.StringValue(text), true)
        }
        "WHEEL_TICK" -> raw.numbers?.let { ticks ->
            val names = listOf("Сброс", "Переднее левое", "Переднее правое", "Заднее правое", "Заднее левое")
            val text = ticks.mapIndexed { index, value ->
                "${names.getOrElse(index) { "Значение ${index + 1}" }}: ${formatNumber(value)}"
            }.joinToString(" · ")
            AospDecodedValue(text, CarValue.StringValue(text), true)
        }
        else -> null
    }

    private fun decodeNumber(raw: RawVehicleValue, rule: NumberRule): AospDecodedValue {
        raw.number?.let { number ->
            val converted = number * rule.multiplier
            return AospDecodedValue(
                displayValue = "${formatNumber(converted, rule.decimalPlaces)} ${rule.unit}",
                value = CarValue.FloatValue(converted),
                decoded = true,
            )
        }
        raw.numbers?.let { numbers ->
            val converted = numbers.map { it * rule.multiplier }
            return AospDecodedValue(
                displayValue = converted.joinToString { "${formatNumber(it, rule.decimalPlaces)} ${rule.unit}" },
                value = CarValue.StringValue(raw.text),
                decoded = true,
            )
        }
        return undecoded(raw)
    }

    private fun decodeEnum(raw: RawVehicleValue, values: Map<Long, String>): AospDecodedValue {
        raw.number?.let { number ->
            val integer = number.toLong().takeIf { it.toDouble() == number }
            val label = integer?.let(values::get)
            return if (label != null) {
                AospDecodedValue(label, CarValue.StringValue(label), true)
            } else {
                AospDecodedValue("Неизвестно (${raw.text})", CarValue.StringValue(raw.text), false)
            }
        }
        raw.numbers?.let { numbers ->
            var allKnown = true
            val labels = numbers.map { number ->
                values[number.toLong()] ?: "Неизвестно (${formatNumber(number)})".also { allKnown = false }
            }
            return AospDecodedValue(labels.joinToString(), CarValue.StringValue(raw.text), allKnown)
        }
        return undecoded(raw)
    }

    private fun decodeBoolean(raw: RawVehicleValue): AospDecodedValue = when (raw.number) {
        0.0 -> AospDecodedValue("Выключено", CarValue.BooleanValue(false), true)
        1.0 -> AospDecodedValue("Включено", CarValue.BooleanValue(true), true)
        else -> undecoded(raw)
    }

    private fun undecoded(raw: RawVehicleValue) = AospDecodedValue(
        displayValue = raw.text,
        value = raw.number?.let(CarValue::FloatValue) ?: CarValue.StringValue(raw.text),
        decoded = false,
    )

    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "$totalSeconds с"
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return buildList {
            if (hours > 0) add("$hours ч")
            if (minutes > 0) add("$minutes мин")
            if (seconds > 0 || isEmpty()) add("$seconds с")
        }.joinToString(" ")
    }

    private fun decomposeArea(areaId: Int, labels: Map<Long, String>): String? {
        labels[areaId.toLong()]?.let { return it }
        val parts = labels.entries
            .filter { (bit, _) -> bit != 0L && areaId.toLong() and bit == bit }
            .sortedBy(Map.Entry<Long, String>::key)
            .map(Map.Entry<Long, String>::value)
        return parts.takeIf(List<String>::isNotEmpty)?.joinToString(" + ")
    }

    private const val AREA_TYPE_MASK = 0x0f000000
    private const val AREA_TYPE_WINDOW = 0x03000000
    private const val AREA_TYPE_MIRROR = 0x04000000
    private const val AREA_TYPE_SEAT = 0x05000000
    private const val AREA_TYPE_DOOR = 0x06000000
    private const val AREA_TYPE_WHEEL = 0x07000000

    private val gearValues = mapOf(
        0L to "Неизвестная передача",
        1L to "N",
        2L to "R",
        4L to "P",
        8L to "D",
        16L to "1-я передача",
        32L to "2-я передача",
        64L to "3-я передача",
        128L to "4-я передача",
        256L to "5-я передача",
        512L to "6-я передача",
        1024L to "7-я передача",
        2048L to "8-я передача",
        4096L to "9-я передача",
        8192L to "10-я передача",
    )
    private val warningValues = mapOf(0L to "Другое состояние", 1L to "Предупреждений нет", 2L to "Предупреждение")
    private val turnSignals = mapOf(0L to "Выключены", 1L to "Правый", 2L to "Левый")
    private val lightStates = mapOf(0L to "Выключены", 1L to "Включены", 2L to "Дневные ходовые огни")
    private val lightSwitchStates = lightStates + (256L to "Автоматический режим")
    private val lightStateProperties = listOf(
        "FOG_LIGHTS_STATE", "FRONT_FOG_LIGHTS_STATE", "REAR_FOG_LIGHTS_STATE",
        "HAZARD_LIGHTS_STATE", "HEADLIGHTS_STATE", "HIGH_BEAM_LIGHTS_STATE",
        "CABIN_LIGHTS_STATE", "READING_LIGHTS_STATE", "SEAT_FOOTWELL_LIGHTS_STATE",
        "STEERING_WHEEL_LIGHTS_STATE",
    )
    private val lightSwitchProperties = listOf(
        "FOG_LIGHTS_SWITCH", "FRONT_FOG_LIGHTS_SWITCH", "REAR_FOG_LIGHTS_SWITCH",
        "HAZARD_LIGHTS_SWITCH", "HEADLIGHTS_SWITCH", "HIGH_BEAM_LIGHTS_SWITCH",
        "CABIN_LIGHTS_SWITCH", "READING_LIGHTS_SWITCH", "SEAT_FOOTWELL_LIGHTS_SWITCH",
        "STEERING_WHEEL_LIGHTS_SWITCH",
    )
    private val automationLevels = (0L..5L).associateWith { "Уровень $it" }
    private val portLocations = mapOf(
        0L to "Неизвестно", 1L to "Спереди слева", 2L to "Спереди справа",
        3L to "Сзади справа", 4L to "Сзади слева", 5L to "Спереди", 6L to "Сзади",
    )
    private val fuelTypes = mapOf(
        0L to "Неизвестно", 1L to "Неэтилированный бензин", 2L to "Этилированный бензин",
        3L to "Дизель 1", 4L to "Дизель 2", 5L to "Биодизель", 6L to "E85",
        7L to "LPG", 8L to "CNG", 9L to "LNG", 10L to "Электричество",
        11L to "Водород", 12L to "Другое",
    )
    private val connectorTypes = mapOf(
        0L to "Неизвестно", 1L to "J1772", 2L to "Mennekes", 3L to "CHAdeMO",
        4L to "CCS Combo 1", 5L to "CCS Combo 2", 6L to "Tesla Roadster",
        7L to "Tesla HPWC", 8L to "Tesla Supercharger", 9L to "GB/T",
        10L to "GB/T DC", 11L to "Scame", 101L to "Другой разъём",
    )
    private val vehicleUnits = mapOf(
        1L to "м/с", 32L to "мм", 33L to "м", 35L to "км", 36L to "миля",
        48L to "°C", 49L to "°F", 50L to "K", 64L to "мл", 65L to "л",
        66L to "галлон США", 67L to "имперский галлон", 96L to "Вт·ч",
        100L to "А·ч", 101L to "кВт·ч", 112L to "кПа", 113L to "psi",
        114L to "бар", 144L to "миль/ч", 145L to "км/ч",
    )
    private val seatAreas = mapOf(
        0L to "Неизвестное сиденье", 1L to "Первый ряд слева", 2L to "Первый ряд по центру",
        4L to "Первый ряд справа", 16L to "Второй ряд слева", 32L to "Второй ряд по центру",
        64L to "Второй ряд справа", 256L to "Третий ряд слева", 512L to "Третий ряд по центру",
        1024L to "Третий ряд справа",
    )
    private val wheelAreas = mapOf(
        1L to "Переднее левое колесо", 2L to "Переднее правое колесо",
        4L to "Заднее левое колесо", 8L to "Заднее правое колесо",
    )
    private val mirrorAreas = mapOf(1L to "Левое зеркало", 2L to "Правое зеркало", 4L to "Салонное зеркало")
    private val doorAreas = mapOf(
        1L to "Передняя левая дверь", 4L to "Передняя правая дверь",
        16L to "Задняя левая дверь", 64L to "Задняя правая дверь",
        0x10000000L to "Капот", 0x20000000L to "Багажник",
    )
    private val windowAreas = mapOf(
        1L to "Лобовое стекло", 2L to "Заднее стекло", 16L to "Переднее левое стекло",
        64L to "Переднее правое стекло", 256L to "Заднее левое стекло",
        1024L to "Заднее правое стекло", 0x00010000L to "Люк",
    )
    private val wiperSwitchValues = mapOf(
        0L to "Другое положение", 1L to "Выключены", 2L to "Однократное срабатывание",
        3L to "Интервал 1", 4L to "Интервал 2", 5L to "Интервал 3",
        6L to "Интервал 4", 7L to "Интервал 5", 8L to "Постоянно 1",
        9L to "Постоянно 2", 10L to "Постоянно 3", 11L to "Постоянно 4",
        12L to "Постоянно 5", 13L to "Автоматический режим", 14L to "Сервисное положение",
    )
}

private fun formatNumber(value: Double, decimalPlaces: Int? = null): String {
    if (!value.isFinite()) return value.toString()
    val decimal = BigDecimal.valueOf(value)
    return (decimalPlaces?.let { decimal.setScale(it, RoundingMode.HALF_UP) } ?: decimal)
        .stripTrailingZeros()
        .toPlainString()
}
