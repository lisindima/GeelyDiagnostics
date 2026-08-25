package com.geelydiagnostics.app.vehicle.ecarx

import com.ecarx.xui.adaptapi.FunctionStatus
import com.ecarx.xui.adaptapi.car.ICar
import com.geelydiagnostics.app.model.ApiSupportStatus
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import java.io.Closeable
import java.lang.reflect.Modifier
import java.util.Locale

internal interface EcarxReader : Closeable {
    fun read(car: ICar)
    override fun close() = Unit
}

internal val ApiSupportStatus.isSupported: Boolean
    get() = this == ApiSupportStatus.ACTIVE || this == ApiSupportStatus.NOT_ACTIVE

internal fun FunctionStatus?.toApiSupport(): ApiSupportStatus = when (this) {
    FunctionStatus.active -> ApiSupportStatus.ACTIVE
    FunctionStatus.notactive -> ApiSupportStatus.NOT_ACTIVE
    FunctionStatus.notavailable -> ApiSupportStatus.NOT_AVAILABLE
    FunctionStatus.error -> ApiSupportStatus.ERROR
    null -> ApiSupportStatus.UNKNOWN
}

internal fun ApiSupportStatus.toPropertyStatus(): VehiclePropertyStatus = when (this) {
    ApiSupportStatus.ACTIVE, ApiSupportStatus.NOT_ACTIVE -> VehiclePropertyStatus.AVAILABLE
    ApiSupportStatus.NOT_AVAILABLE, ApiSupportStatus.UNKNOWN -> VehiclePropertyStatus.UNAVAILABLE
    ApiSupportStatus.ERROR -> VehiclePropertyStatus.ERROR
}

internal val ApiSupportStatus.displayLabel: String
    get() = when (this) {
        ApiSupportStatus.ACTIVE -> "Доступна"
        ApiSupportStatus.NOT_ACTIVE -> "Поддерживается · неактивна"
        ApiSupportStatus.NOT_AVAILABLE -> "Не поддерживается"
        ApiSupportStatus.ERROR -> "Ошибка проверки"
        ApiSupportStatus.UNKNOWN -> "Состояние неизвестно"
    }

internal fun VehicleDisplayValue.toCarValue(): CarValue? {
    if (this == VehicleDisplayValue.unavailable) return null
    val number = raw.toDoubleOrNull()
    return when {
        number == null -> CarValue.StringValue(raw)
        raw.contains('.') || raw.contains(',') -> CarValue.FloatValue(number)
        else -> CarValue.IntValue(number.toInt())
    }
}

internal fun VehicleDisplayValue.toRawVehicleValue(): RawVehicleValue? =
    takeUnless { it == VehicleDisplayValue.unavailable }
        ?.let { RawVehicleValue(it.raw, it.raw.toDoubleOrNull()) }

internal fun intConstants(type: Class<*>, prefix: String? = null): List<Pair<String, Int>> =
    type.fields.asSequence()
        .filter { field ->
            Modifier.isStatic(field.modifiers) &&
                field.type == Int::class.javaPrimitiveType &&
                (prefix == null || field.name.startsWith(prefix))
        }
        .map { it.name to it.getInt(null) }
        .sortedBy(Pair<String, Int>::first)
        .toList()

internal fun describe(error: Throwable): String = generateSequence(error) { it.cause }
    .take(5)
    .joinToString(" <- ") { throwable ->
        val message = throwable.message?.takeIf(String::isNotBlank)
        if (message == null) throwable.javaClass.name else "${throwable.javaClass.name}: $message"
    }
    .ifBlank { error.javaClass.name }

internal fun prettyName(value: String, vararg prefixes: String): String {
    var normalized = value
    prefixes.firstOrNull(normalized::startsWith)?.let { normalized = normalized.removePrefix(it) }
    return normalized.lowercase(Locale.US)
        .replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

internal fun formatFloat(value: Float): String =
    if (value.isFinite()) {
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    } else {
        value.toString()
    }
