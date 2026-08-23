package com.geelydiagnostics.app.vehicle.property

import com.geelydiagnostics.app.vehicle.mapping.ReadSignalMapping
import com.geelydiagnostics.app.vehicle.mapping.TransformResult
import com.geelydiagnostics.app.vehicle.mapping.TransformValue
import java.math.BigDecimal

internal class MappedPropertyDecoder(
    private val catalog: CarPropertyCatalog,
) {
    fun decode(
        mapping: ReadSignalMapping?,
        raw: RawVehicleValue,
        sourceSignalId: Int,
        sourceSignalName: String,
        areaId: Int,
        profileKey: String?,
        sourceTimestampNanos: Long?,
        receivedAtMillis: Long,
        autoUpdates: Boolean,
    ): CarPropertySnapshot {
        if (mapping == null) {
            return CarPropertySnapshot(
                propertyId = null,
                value = raw.number?.let { CarValue.FloatValue(it) }
                    ?: CarValue.StringValue(raw.text),
                displayValue = raw.text,
                rawValue = raw,
                status = VehiclePropertyStatus.AVAILABLE,
                source = VehiclePropertySource.VHAL,
                sourceSignalId = sourceSignalId,
                sourceSignalName = sourceSignalName,
                areaId = areaId,
                sourceTimestampNanos = sourceTimestampNanos,
                receivedAtMillis = receivedAtMillis,
                autoUpdates = autoUpdates,
            )
        }

        val definition = catalog.definition(mapping.propertyId)
            ?: return failure(mapping, raw, areaId, profileKey, sourceTimestampNanos, receivedAtMillis,
                autoUpdates, "Свойство ${mapping.propertyId} отсутствует в каталоге")
        return when (val transformed = mapping.transform.apply(raw)) {
            is TransformResult.Failure -> failure(
                mapping,
                raw,
                areaId,
                profileKey,
                sourceTimestampNanos,
                receivedAtMillis,
                autoUpdates,
                "Ошибка преобразования: ${transformed.reason}",
            )
            is TransformResult.Success -> {
                val typed = transformed.value.toCarValue(definition.valueType)
                    ?: return failure(mapping, raw, areaId, profileKey, sourceTimestampNanos,
                        receivedAtMillis, autoUpdates,
                        "Значение не соответствует типу ${definition.valueType}")
                CarPropertySnapshot(
                    propertyId = mapping.propertyId,
                    value = typed,
                    displayValue = format(mapping.propertyId, transformed.value),
                    rawValue = raw,
                    status = VehiclePropertyStatus.AVAILABLE,
                    source = VehiclePropertySource.VHAL,
                    sourceSignalId = mapping.signalId,
                    sourceSignalName = mapping.signalName,
                    areaId = areaId,
                    profileKey = profileKey,
                    sourceTimestampNanos = sourceTimestampNanos,
                    receivedAtMillis = receivedAtMillis,
                    autoUpdates = autoUpdates,
                )
            }
        }
    }

    private fun failure(
        mapping: ReadSignalMapping,
        raw: RawVehicleValue,
        areaId: Int,
        profileKey: String?,
        sourceTimestampNanos: Long?,
        receivedAtMillis: Long,
        autoUpdates: Boolean,
        error: String,
    ) = CarPropertySnapshot(
        propertyId = mapping.propertyId,
        value = null,
        displayValue = raw.text,
        rawValue = raw,
        status = VehiclePropertyStatus.ERROR,
        source = VehiclePropertySource.VHAL,
        sourceSignalId = mapping.signalId,
        sourceSignalName = mapping.signalName,
        areaId = areaId,
        profileKey = profileKey,
        sourceTimestampNanos = sourceTimestampNanos,
        receivedAtMillis = receivedAtMillis,
        autoUpdates = autoUpdates,
        error = error,
    )

    private fun format(id: CarPropertyId, value: TransformValue): String {
        val number = (value as? TransformValue.NumberValue)?.value
        val integer = number?.toLong()?.takeIf { it.toDouble() == number }
        val label = integer?.let { CarPropertyPresentations.valueLabel(id, it) }
        if (label != null) return label
        if (id.rawValue in 30002..30005 && integer != null) return "Уровень $integer/10"
        val plain = when (value) {
            is TransformValue.BooleanValue -> if (value.value) "Включено" else "Выключено"
            is TransformValue.NumberValue -> formatNumber(
                value.value,
                catalog.definition(id)?.decimalPlaces,
            )
            is TransformValue.StringValue -> value.value
        }
        val unit = CarPropertyPresentations.get(id).unit
        return if (unit == null) plain else "$plain $unit"
    }
}

private fun TransformValue.toCarValue(type: CarValueType): CarValue? = when (type) {
    CarValueType.BOOLEAN -> when (this) {
        is TransformValue.BooleanValue -> CarValue.BooleanValue(value)
        is TransformValue.NumberValue -> when (value) {
            0.0 -> CarValue.BooleanValue(false)
            1.0 -> CarValue.BooleanValue(true)
            else -> null
        }
        is TransformValue.StringValue -> when (value.lowercase()) {
            "false", "0" -> CarValue.BooleanValue(false)
            "true", "1" -> CarValue.BooleanValue(true)
            else -> null
        }
    }
    CarValueType.INT -> asNumber()?.let { number ->
        val integer = number.toInt()
        if (integer.toDouble() == number) CarValue.IntValue(integer) else CarValue.FloatValue(number)
    }
    CarValueType.FLOAT -> asNumber()?.let(CarValue::FloatValue)
    CarValueType.STRING -> CarValue.StringValue(asText())
    CarValueType.CHAR -> asText().singleOrNull()?.let(CarValue::CharValue)
}

private fun TransformValue.asNumber(): Double? = when (this) {
    is TransformValue.NumberValue -> value
    is TransformValue.StringValue -> value.toDoubleOrNull()
    is TransformValue.BooleanValue -> null
}

private fun TransformValue.asText(): String = when (this) {
    is TransformValue.BooleanValue -> value.toString()
    is TransformValue.NumberValue -> formatNumber(value)
    is TransformValue.StringValue -> value
}

private fun formatNumber(value: Double): String = if (value.isFinite()) {
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
} else {
    value.toString()
}

private fun formatNumber(value: Double, decimalPlaces: Int?): String {
    if (!value.isFinite() || decimalPlaces == null) return formatNumber(value)
    return BigDecimal.valueOf(value)
        .setScale(decimalPlaces, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
