package com.geelydiagnostics.app.export

import com.geelydiagnostics.app.model.AppUiState
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.ui.catalog.matchesFavorite
import com.geelydiagnostics.app.ui.display.DisplaySafeAreaState

import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.mapping.toJson
import com.geelydiagnostics.app.vehicle.property.primaryReading
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object DiagnosticsReportExporter {

    fun create(
        state: AppUiState,
        generatedAtMillis: Long,
        appVersion: String,
        displaySafeAreaState: DisplaySafeAreaState = DisplaySafeAreaState(),
    ): String = JSONObject().apply {
        put("schemaVersion", 9)
        put("application", "Geely Diagnostics")
        put("appVersion", appVersion)
        put("generatedAt", isoTime(generatedAtMillis))
        put("readOnly", true)
        put("display", displaySafeAreaState.toJson())
        put("vhalProfile", state.selectedVhalProfile.key)
        put("vhalBackend", state.selectedVhalBackend.name)
        put("vhalDiscovery", JSONObject().apply {
            put("mappedBootstrapReady", state.vhalDiscovery.mappedBootstrapReady)
            put("rawDiscoveryRunning", state.vhalDiscovery.rawDiscoveryRunning)
            put("rawDiscoveryCompleted", state.vhalDiscovery.rawDiscoveryCompleted)
        })
        put("scanStartedAt", state.scanStartedAtMillis.jsonTime())
        put("ecarxDiagnostics", state.ecarxDiagnosticDetails.toJson())
        put("obd2", state.obd2.toJson())
        put("statuses", JSONObject().apply {
            putStatus("ecarx", state.carStatus, state.carDetail)
            putStatus("diagnostics", state.diagnosticsStatus, state.diagnosticsDetail)
            putStatus("dtc", state.dtcManagerStatus, state.dtcManagerDetail)
            putStatus("ecarxParameters", state.ecarxParameterStatus, state.ecarxParameterDetail)
            putStatus("vhal", state.vhalStatus, state.vhalDetail)
            putStatus("vehicleInfo", state.carInfoStatus, state.carInfoDetail)
            putStatus("functions", state.functionStatus, state.functionDetail)
        })
        put("dtcs", JSONArray().apply {
            state.dtcs.forEach { record ->
                put(JSONObject().apply {
                    put("code", record.code)
                    put("id", record.id)
                    put("ecuType", record.ecuType)
                    put("ecuName", com.geelydiagnostics.app.model.EcarxEcuNames.name(record.ecuType))
                    put("statusRaw", record.status)
                    put("tickTimeRaw", record.tickTime)
                })
            }
        })
        put("parameters", JSONArray().apply {
            state.parameters.forEach { record ->
                put(record.toJson(record.matchesFavorite(state.favoriteKeys)))
            }
        })
        put("vehicleInfo", JSONArray().apply {
            state.vehicleInfo.forEach { record ->
                put(record.toJson(record.matchesFavorite(state.favoriteKeys)))
            }
        })
        put("functions", JSONArray().apply {
            state.functions.forEach { record ->
                put(record.toJson(record.matchesFavorite(state.favoriteKeys)))
            }
        })
        put("log", JSONArray(state.logLines))
    }.toString(2)

    private fun JSONObject.putStatus(key: String, status: ReadStatus, detail: String) {
        put(key, JSONObject().apply {
            put("status", status.name)
            put("detail", detail)
        })
    }

    private fun VehicleParameter.toJson(favorite: Boolean) = JSONObject().apply {
        put("section", section.name)
        put("normalizedPropertyId", propertyId?.rawValue ?: JSONObject.NULL)
        put("normalizedPropertyName", if (propertyId != null) title else JSONObject.NULL)
        put("normalizedValue", normalizedValue.jsonValue())
        put("normalizedValueType", normalizedValue.jsonType())
        put("unit", primaryReading.unit ?: JSONObject.NULL)
        put("title", title)
        put("display", value.display)
        put("raw", value.raw)
        put("status", status.name)
        put("primarySource", primaryReading.source.name)
        put("primarySignalId", primaryReading.signalId)
        put("primaryBackend", primaryReading.backend ?: JSONObject.NULL)
        put("valueKind", valueKind)
        put("areaId", areaId)
        put("sourceTimestampNanos", sourceTimestampNanos ?: JSONObject.NULL)
        put("updatedAt", updatedAtMillis.jsonTime())
        put("changedSinceScan", changedSinceScan)
        put("autoUpdates", autoUpdates)
        put("decoded", decoded)
        put("favorite", favorite)
        put("error", error)
        put("sources", JSONArray().apply {
            sourceReadings.forEachIndexed { index, reading ->
                put(JSONObject().apply {
                    put("primary", index == 0)
                    put("normalizedPropertyId", propertyId?.rawValue ?: JSONObject.NULL)
                    put("normalizedValue", reading.normalizedValue.jsonValue())
                    put("normalizedValueType", reading.normalizedValue.jsonType())
                    put("unit", reading.unit ?: JSONObject.NULL)
                    put("backend", reading.backend ?: JSONObject.NULL)
                    put("mappingOrigin", reading.mappingOrigin.name)
                    put("readTransform", reading.readTransform?.toJson() ?: JSONObject.NULL)
                    put("id", reading.signalId)
                    put("apiName", reading.signalName)
                    put("source", reading.source.name)
                    put("display", reading.value.display)
                    put("raw", reading.value.raw)
                    put("status", reading.status.name)
                    put("profile", reading.profile ?: JSONObject.NULL)
                    put("areaId", reading.areaId)
                    put("updatedAt", reading.updatedAtMillis.jsonTime())
                    put("sourceTimestampNanos", reading.sourceTimestampNanos ?: JSONObject.NULL)
                    put("autoUpdates", reading.autoUpdates)
                    put("decoded", reading.decoded)
                    put("mode", reading.modeLabel ?: JSONObject.NULL)
                    put("description", reading.description ?: JSONObject.NULL)
                    put("details", JSONArray().apply {
                        reading.details.forEach { detail ->
                            put(JSONObject().apply {
                                put("label", detail.label)
                                put("value", detail.value)
                            })
                        }
                    })
                    put("error", reading.error)
                })
            }
        })
    }

    private fun Long?.jsonTime(): Any = this?.let(::isoTime) ?: JSONObject.NULL

    private fun CarValue?.jsonValue(): Any = when (this) {
        is CarValue.BooleanValue -> value
        is CarValue.IntValue -> value
        is CarValue.FloatValue -> value.takeIf(Double::isFinite) ?: JSONObject.NULL
        is CarValue.CharValue -> value.toString()
        is CarValue.StringValue -> value
        null -> JSONObject.NULL
    }

    private fun CarValue?.jsonType(): Any = when (this) {
        is CarValue.BooleanValue -> "BOOLEAN"
        is CarValue.IntValue -> "INT"
        is CarValue.FloatValue -> "FLOAT"
        is CarValue.CharValue -> "CHAR"
        is CarValue.StringValue -> "STRING"
        null -> JSONObject.NULL
    }

    private fun isoTime(timestamp: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US,
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timestamp))
}
