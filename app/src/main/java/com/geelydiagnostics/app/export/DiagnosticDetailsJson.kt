package com.geelydiagnostics.app.export

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.vehicle.vhal.Obd2Properties
import org.json.JSONArray
import org.json.JSONObject

internal fun EcarxDiagnosticDetails.toJson() = JSONObject().apply {
    put("partInfoStatus", partInfoStatus.name)
    put("partInfoDetail", partInfoDetail)
    put("parts", JSONArray().apply { parts.forEach { field -> put(JSONObject().apply {
        put("id", field.id); put("key", field.key); put("title", field.title)
        put("raw", field.value ?: JSONObject.NULL); put("error", field.error)
    }) } })
    put("apis", JSONArray().apply { apis.forEach { api -> put(JSONObject().apply {
        put("name", api.name); put("present", api.present ?: JSONObject.NULL)
        put("signatures", JSONArray(api.signatures)); put("detail", api.detail)
    }) } })
}

internal fun Obd2Snapshot.toJson() = JSONObject().apply {
    put("backend", backend)
    put("detail", detail)
    put("autoUpdates", autoUpdates)
    put("capabilities", JSONArray().apply { capabilities.forEach { capability -> put(JSONObject().apply {
        put("propertyId", capability.propertyId); put("name", Obd2Properties.name(capability.propertyId))
        put("supported", capability.supported ?: JSONObject.NULL)
        put("status", capability.status.name); put("detail", capability.detail)
    }) } })
    put("live", live?.toJson() ?: JSONObject.NULL)
    put("freezeTimestampsNanos", JSONArray(freezeTimestamps))
    put("freezeFrames", JSONArray().apply { freezeFrames.forEach { put(it.toJson()) } })
}

private fun Obd2Frame.toJson() = JSONObject().apply {
    put("timestampNanos", timestampNanos ?: JSONObject.NULL)
    put("requestedTimestampNanos", requestedTimestampNanos ?: JSONObject.NULL)
    put("dtc", dtc); put("error", error)
    put("integers", JSONObject().apply { integers.forEach { (id, value) -> put(id.toString(), value) } })
    put("floats", JSONObject().apply { floats.forEach { (id, value) -> put(id.toString(), value.jsonNumber()) } })
    put("rawHidlPayload", raw?.let { payload -> JSONObject().apply {
        put("int32Values", JSONArray(payload.int32Values))
        put("floatValues", JSONArray(payload.floatValues.map { it.jsonNumber() }))
        put("int64Values", JSONArray(payload.int64Values))
        put("bytes", JSONArray(payload.bytes)); put("stringValue", payload.stringValue)
    } } ?: JSONObject.NULL)
}

private fun Double.jsonNumber(): Any = if (isFinite()) this else toString()
