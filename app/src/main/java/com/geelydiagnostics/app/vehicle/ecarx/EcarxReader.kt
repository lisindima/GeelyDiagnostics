package com.geelydiagnostics.app.vehicle.ecarx

import com.ecarx.xui.adaptapi.FunctionStatus
import com.ecarx.xui.adaptapi.car.ICar
import com.geelydiagnostics.app.ApiSupportStatus
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
