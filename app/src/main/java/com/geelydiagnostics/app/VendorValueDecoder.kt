package com.geelydiagnostics.app

import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import java.util.Locale

/** Converts confirmed ECARX enum values while always preserving the value returned by the API. */
internal object VendorValueDecoder {

    fun sensor(apiName: String, rawValue: Int): VehicleDisplayValue {
        val existing = SENSOR_VALUES[apiName]
        if (existing?.containsKey(rawValue) == true) return decoded(apiName, rawValue, existing)
        val metadata = EcarxSensorMetadata.fields[apiName]
        metadata?.values?.get(rawValue)?.let { label ->
            return VehicleDisplayValue(display = label, raw = rawValue.toString())
        }
        val legacy = decoded(apiName, rawValue, existing)
        return if (legacy.display != legacy.raw || metadata?.unit == null) {
            legacy
        } else {
            legacy.copy(display = withUnit(legacy.display, metadata.unit))
        }
    }

    fun sensor(apiName: String, rawValue: Float): VehicleDisplayValue {
        val metadata = EcarxSensorMetadata.fields[apiName]
        val raw = formatNumber(rawValue.toDouble())
        val normalized = rawValue * (metadata?.rawToDisplayScale ?: 1f)
        return VehicleDisplayValue(
            display = withUnit(formatNumber(normalized.toDouble()), metadata?.unit),
            raw = raw,
        )
    }

    fun function(apiName: String, rawValue: Int, supportedValues: IntArray?): VehicleDisplayValue {
        val known = FUNCTION_VALUES[apiName]
        if (known?.containsKey(rawValue) == true) return decoded(apiName, rawValue, known)
        EcarxFunctionMetadata.fields[apiName]?.values?.get(rawValue)?.let { label ->
            return VehicleDisplayValue(display = label, raw = rawValue.toString())
        }
        if (apiName in EcarxFunctionMetadata.commonValueKeys) {
            COMMON_FUNCTION_VALUES[rawValue]?.let { label ->
                return VehicleDisplayValue(display = label, raw = rawValue.toString())
            }
        }

        val supported = supportedValues?.toSet().orEmpty()
        val booleanLabel = if (supported == setOf(0, 1)) {
            when (rawValue) {
                0 -> "Выключено"
                1 -> "Включено"
                else -> null
            }
        } else {
            null
        }
        return VehicleDisplayValue(display = booleanLabel ?: rawValue.toString(), raw = rawValue.toString())
    }

    fun carInfo(apiName: String, rawValue: Int, fallbackLabel: String? = null): VehicleDisplayValue {
        val metadata = EcarxCarInfoMetadata.field(apiName)
        val label = metadata?.values?.get(rawValue) ?: fallbackLabel
        val raw = rawValue.toString()
        return VehicleDisplayValue(
            display = label ?: withUnit(raw, metadata?.unit),
            raw = raw,
        )
    }

    fun carInfo(apiName: String, rawValues: IntArray): VehicleDisplayValue {
        val metadata = EcarxCarInfoMetadata.field(apiName)
        val raw = rawValues.joinToString()
        val display = rawValues.joinToString { value ->
            metadata?.values?.get(value) ?: value.toString()
        }
        return VehicleDisplayValue(display = display, raw = raw)
    }

    fun carInfo(apiName: String, rawValue: Float): VehicleDisplayValue {
        val raw = formatNumber(rawValue.toDouble())
        return VehicleDisplayValue(
            display = withUnit(raw, EcarxCarInfoMetadata.field(apiName)?.unit),
            raw = raw,
        )
    }

    private fun decoded(apiName: String, rawValue: Int, values: Map<Int, String>?): VehicleDisplayValue {
        val symbol = values?.get(rawValue)
        return VehicleDisplayValue(
            display = symbol?.let { displayName(apiName, it) } ?: rawValue.toString(),
            raw = rawValue.toString(),
        )
    }

    private fun displayName(apiName: String, symbol: String): String {
        EXACT_LABELS[symbol]?.let { return it }

        val context = apiName.split('_').toSet()
        val meaningful = symbol.split('_').filterNot { token ->
            token in context || token in GENERIC_TOKENS
        }
        val tokens = meaningful.ifEmpty { symbol.split('_') }
        return tokens.joinToString(" ") { token ->
            TOKEN_LABELS[token] ?: token.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase)
        }
    }

    private fun enumValues(vararg values: Pair<String, Int>): Map<Int, String> =
        values.associate { (name, raw) -> raw to name }

    private val SENSOR_VALUES = mapOf(
        "SENSOR_TYPE_ABS_WARNING" to enumValues(
            "ABS_WARNING_STATE_FLSG" to 1058306,
            "ABS_WARNING_STATE_OFF" to 1058308,
            "ABS_WARNING_STATE_ON" to 1058305,
            "ABS_WARNING_STATE_RESD" to 1058307,
        ),
        "SENSOR_TYPE_ALRM_STS" to enumValues(
            "SENSOR_VALUE_ALRM_STS_DISARMD" to 2122497,
            "SENSOR_VALUE_ALRM_STS_ARMD" to 2122498,
            "SENSOR_VALUE_ALRM_STS_ACTV" to 2122499,
        ),
        "SENSOR_TYPE_AQI_LEVEL_AMBIENT" to enumValues(
            "AQI_LEVEL_NO_POLLUTION" to 2106113,
            "AQI_LEVEL_LOW_POLLUTION" to 2106114,
            "AQI_LEVEL_MEDIUM_POLLUTION" to 2106115,
            "AQI_LEVEL_HIGH_POLLUTION" to 2106116,
            "AQI_LEVEL_HIGHER_POLLUTION" to 2106117,
            "AQI_LEVEL_LOWER_POLLUTION" to 2106118,
            "AQI_LEVEL_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_BRAKE_FLUID_LEVEL" to enumValues(
            "BRAKE_FLUID_LEVEL_LOW" to 2098690,
            "BRAKE_FLUID_LEVEL_NORMAL" to 2098689,
            "BRAKE_FLUID_LEVEL_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_BRK_WARNING" to enumValues(
            "BRK_WARNING_STATE_OFF" to 1058050,
            "BRK_WARNING_STATE_ON" to 1058049,
        ),
        "SENSOR_TYPE_CAR_MODE" to enumValues(
            "CAR_MODE_CRASH" to 2102276,
            "CAR_MODE_DYNO" to 2102277,
            "CAR_MODE_FACTORY" to 2102274,
            "CAR_MODE_NORMAL" to 2102273,
            "CAR_MODE_TRANSPORT" to 2102275,
            "CAR_MODE_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_DAY_NIGHT" to enumValues(
            "DAY_NIGHT_MODE_DAY" to 2101249,
            "DAY_NIGHT_MODE_NIGHT" to 2101250,
            "DAY_NIGHT_MODE_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_DRIVER_TIREDNESS_STATUS" to enumValues(
            "DriverTirednessStatus_UNAVAILABLE" to 3149824,
            "DriverTirednessStatus_UNKNOWN" to 3149825,
            "DriverTirednessStatus_NO_WARNING" to 3149826,
            "DriverTirednessStatus_DISTRACTIVE" to 3149827,
            "DriverTirednessStatus_WARNING_LEVEL_1" to 3149828,
            "DriverTirednessStatus_WARNING_LEVEL_2" to 3149829,
            "DriverTirednessStatus_RESERVED" to 3149830,
        ),
        "SENSOR_TYPE_ENGINE_COOLANT_LEVEL" to enumValues(
            "ENGINE_COOLANT_LEVEL_LOW" to 2098434,
            "ENGINE_COOLANT_LEVEL_LOW_1" to 2098435,
            "ENGINE_COOLANT_LEVEL_NORMAL" to 2098433,
            "ENGINE_COOLANT_LEVEL_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_ENGINE_OIL_LEVEL" to enumValues(
            "ENGINE_OIL_LEVEL_HIGH" to 2098180,
            "ENGINE_OIL_LEVEL_LOW_1" to 2098178,
            "ENGINE_OIL_LEVEL_LOW_2" to 2098179,
            "ENGINE_OIL_LEVEL_OK" to 2098177,
            "ENGINE_OIL_LEVEL_RESD" to 2098182,
            "ENGINE_OIL_LEVEL_SRVRQRD" to 2098181,
            "ENGINE_OIL_LEVEL_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_ENGINE_START_STOP_STATE" to enumValues(
            "ENGINE_START_STOP_STATE_STANDBY" to 2103042,
            "ENGINE_START_STOP_STATE_STOPPED" to 2103043,
            "ENGINE_START_STOP_STATE_STARTER_RESTART" to 2103044,
            "ENGINE_START_STOP_STATE_ENGINE_RESTART" to 2103045,
            "ENGINE_START_STOP_STATE_OPERATION" to 2103046,
            "ENGINE_START_STOP_STATE_AUTO_STOPPING" to 2103047,
            "ENGINE_START_STOP_STATE_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_ESC_WARNING" to enumValues(
            "ESC_WARNING_STATE_FLSG" to 1058562,
            "ESC_WARNING_STATE_OFF" to 1058564,
            "ESC_WARNING_STATE_ON" to 1058561,
            "ESC_WARNING_STATE_RESD" to 1058563,
        ),
        "SENSOR_TYPE_GEAR" to enumValues(
            "SENSOR_GEAR_FIRST" to 2097665,
            "SENSOR_GEAR_SECOND" to 2097666,
            "SENSOR_GEAR_THIRD" to 2097667,
            "SENSOR_GEAR_FOURTH" to 2097668,
            "SENSOR_GEAR_FIFTH" to 2097669,
            "SENSOR_GEAR_SIXTH" to 2097670,
            "SENSOR_GEAR_SEVENTH" to 2097671,
            "SENSOR_GEAR_EIGHTH" to 2097672,
            "SENSOR_GEAR_NINTH" to 2097673,
            "SENSOR_GEAR_TENTH" to 2097674,
            "SENSOR_GEAR_DRIVE" to 2097696,
            "SENSOR_GEAR_NEUTRAL" to 2097680,
            "SENSOR_GEAR_PARK" to 2097712,
            "SENSOR_GEAR_REVERSE" to 2097728,
            "SENSOR_GEAR_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_IGNITION_STATE" to enumValues(
            "IGNITION_STATE_ACC" to 2097412,
            "IGNITION_STATE_DRIVING" to 2097415,
            "IGNITION_STATE_LOCK" to 2097410,
            "IGNITION_STATE_OFF" to 2097411,
            "IGNITION_STATE_ON" to 2097413,
            "IGNITION_STATE_START" to 2097414,
            "IGNITION_STATE_UNDEFINED" to 2097409,
        ),
        "SENSOR_TYPE_PM25_LEVEL_AMBIENT" to enumValues(
            "PM25_LEVEL_NO_POLLUTION" to 2105601,
            "PM25_LEVEL_LOW_POLLUTION" to 2105602,
            "PM25_LEVEL_MEDIUM_POLLUTION" to 2105603,
            "PM25_LEVEL_HIGH_POLLUTION" to 2105604,
            "PM25_LEVEL_HIGHER_POLLUTION" to 2105605,
            "PM25_LEVEL_LOWER_POLLUTION" to 2105606,
            "PM25_LEVEL_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_RAIN" to enumValues(
            "RAINSENSORSENSILVL_LVL1" to 0,
            "RAINSENSORSENSILVL_LVL2" to 1,
            "RAINSENSORSENSILVL_LVL3" to 2,
            "RAINSENSORSENSILVL_LVL4" to 3,
            "RAINSENSORSENSILVL_LVL5" to 4,
            "RAINSENSORSENSILVL_LVL6" to 5,
            "RAINSENSORSENSILVL_LVL7" to 6,
        ),
        "SENSOR_TYPE_SAFE_BELT_DRIVER" to enumValues(
            "SAFE_BELT_STATE_UNBUCKLED" to 2101761,
            "SAFE_BELT_STATE_BUCKLED" to 2101762,
            "SAFE_BELT_STATE_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_SEAT_OCCUPATION_STATUS_DRIVER" to enumValues(
            "SEAT_OCCUPATION_STATUS_NONE" to 2110209,
            "SEAT_OCCUPATION_STATUS_OCCUPIED" to 2110210,
            "SEAT_OCCUPATION_STATUS_FAULT" to 2110211,
            "SEAT_OCCUPATION_STATUS_UNKNOWN" to -1,
        ),
        "SENSOR_TYPE_TIREDNESS_DRIVING_STATE" to enumValues(
            "TIREDNESS_DRIVING_ON" to 3148545,
            "TIREDNESS_DRIVING_OFF" to 3148546,
        ),
    )

    private val FUNCTION_VALUES = mapOf(
        "SETTING_FUNC_LAMP_AUTOLIGHT" to enumValues(
            "LAMP_AUTOLIGHT_VALUE_LATER" to 537133825,
            "LAMP_AUTOLIGHT_VALUE_NORMAL" to 537133826,
            "LAMP_AUTOLIGHT_VALUE_EARLIER" to 537133827,
        ),
        "SETTING_FUNC_SPEED_CONTROL_MODE" to enumValues(
            "SPEED_CONTROL_MODE_ACC" to 537069058,
            "SPEED_CONTROL_MODE_CC" to 537069057,
            "SPEED_CONTROL_MODE_GPILOT" to 537069059,
            "SPEED_CONTROL_MODE_OFF" to 0,
        ),
        "SETTING_FUNC_AMBIENCE_LIGHT_EXPERIENCE" to enumValues(
            "AMBIENCE_LIGHT_EXPERIENCE_CUSTOM" to 537526530,
            "AMBIENCE_LIGHT_EXPERIENCE_FULL" to 537526529,
        ),
        "SETTING_FUNC_AMBIENCE_LIGHT_MAINCOLOR" to enumValues(
            "AMBIENCE_LIGHT_MAINCOLOR_BREATHE_MODE" to 537526790,
            "AMBIENCE_LIGHT_MAINCOLOR_DRIVERMODE" to 537526786,
            "AMBIENCE_LIGHT_MAINCOLOR_MUSIC" to 537526788,
            "AMBIENCE_LIGHT_MAINCOLOR_NONE" to 0,
            "AMBIENCE_LIGHT_MAINCOLOR_NON_POLAR" to 537526789,
            "AMBIENCE_LIGHT_MAINCOLOR_SETCOLOR" to 537526787,
            "AMBIENCE_LIGHT_MAINCOLOR_SPEED_MODE" to 537526791,
            "AMBIENCE_LIGHT_MAINCOLOR_THEME" to 537526785,
            "AMBIENCE_LIGHT_MAINCOLOR_WEATHER" to 537526792,
        ),
        "SETTING_FUNC_ARTIFICIAL_SOUND_TYPE" to enumValues(
            "ARTIFICIAL_SOUND_TYPE_1" to 538575873,
            "ARTIFICIAL_SOUND_TYPE_2" to 538575874,
            "ARTIFICIAL_SOUND_TYPE_3" to 538575875,
            "ARTIFICIAL_SOUND_TYPE_4" to 538575876,
            "ARTIFICIAL_SOUND_TYPE_5" to 538575877,
            "ARTIFICIAL_SOUND_TYPE_6" to 538575878,
            "ARTIFICIAL_SOUND_TYPE_7" to 538575879,
            "ARTIFICIAL_SOUND_TYPE_8" to 538575880,
            "ARTIFICIAL_SOUND_TYPE_NONE" to 0,
        ),
        "SETTING_FUNC_AUTO_CLOSE_WINDOW" to enumValues(
            "AUTO_CLOSE_WINDOW_KEY_LONG_PRESS" to 537396226,
            "AUTO_CLOSE_WINDOW_OFF" to 0,
            "AUTO_CLOSE_WINDOW_VEHICLE_LOCK" to 537396225,
        ),
        "SETTING_FUNC_AUTO_SHOW_MODE" to enumValues(
            "SETTING_FUNC_AUTO_SHOW_MODE_TEXT_FALSE" to 1,
            "SETTING_FUNC_AUTO_SHOW_MODE_TEXT_GEAR" to 2,
            "SETTING_FUNC_AUTO_SHOW_MODE_TEXT_NORMAL" to 0,
        ),
        "SETTING_FUNC_AUTO_SHOW_MODE_TEXT" to enumValues(
            "SETTING_FUNC_AUTO_SHOW_MODE_TEXT_FALSE" to 1,
            "SETTING_FUNC_AUTO_SHOW_MODE_TEXT_GEAR" to 2,
            "SETTING_FUNC_AUTO_SHOW_MODE_TEXT_NORMAL" to 0,
        ),
        "SETTING_FUNC_CAR_LOCATOR" to enumValues(
            "CAR_LOCATOR_REMINDER_MODE_LIGHT" to 538313730,
            "CAR_LOCATOR_REMINDER_MODE_LIGHT_SOUND" to 538313731,
            "CAR_LOCATOR_REMINDER_MODE_OFF" to 0,
            "CAR_LOCATOR_REMINDER_MODE_SOUND" to 538313729,
        ),
        "SETTING_FUNC_CAR_LOCATOR_REMINDER_MODE" to enumValues(
            "CAR_LOCATOR_REMINDER_MODE_LIGHT" to 538313730,
            "CAR_LOCATOR_REMINDER_MODE_LIGHT_SOUND" to 538313731,
            "CAR_LOCATOR_REMINDER_MODE_OFF" to 0,
            "CAR_LOCATOR_REMINDER_MODE_SOUND" to 538313729,
        ),
        "SETTING_FUNC_DAYMODE_SETTING" to enumValues(
            "DAYMODE_SETTING_BRIGHTNESS_DAY" to 538247425,
            "DAYMODE_SETTING_BRIGHTNESS_NIGHT" to 538247426,
            "DAYMODE_SETTING_BRIGHTNESS_AUTO" to 538247427,
            "DAYMODE_SETTING_CUSTOM" to 538247428,
            "DAYMODE_SETTING_SUNRISE_AND_SUNSET" to 538247429,
            "DAYMODE_SETTING_OFF" to 0,
        ),
        "SETTING_FUNC_ENERGY_REGENERATION" to enumValues(
            "ENERGY_REGENERATION_LEVEL_AUTO" to 537003268,
            "ENERGY_REGENERATION_LEVEL_HIGH" to 537003267,
            "ENERGY_REGENERATION_LEVEL_LOW" to 537003265,
            "ENERGY_REGENERATION_LEVEL_MID" to 537003266,
            "ENERGY_REGENERATION_LEVEL_OFF" to 0,
        ),
        "SETTING_FUNC_ESM_VOLUME" to enumValues(
            "ESM_VOLUME_LEVEL_HIGH" to 538575363,
            "ESM_VOLUME_LEVEL_LOW" to 538575361,
            "ESM_VOLUME_LEVEL_MID" to 538575362,
            "ESM_VOLUME_LEVEL_OFF" to 0,
        ),
        "SETTING_FUNC_FORWARD_COLLISION_WARN" to enumValues(
            "FORWARD_COLLISION_WARN_SNVTY_HIGH" to 537788931,
            "FORWARD_COLLISION_WARN_SNVTY_LOW" to 537788929,
            "FORWARD_COLLISION_WARN_SNVTY_NORMAL" to 537788930,
            "FORWARD_COLLISION_WARN_SNVTY_OFF" to 0,
        ),
        "SETTING_FUNC_HEAD_RESTRAINT_AUDIO" to enumValues(
            "SETTING_FUNC_HEAD_RESTRAINT_AUDIO_DRVING" to 539099906,
            "SETTING_FUNC_HEAD_RESTRAINT_AUDIO_PRIVATE" to 539099907,
            "SETTING_FUNC_HEAD_RESTRAINT_AUDIO_SHARE" to 539099905,
        ),
        "SETTING_FUNC_KEYLESS_UNLOCKING" to enumValues(
            "KEYLESS_UNLOCKING_ALL_DOORS" to 537920513,
            "KEYLESS_UNLOCKING_OFF" to 0,
            "KEYLESS_UNLOCKING_SINGLE_DOOR" to 537920514,
        ),
        "SETTING_FUNC_LANE_CHANGE_WARNING_MODE" to enumValues(
            "LANE_CHANGE_WARNING_MODE_OFF" to 0,
            "LANE_CHANGE_WARNING_MODE_SOUND" to 537330706,
            "LANE_CHANGE_WARNING_MODE_VISUAL" to 537330705,
            "LANE_CHANGE_WARNING_MODE_VISUAL_SOUND" to 537330707,
        ),
        "SETTING_FUNC_LANE_KEEPING_AID" to enumValues(
            "LANE_KEEPING_AID_MODE_INTV" to 537330178,
            "LANE_KEEPING_AID_MODE_OFF" to 0,
            "LANE_KEEPING_AID_MODE_WARN" to 537330179,
            "LANE_KEEPING_AID_MODE_WARN_INTV" to 537330177,
            "LANE_KEEPING_AID_WARNING_HAPTIC" to 537330946,
            "LANE_KEEPING_AID_WARNING_OFF" to 0,
            "LANE_KEEPING_AID_WARNING_SOUND" to 537330945,
            "LANE_KEEPING_AID_WARNING_SOUND_HAPTIC" to 537330947,
        ),
        "SETTING_FUNC_LANE_KEEPING_AID_MODE" to enumValues(
            "LANE_KEEPING_AID_MODE_INTV" to 537330178,
            "LANE_KEEPING_AID_MODE_OFF" to 0,
            "LANE_KEEPING_AID_MODE_WARN" to 537330179,
            "LANE_KEEPING_AID_MODE_WARN_INTV" to 537330177,
        ),
        "SETTING_FUNC_LANE_KEEPING_AID_WARNING" to enumValues(
            "LANE_KEEPING_AID_WARNING_HAPTIC" to 537330946,
            "LANE_KEEPING_AID_WARNING_OFF" to 0,
            "LANE_KEEPING_AID_WARNING_SOUND" to 537330945,
            "LANE_KEEPING_AID_WARNING_SOUND_HAPTIC" to 537330947,
        ),
        "SETTING_FUNC_MIRROR_DIPPING" to enumValues(
            "MIRROR_DIPPING_BOTH" to 537461507,
            "MIRROR_DIPPING_DRIVER" to 537461505,
            "MIRROR_DIPPING_OFF" to 0,
            "MIRROR_DIPPING_PASSENGER" to 537461506,
        ),
        "SETTING_FUNC_PARK_ASSIST_SYS_VOLUME" to enumValues(
            "PARK_ASSIST_SYS_VOLUME_HIGH" to 537723395,
            "PARK_ASSIST_SYS_VOLUME_LOW" to 537723393,
            "PARK_ASSIST_SYS_VOLUME_MID" to 537723394,
            "PARK_ASSIST_SYS_VOLUME_OFF" to 0,
        ),
        "SETTING_FUNC_PEB_MODE" to enumValues(
            "PEB_MODE_MSP" to 537264642,
            "PEB_MODE_OFF" to 0,
            "PEB_MODE_PEB" to 537264641,
        ),
        "SETTING_FUNC_PGEAR_UNLOCK" to enumValues(
            "PGEAR_UNLOCK_TYP_OFF" to 2,
            "PGEAR_UNLOCK_TYP_ON" to 1,
        ),
        "SETTING_FUNC_REFUELING_SWT" to enumValues(
            "REFUELING_SWT_UNLCK" to 538379009,
        ),
        "SETTING_FUNC_ROTATED_WHEELS_WARNING" to enumValues(
            "ROTATED_WHEELS_WARNING_INFO_NONE" to 0,
            "ROTATED_WHEELS_WARNING_INFO_RIGHTWARD" to 538772226,
        ),
        "SETTING_FUNC_ROTATED_WHEELS_WARNING_INFO" to enumValues(
            "ROTATED_WHEELS_WARNING_INFO_NONE" to 0,
            "ROTATED_WHEELS_WARNING_INFO_RIGHTWARD" to 538772226,
        ),
        "SETTING_FUNC_SCREEN_SAVER_TIME" to enumValues(
            "SCREEN_SAVER_TIME_10" to 539035394,
            "SCREEN_SAVER_TIME_5" to 539035393,
            "SCREEN_SAVER_TIME_NEVER" to 539035395,
        ),
        "SETTING_FUNC_SPEED_LIMITATION_MODE" to enumValues(
            "SPEED_LIMITATION_MODE_ASL" to 537068802,
            "SPEED_LIMITATION_MODE_AVSL" to 537068801,
            "SPEED_LIMITATION_MODE_OFF" to 0,
        ),
        "SETTING_FUNC_STEERING_ASSISTANCE_LEVEL" to enumValues(
            "STEERING_ASSISTANCE_LEVEL_HIGH" to 537331713,
            "STEERING_ASSISTANCE_LEVEL_LOW" to 537331715,
            "STEERING_ASSISTANCE_LEVEL_MEDIUM" to 537331714,
            "STEERING_ASSISTANCE_LEVEL_OFF" to 0,
        ),
        "SETTING_FUNC_SUSPENSION_HEIGHT_ADJUST" to enumValues(
            "SUSPENSION_HEIGHT_ADJUST_LEVEL_HIGH_1" to 538509570,
            "SUSPENSION_HEIGHT_ADJUST_LEVEL_HIGH_2" to 538509569,
            "SUSPENSION_HEIGHT_ADJUST_LEVEL_LOW_1" to 538509572,
            "SUSPENSION_HEIGHT_ADJUST_LEVEL_LOW_2" to 538509573,
            "SUSPENSION_HEIGHT_ADJUST_LEVEL_NORMAL" to 538509571,
            "SUSPENSION_HEIGHT_ADJUST_LEVEL_OFF" to 0,
        ),
    )

    private val COMMON_FUNCTION_VALUES = mapOf(
        0 to "Выключено",
        1 to "Включено",
        2 to "По умолчанию",
        253 to "Ошибка",
        254 to "Нет значения",
        255 to "Неизвестно",
    )

    private val EXACT_LABELS = mapOf(
        "SENSOR_GEAR_FIRST" to "1",
        "SENSOR_GEAR_SECOND" to "2",
        "SENSOR_GEAR_THIRD" to "3",
        "SENSOR_GEAR_FOURTH" to "4",
        "SENSOR_GEAR_FIFTH" to "5",
        "SENSOR_GEAR_SIXTH" to "6",
        "SENSOR_GEAR_SEVENTH" to "7",
        "SENSOR_GEAR_EIGHTH" to "8",
        "SENSOR_GEAR_NINTH" to "9",
        "SENSOR_GEAR_TENTH" to "10",
        "SENSOR_GEAR_DRIVE" to "D",
        "SENSOR_GEAR_NEUTRAL" to "N",
        "SENSOR_GEAR_PARK" to "P",
        "SENSOR_GEAR_REVERSE" to "R",
        "SENSOR_GEAR_UNKNOWN" to "Неизвестно",
        "IGNITION_STATE_ACC" to "ACC",
        "IGNITION_STATE_DRIVING" to "Движение",
        "IGNITION_STATE_LOCK" to "Заблокировано",
        "IGNITION_STATE_OFF" to "Выключено",
        "IGNITION_STATE_ON" to "Включено",
        "IGNITION_STATE_START" to "Запуск",
        "IGNITION_STATE_UNDEFINED" to "Не определено",
        "DAY_NIGHT_MODE_DAY" to "День",
        "DAY_NIGHT_MODE_NIGHT" to "Ночь",
        "DAY_NIGHT_MODE_UNKNOWN" to "Неизвестно",
        "LAMP_AUTOLIGHT_VALUE_EARLIER" to "Раньше",
        "LAMP_AUTOLIGHT_VALUE_NORMAL" to "Обычно",
        "LAMP_AUTOLIGHT_VALUE_LATER" to "Позже",
        "RAINSENSORSENSILVL_LVL1" to "Уровень 1",
        "RAINSENSORSENSILVL_LVL2" to "Уровень 2",
        "RAINSENSORSENSILVL_LVL3" to "Уровень 3",
        "RAINSENSORSENSILVL_LVL4" to "Уровень 4",
        "RAINSENSORSENSILVL_LVL5" to "Уровень 5",
        "RAINSENSORSENSILVL_LVL6" to "Уровень 6",
        "RAINSENSORSENSILVL_LVL7" to "Уровень 7",
        "SENSOR_VALUE_ALRM_STS_DISARMD" to "Снята с охраны",
        "SENSOR_VALUE_ALRM_STS_ARMD" to "Под охраной",
        "SENSOR_VALUE_ALRM_STS_ACTV" to "Тревога",
        "DriverTirednessStatus_UNAVAILABLE" to "Недоступно",
        "DriverTirednessStatus_UNKNOWN" to "Неизвестно",
        "DriverTirednessStatus_NO_WARNING" to "Нет предупреждения",
        "DriverTirednessStatus_DISTRACTIVE" to "Водитель отвлёкся",
        "DriverTirednessStatus_WARNING_LEVEL_1" to "Предупреждение 1",
        "DriverTirednessStatus_WARNING_LEVEL_2" to "Предупреждение 2",
        "DriverTirednessStatus_RESERVED" to "Резерв",
        "SAFE_BELT_STATE_UNBUCKLED" to "Не пристёгнут",
        "SAFE_BELT_STATE_BUCKLED" to "Пристёгнут",
        "SAFE_BELT_STATE_UNKNOWN" to "Неизвестно",
        "SEAT_OCCUPATION_STATUS_NONE" to "Свободно",
        "SEAT_OCCUPATION_STATUS_OCCUPIED" to "Занято",
        "SEAT_OCCUPATION_STATUS_FAULT" to "Неисправность",
        "SEAT_OCCUPATION_STATUS_UNKNOWN" to "Неизвестно",
    )

    private val GENERIC_TOKENS = setOf(
        "SETTING", "FUNC", "TYPE", "VALUE", "STATE", "MODE", "LEVEL", "LVL", "SNVTY", "INFO",
    )

    private val TOKEN_LABELS = mapOf(
        "OFF" to "Выключено",
        "ON" to "Включено",
        "UNKNOWN" to "Неизвестно",
        "UNDEFINED" to "Не определено",
        "NORMAL" to "Норма",
        "OK" to "Норма",
        "LOW" to "Низкий",
        "HIGH" to "Высокий",
        "MID" to "Средний",
        "MEDIUM" to "Средний",
        "AUTO" to "Авто",
        "FLSG" to "Мигает",
        "RESD" to "Резерв",
        "SRVRQRD" to "Требуется сервис",
        "CRASH" to "Аварийный",
        "DYNO" to "Диностенд",
        "FACTORY" to "Заводской",
        "TRANSPORT" to "Транспортировочный",
        "DAY" to "День",
        "NIGHT" to "Ночь",
        "SOUND" to "Звук",
        "VISUAL" to "Визуально",
        "HAPTIC" to "Вибрация",
        "INTV" to "Вмешательство",
        "WARN" to "Предупреждение",
        "DRIVER" to "Водитель",
        "PASSENGER" to "Пассажир",
        "BOTH" to "Оба",
        "NONE" to "Нет",
        "NEVER" to "Никогда",
        "CUSTOM" to "Пользовательский",
        "FULL" to "Полный",
        "PRIVATE" to "Личный",
        "SHARE" to "Общий",
        "ALL" to "Все",
        "DOORS" to "двери",
        "SINGLE" to "Одна",
        "RIGHTWARD" to "Вправо",
        "UNLCK" to "Разблокировано",
    )

    private fun withUnit(value: String, unit: String?): String =
        if (unit.isNullOrBlank()) value else "$value $unit"

    private fun formatNumber(value: Double): String =
        if (value.isFinite()) {
            java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        } else {
            value.toString()
        }
}
