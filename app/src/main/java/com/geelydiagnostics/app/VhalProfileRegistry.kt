package com.geelydiagnostics.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.Base64
import java.util.zip.GZIPInputStream

enum class VhalProfile(
    val key: String,
    val vehicle: String,
) {
    G426("G426", "Boyue Cool / Cityray"),
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

internal enum class VhalValueType(val label: String) {
    INT("int"), FLOAT("float"), CHAR("char"), STRING("string"), BOOLEAN("boolean"),
}

internal data class VhalRawValue(
    val text: String,
    val number: Double? = null,
) {
    companion object {
        fun number(value: Number): VhalRawValue = VhalRawValue(
            text = formatNumber(value.toDouble()),
            number = value.toDouble(),
        )
    }
}

internal data class VhalSignalSpec(
    val profile: VhalProfile,
    val propertyId: Int,
    val readSignalId: Int,
    val apiName: String,
    val title: String,
    val valueType: VhalValueType,
    val unit: String?,
    val transform: List<VhalTransformStep>,
)

internal sealed interface VhalTransformStep {
    data class Mapping(val values: Map<String, Any>, val default: Any?) : VhalTransformStep
    data class Expression(val value: String) : VhalTransformStep
}

/**
 * Read-only AutoService mappings for all twelve VHAL profiles. The embedded dataset has been
 * stripped of every writeSignalId/writeTransform field before compression.
 */
internal object VhalProfileRegistry {
    private val cache = mutableMapOf<VhalProfile, List<VhalSignalSpec>>()

    fun signals(profile: VhalProfile): List<VhalSignalSpec> = synchronized(cache) {
        cache.getOrPut(profile) { parseProfile(profile) }
    }

    fun decode(spec: VhalSignalSpec, raw: VhalRawValue): ApiValue {
        var transformed: Any = raw.number ?: raw.text
        spec.transform.forEach { step ->
            transformed = when (step) {
                is VhalTransformStep.Mapping -> {
                    step.values[normalizeMapKey(transformed)] ?: step.default ?: transformed
                }
                is VhalTransformStep.Expression -> applyExpression(transformed, step.value)
            }
        }

        val number = (transformed as? Number)?.toDouble()
            ?: (transformed as? String)?.toDoubleOrNull()
        val integer = number?.toLong()?.takeIf { it.toDouble() == number }
        val label = integer?.let { valueLabels(spec.propertyId)[it] }
        val display = when {
            label != null -> label
            spec.propertyId in WINDOW_PROPERTY_IDS && integer != null -> "Уровень $integer/10"
            transformed is String && transformed.toDoubleOrNull() == null -> transformed
            number != null -> withUnit(formatNumber(number), spec.unit)
            else -> transformed.toString()
        }
        return ApiValue(display = display, raw = raw.text)
    }

    private fun parseProfile(profile: VhalProfile): List<VhalSignalSpec> {
        val array = JSONObject(decompressProfiles()).getJSONArray(profile.key)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val propertyId = item.getInt("propertyId")
            val definition = PROPERTY_DEFINITIONS[propertyId]
                ?: VhalPropertyDefinition("Property $propertyId", VhalValueType.INT)
            VhalSignalSpec(
                profile = profile,
                propertyId = propertyId,
                readSignalId = item.getInt("readSignalId"),
                apiName = item.getString("readSignalName"),
                title = definition.title,
                valueType = definition.valueType,
                unit = definition.unit,
                transform = parseTransform(item.optJSONObject("readTransform")),
            )
        }
    }

    private fun parseTransform(transform: JSONObject?): List<VhalTransformStep> {
        val steps = transform?.optJSONArray("steps") ?: return emptyList()
        return (0 until steps.length()).mapNotNull { index ->
            val step = steps.getJSONObject(index)
            when (step.optString("type")) {
                "mapping" -> {
                    val valuesObject = step.getJSONObject("map")
                    val values = valuesObject.keys().asSequence().associateWith { key ->
                        valuesObject.get(key).jsonValue()
                    }
                    VhalTransformStep.Mapping(
                        values = values,
                        default = if (step.has("default")) step.get("default").jsonValue() else null,
                    )
                }
                "expression" -> VhalTransformStep.Expression(step.getString("expression"))
                else -> null
            }
        }
    }

    private fun applyExpression(value: Any, expression: String): Any {
        val number = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: return value
        val match = EXPRESSION.matchEntire(expression.trim()) ?: return value
        val operand = match.groupValues[2].toDoubleOrNull() ?: return value
        return when (match.groupValues[1]) {
            "/" -> if (operand == 0.0) value else number / operand
            "*" -> number * operand
            "+" -> number + operand
            "-" -> number - operand
            else -> value
        }
    }

    private fun decompressProfiles(): String {
        val bytes = Base64.getDecoder().decode(COMPRESSED_READ_PROFILES)
        return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
    }

    private data class VhalPropertyDefinition(
        val title: String,
        val valueType: VhalValueType,
        val unit: String? = null,
    )

    private val PROPERTY_DEFINITIONS = mapOf(
        10000 to def("Номер профиля автомобиля", VhalValueType.INT),
        10001 to def("Скорость автомобиля", VhalValueType.INT, "км/ч"),
        10002 to def("Заряд высоковольтной батареи", VhalValueType.INT, "%"),
        10003 to def("Передача", VhalValueType.CHAR),
        10004 to def("Передача трансмиссии", VhalValueType.INT),
        10005 to def("Температура снаружи", VhalValueType.FLOAT, "°C"),
        10006 to def("Температура в салоне", VhalValueType.FLOAT, "°C"),
        10007 to def("Блокировка дверей", VhalValueType.INT),
        10008 to def("Стоп-сигнал", VhalValueType.INT),
        10009 to def("Левый указатель поворота", VhalValueType.INT),
        10010 to def("Правый указатель поворота", VhalValueType.INT),
        10011 to def("Оставшееся топливо", VhalValueType.INT, "%"),
        10012 to def("Оставшееся топливо", VhalValueType.INT, "л"),
        10013 to def("Давление в передней левой шине", VhalValueType.FLOAT, "кПа"),
        10014 to def("Давление в передней правой шине", VhalValueType.FLOAT, "кПа"),
        10015 to def("Давление в задней левой шине", VhalValueType.FLOAT, "кПа"),
        10016 to def("Давление в задней правой шине", VhalValueType.FLOAT, "кПа"),
        10017 to def("Передняя левая дверь", VhalValueType.INT),
        10018 to def("Передняя правая дверь", VhalValueType.INT),
        10019 to def("Задняя левая дверь", VhalValueType.INT),
        10020 to def("Задняя правая дверь", VhalValueType.INT),
        10021 to def("Обороты двигателя", VhalValueType.INT, "об/мин"),
        10022 to def("Оставшийся запас хода", VhalValueType.INT, "км"),
        10023 to def("Цвет левой линии полосы", VhalValueType.INT),
        10024 to def("Тип левой линии полосы", VhalValueType.INT),
        10025 to def("Цвет правой линии полосы", VhalValueType.INT),
        10026 to def("Тип правой линии полосы", VhalValueType.INT),
        10027 to def("Левая слепая зона при движении", VhalValueType.INT),
        10028 to def("Правая слепая зона при движении", VhalValueType.INT),
        10029 to def("Левая слепая зона при парковке", VhalValueType.INT),
        10030 to def("Правая слепая зона при парковке", VhalValueType.INT),
        10031 to def("Передний левый внешний радар", VhalValueType.INT, "см"),
        10032 to def("Передний правый внешний радар", VhalValueType.INT, "см"),
        10033 to def("Задний левый внешний радар", VhalValueType.INT, "см"),
        10034 to def("Задний левый внутренний радар", VhalValueType.INT, "см"),
        10035 to def("Задний правый внутренний радар", VhalValueType.INT, "см"),
        10036 to def("Задний правый внешний радар", VhalValueType.INT, "см"),
        10037 to def("Событие кнопки", VhalValueType.STRING),
        10038 to def("Ближний свет", VhalValueType.INT),
        10039 to def("Дальний свет", VhalValueType.INT),
        10040 to def("Стояночный тормоз", VhalValueType.INT),
        10041 to def("Габаритные огни", VhalValueType.INT),
        10042 to def("Задний противотуманный фонарь", VhalValueType.INT),
        10043 to def("Запрос режима питания", VhalValueType.STRING),
        10044 to def("Указание поворота", VhalValueType.INT),
        10045 to def("Левое зеркало", VhalValueType.INT),
        10046 to def("Правое зеркало", VhalValueType.INT),
        10047 to def("Переднее пассажирское сиденье", VhalValueType.INT),
        10049 to def("Ограничение скорости", VhalValueType.INT, "км/ч"),
        10050 to def("Зарядный разъём", VhalValueType.INT),
        10051 to def("Состояние зарядки", VhalValueType.INT),
        10052 to def("Режим VSTD", VhalValueType.INT),
        10053 to def("Состояние тревоги", VhalValueType.INT),
        10054 to def("Нажатие педали акселератора", VhalValueType.FLOAT, "%"),
        10055 to def("Нажатие педали тормоза", VhalValueType.INT, "%"),
        10056 to def("Ремень безопасности водителя", VhalValueType.INT),
        10057 to def("Тип контроллера автоматического вождения", VhalValueType.INT),
        10058 to def("Угол рулевого колеса", VhalValueType.INT, "°"),
        10059 to def("Признак запаса хода на электротяге", VhalValueType.INT),
        10060 to def("Запас хода высоковольтной батареи", VhalValueType.INT, "км"),
        10061 to def("Средний расход топлива", VhalValueType.FLOAT, "л/100 км"),
        10062 to def("Средний расход электроэнергии", VhalValueType.FLOAT, "кВт·ч/100 км"),
        30001 to def("Кондиционер", VhalValueType.BOOLEAN),
        30002 to def("Переднее левое стекло", VhalValueType.INT),
        30003 to def("Переднее правое стекло", VhalValueType.INT),
        30004 to def("Заднее левое стекло", VhalValueType.INT),
        30005 to def("Заднее правое стекло", VhalValueType.INT),
        30006 to def("Передняя левая лампа для чтения", VhalValueType.INT),
        30007 to def("Передняя правая лампа для чтения", VhalValueType.INT),
        30008 to def("Задняя левая лампа для чтения", VhalValueType.INT),
        30009 to def("Задняя правая лампа для чтения", VhalValueType.INT),
        30010 to def("Состояние приборной панели", VhalValueType.INT),
        30011 to def("Багажник", VhalValueType.INT),
    )

    private fun valueLabels(propertyId: Int): Map<Long, String> = when (propertyId) {
        10007 -> mapOf(0L to "Разблокированы", 1L to "Заблокированы")
        10008, 10009, 10010, 10038, 10039, 10041, 10042 -> OFF_ON
        10017, 10018, 10019, 10020 -> mapOf(0L to "Закрыта", 1L to "Открыта")
        10023, 10025 -> mapOf(0L to "Выключено", 1L to "Обычная", 2L to "Распознана системой", 3L to "Пересекается")
        10024, 10026 -> mapOf(0L to "Выключено", 1L to "Сплошная", 2L to "Прерывистая")
        10027, 10028, 10029, 10030 -> mapOf(0L to "Нет", 1L to "Слабое предупреждение", 3L to "Сильное предупреждение")
        10040 -> mapOf(0L to "Выключен", 1L to "Включён")
        10044 -> mapOf(0L to "Выключено", 1L to "Налево", 2L to "Направо", 3L to "Аварийная сигнализация")
        10045, 10046 -> mapOf(0L to "Разложено", 1L to "Сложено")
        10047 -> mapOf(0L to "Свободно", 1L to "Занято")
        10050 -> mapOf(1L to "Подключён", 2L to "Отключён")
        10051 -> mapOf(1L to "Заряжается", 2L to "Остановлена")
        10056 -> mapOf(1L to "Не пристёгнут", 2L to "Пристёгнут")
        30001 -> mapOf(0L to "Выключен", 1L to "Включён")
        30006, 30007, 30008, 30009 -> OFF_ON
        30011 -> mapOf(0L to "Закрыт", 1L to "Открыт")
        else -> emptyMap()
    }

    private fun def(title: String, type: VhalValueType, unit: String? = null) =
        VhalPropertyDefinition(title, type, unit)

    private fun Any.jsonValue(): Any = if (this == JSONObject.NULL) "" else this

    private val OFF_ON = mapOf(0L to "Выключено", 1L to "Включено")
    private val WINDOW_PROPERTY_IDS = 30002..30005
    private val EXPRESSION = Regex("""x\s*([/*+\-])\s*(-?\d+(?:\.\d+)?)""")

    private const val COMPRESSED_READ_PROFILES =
        "H4sICDPrhmoCA3ZoYWwtcmVhZC1wcm9maWxlcy5qc29uAO2dW3PaSBqG7/dXTHGdrVWfdMgdAWyzkTEj4TiprSmKGNmm1gZKyJ6k" +
        "pvLfpwUzO6nN15zUGlrl94bGcfzx0VIf30dv/9Z6/5GxMefjOL3ott7+57fWMl8ss7z42p+23jLP88SbVp5Npunsfj55LP9RqSAM" +
        "RKjY978YTJ6y1tvWeTbJ45e8P5/ezlub34/yyXx1t8ifWm9/a62KbLlaf0zxdVn+wdNkuZzN7/X/1e/K/+HpfxzqH5kuE11yXQ50" +
        "KXTZbX1705pmd5Pnx0L/+M/Wt1++fXvzY8rSkHLoESkPizLp9m3RokIpMhSPfEmEaj99HiWTX8viw+SRjOeb4oVEvM7TsngqRmf5" +
        "vPjuLRk3pL+yJ6iv3PtS5HFxnxartFgs4xkZMTJFZFsjjp7zub78eZxRUZlnisr3ipqQubKArtVQUlepu1jk3fwl12GPu0X1Lel9" +
        "o289FhoyUZ4hk+Fktaolk8hUJ4EhkzhLsjoy4Z6pToQhk2RWNZPvuglmyIr/kJXPPM4iTrXE5aRYTYtsNp/ObidFNqXuQk73lEz4" +
        "1LVvr77Gs3kWZ53F4yIn40lTPLYt3ujrkoymTNG4OVoyM2fnm+KJbfEM2YkfWjAPIxkoL6SuxsXN+H3v07g/GF6PfogmdN/F6NwU" +
        "p/qDs8lt1p/Ok2x1pptjvrj7lK0GCzIuN/TeiqrDm9l8uFjN9Y3cLsr+5rjbWY+DbD0KsvUYqD9Irl/V+tXXeb1pBevXsPVWf71o" +
        "/cq8TaH/Wl93xjeF/nt91ZjcFGpT6BC68lmwKXQQXeMsWhfc2xQ6iu5NON8UYlPI8sLpUv1R+mW5q+EJ44RCV6LYUYllV4lK3FSi" +
        "NFWi3FGJie4iUImbSjRN7pTaWYl6IvIaK/GXN38sGCS5VqD7XRkE1IiV9j9kD+lyWi4WyAHVo4doqZggh+js/uvDy+dJUawWt2Q8" +
        "uueRgpx8uryUkYJcLxy8lImiwFPk1PS4pcwmHq+2lAnoq84iemJ2m80fF7f/XWVFUdb+AYsjGfqWF0cyDGpYHElyErT34ojR9Sk5" +
        "J+vz7jl7zLOnyWyuo5DtiNHtUtGLuDLi48vjrGzl63cvk8e7fPF091x8ftyrYWVflnm2Ws0WZTv87oe3rS8//eun8nIYWg8Thq/u" +
        "KXqOP8uzdfTnPNMpzovH7K6wkqIpQWlK0N8vwXx2/1BvhsqUYbAzQ/3rvPYa9E35hXvlV38F0nsTgpM9+An2JgQPlBt7E7pOIjf2" +
        "JnSd+KfcmyDnUer/K+7PoTW+Tke9ZDy8uuklZ/HVzbg3OE+H3W5fv1i4tXnroB0UKSNueQdFeszqDor0uMUdFOkJqzso0pNH7KBw" +
        "sp+RESMny3/eM52L/mVvHHcu2/1BtzOIe2To0BRa7h866ZOhI1NotTt0bzTqxB/I7STPFNbfGTbpm8OS7VKGTFLtsj1sj6+uR910" +
        "kCbddHR1dpYMRnHvXfyekcG5ITi5pCeDJ31jcGHKnO0TPOltyVuaQgtD6P4g3TO0MoVW+4XeUiG+KXSwX4VsCW1vL7OMZmh8inlb" +
        "buWbdjLoD87HvY+jJB6dp6M0vnrXa1+Sn2BqgyE76BMu+qZPkB49nvnkgkF3GePe8F3cvhwmvZ//enf0Ipytl+CmKYBkhq8fRAd9" +
        "/eFVGvfLVkjWANm4lWKcupMvrr+Lm/TOrs7JmFSbjvxIMnJ0G24mCOPLq67u5Ho/X/dSOlVTe/Z9Y6PrdhKdKRmNbMJKBmTHo7/4" +
        "ZT9Jzq5i3crS9qibfEiO3mfz1ntrzHTZfVNifI/Ehu00rSuxgL5VPHLBoBMrc0l77VF5BY6dlbLvZ6UevV3KDLNSn1TMylY8Sqrk" +
        "ZGywpv6KBzsHdz0jjvuXZ/0kJYUjYgemnNKKwKd3HOeLYnY3nT2tHha/Psym2UFiFDlpqCxGCUEOMxCjDhGjdCVGEKMqilFCRB7E" +
        "qIpilK5EBjFq70o0DeqhoMfOpNfWA4IepdZDQnJ1o1fAVscrQQoafyQl90xKr52tJxWaklI7kkp7natBt6aqikxZ+ftmZbuu9IRD" +
        "CcPEh8TLyolPO04uy3niuqCmxUpS4pkMOLlt/ecc5kPvQs/eLwfno/P4Xdo+67F2p5MMe904adMfo0zLDkN9vkver8PpeMNe0iFj" +
        "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"
}

private fun normalizeMapKey(value: Any): String = when (value) {
    is Number -> formatNumber(value.toDouble())
    else -> value.toString()
}

private fun withUnit(value: String, unit: String?): String = if (unit == null) value else "$value $unit"

private fun formatNumber(value: Double): String = when {
    !value.isFinite() -> value.toString()
    else -> BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
