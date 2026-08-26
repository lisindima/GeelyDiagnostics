package com.geelydiagnostics.app.vehicle.mapping

import org.json.JSONArray
import org.json.JSONObject

/** Lossless, parser-compatible description of the read transform; identity has an empty pipeline. */
internal fun ReadTransform.toJson(): JSONObject = JSONObject().put("steps", JSONArray().apply {
    val steps = (this@toJson as? ReadTransform.Pipeline)?.steps.orEmpty()
    steps.forEach { step ->
        put(JSONObject().apply {
            when (step) {
                is ReadTransformStep.Mapping -> {
                    put("type", "mapping")
                    put("map", JSONObject().apply {
                        step.values.forEach { (key, value) -> put(key, value.jsonValue()) }
                    })
                    step.default?.let { put("default", it.jsonValue()) }
                }
                is ReadTransformStep.Arithmetic -> {
                    put("type", "expression")
                    val symbol = when (step.operator) {
                        Operator.DIVIDE -> "/"
                        Operator.MULTIPLY -> "*"
                        Operator.ADD -> "+"
                        Operator.SUBTRACT -> "-"
                    }
                    val operand = java.math.BigDecimal.valueOf(step.operand).stripTrailingZeros().toPlainString()
                    put("expression", "x $symbol $operand")
                }
            }
        })
    }
})

private fun TransformValue.jsonValue(): Any = when (this) {
    is TransformValue.NumberValue -> value
    is TransformValue.StringValue -> value
    is TransformValue.BooleanValue -> value
}
