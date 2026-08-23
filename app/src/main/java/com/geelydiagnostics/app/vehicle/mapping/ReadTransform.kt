package com.geelydiagnostics.app.vehicle.mapping

import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import org.json.JSONObject

sealed interface TransformValue {
    data class NumberValue(val value: Double) : TransformValue
    data class StringValue(val value: String) : TransformValue
    data class BooleanValue(val value: Boolean) : TransformValue
}

sealed interface TransformResult {
    data class Success(val value: TransformValue) : TransformResult
    data class Failure(val reason: String) : TransformResult
}

sealed interface ReadTransform {
    fun apply(raw: RawVehicleValue): TransformResult

    data object Identity : ReadTransform {
        override fun apply(raw: RawVehicleValue): TransformResult = TransformResult.Success(
            raw.number?.let(TransformValue::NumberValue) ?: TransformValue.StringValue(raw.text),
        )
    }

    data class Pipeline(val steps: List<ReadTransformStep>) : ReadTransform {
        override fun apply(raw: RawVehicleValue): TransformResult {
            var current: TransformValue = raw.number?.let(TransformValue::NumberValue)
                ?: TransformValue.StringValue(raw.text)
            steps.forEachIndexed { index, step ->
                when (val result = step.apply(current)) {
                    is TransformResult.Success -> current = result.value
                    is TransformResult.Failure -> return TransformResult.Failure(
                        "Step ${index + 1}: ${result.reason}",
                    )
                }
            }
            return TransformResult.Success(current)
        }
    }
}

sealed interface ReadTransformStep {
    fun apply(value: TransformValue): TransformResult

    data class Mapping(
        val values: Map<String, TransformValue>,
        val default: TransformValue?,
    ) : ReadTransformStep {
        override fun apply(value: TransformValue): TransformResult {
            val key = value.mappingKey()
            val mapped = values[key] ?: default
                ?: return TransformResult.Failure("No mapping for $key and no default")
            return TransformResult.Success(mapped)
        }
    }

    data class Arithmetic(
        val operator: Operator,
        val operand: Double,
    ) : ReadTransformStep {
        override fun apply(value: TransformValue): TransformResult {
            val number = value.asNumber()
                ?: return TransformResult.Failure("Arithmetic input is not numeric")
            if (operator == Operator.DIVIDE && operand == 0.0) {
                return TransformResult.Failure("Division by zero")
            }
            val transformed = when (operator) {
                Operator.DIVIDE -> number / operand
                Operator.MULTIPLY -> number * operand
                Operator.ADD -> number + operand
                Operator.SUBTRACT -> number - operand
            }
            return TransformResult.Success(TransformValue.NumberValue(transformed))
        }
    }
}

enum class Operator {
    DIVIDE,
    MULTIPLY,
    ADD,
    SUBTRACT,
}

internal object ReadTransformParser {
    private val expression = Regex("""x\s*([/*+\-])\s*(-?\d+(?:\.\d+)?)""")

    fun parse(transform: JSONObject?): ReadTransform {
        val steps = transform?.optJSONArray("steps") ?: return ReadTransform.Identity
        val parsed = (0 until steps.length()).map { index ->
            val step = steps.getJSONObject(index)
            when (val type = step.getString("type")) {
                "mapping" -> parseMapping(step)
                "expression" -> parseExpression(step.getString("expression"))
                else -> throw IllegalArgumentException("Unsupported read transform type: $type")
            }
        }
        return if (parsed.isEmpty()) ReadTransform.Identity else ReadTransform.Pipeline(parsed)
    }

    private fun parseMapping(step: JSONObject): ReadTransformStep.Mapping {
        val map = step.getJSONObject("map")
        val values = map.keys().asSequence().associateWith { key -> map.get(key).toTransformValue() }
        val default = if (step.has("default")) step.get("default").toTransformValue() else null
        return ReadTransformStep.Mapping(values, default)
    }

    private fun parseExpression(value: String): ReadTransformStep.Arithmetic {
        val match = expression.matchEntire(value.trim())
            ?: throw IllegalArgumentException("Unsupported read expression: $value")
        val operator = when (match.groupValues[1]) {
            "/" -> Operator.DIVIDE
            "*" -> Operator.MULTIPLY
            "+" -> Operator.ADD
            "-" -> Operator.SUBTRACT
            else -> error("Unreachable")
        }
        val operand = match.groupValues[2].toDouble()
        require(operator != Operator.DIVIDE || operand != 0.0) { "Division by zero in $value" }
        return ReadTransformStep.Arithmetic(operator, operand)
    }
}

private fun Any.toTransformValue(): TransformValue = when (this) {
    is Boolean -> TransformValue.BooleanValue(this)
    is Number -> TransformValue.NumberValue(toDouble())
    JSONObject.NULL -> TransformValue.StringValue("")
    else -> TransformValue.StringValue(toString())
}

private fun TransformValue.mappingKey(): String = when (this) {
    is TransformValue.BooleanValue -> value.toString()
    is TransformValue.NumberValue -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    is TransformValue.StringValue -> value
}

private fun TransformValue.asNumber(): Double? = when (this) {
    is TransformValue.NumberValue -> value
    is TransformValue.StringValue -> value.toDoubleOrNull()
    is TransformValue.BooleanValue -> null
}
