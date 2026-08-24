package com.geelydiagnostics.app.export

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.catalog.matchesFavorite

import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
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
    ): String = JSONObject().apply {
        put("schemaVersion", 5)
        put("application", "Geely Diagnostics")
        put("appVersion", appVersion)
        put("generatedAt", isoTime(generatedAtMillis))
        put("readOnly", true)
        put("vhalProfile", state.selectedVhalProfile.key)
        put("vhalBackend", state.selectedVhalBackend.name)
        put("scanStartedAt", state.scanStartedAtMillis.jsonTime())
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
                    put("statusRaw", record.status)
                    put("tickTimeRaw", record.tickTime)
                })
            }
        })
        put("parameters", JSONArray().apply {
            state.parameters.forEach { record ->
                put(JSONObject().apply {
                    put("normalizedPropertyId", record.propertyId?.rawValue ?: JSONObject.NULL)
                    put("title", record.title)
                    put("display", record.value.display)
                    put("raw", record.value.raw)
                    put("status", record.status.name)
                    put("primarySource", record.primaryReading.source.name)
                    put("valueKind", record.valueKind)
                    put("areaId", record.areaId)
                    put("sourceTimestampNanos", record.sourceTimestampNanos ?: JSONObject.NULL)
                    put("updatedAt", record.updatedAtMillis.jsonTime())
                    put("changedSinceScan", record.changedSinceScan)
                    put("autoUpdates", record.autoUpdates)
                    put("decoded", record.decoded)
                    put("favorite", record.matchesFavorite(state.favoriteKeys))
                    put("error", record.error)
                    put("sources", JSONArray().apply {
                        record.sourceReadings.forEach { reading ->
                            put(JSONObject().apply {
                                putCommonValue(
                                    reading.signalId,
                                    reading.signalName,
                                    record.title,
                                    reading.value,
                                    reading.source,
                                )
                                put("status", reading.status.name)
                                put("profile", reading.profile ?: JSONObject.NULL)
                                put("areaId", reading.areaId)
                                put("updatedAt", reading.updatedAtMillis.jsonTime())
                                put("sourceTimestampNanos", reading.sourceTimestampNanos ?: JSONObject.NULL)
                                put("autoUpdates", reading.autoUpdates)
                                put("decoded", reading.decoded)
                                put("error", reading.error)
                            })
                        }
                    })
                })
            }
        })
        put("vehicleInfo", JSONArray().apply {
            state.vehicleInfo.forEach { record ->
                put(JSONObject().apply {
                    putCommonValue(record.id, record.apiName, record.title, record.value, record.source)
                    put("support", record.support.name)
                    put("updatedAt", record.updatedAtMillis.jsonTime())
                    put("favorite", record.favoriteKey in state.favoriteKeys)
                    put("error", record.error)
                })
            }
        })
        put("functions", JSONArray().apply {
            state.functions.forEach { record ->
                put(JSONObject().apply {
                    putCommonValue(record.id, record.apiName, record.title, record.value, record.source)
                    put("support", record.support.name)
                    put("supportedValuesRaw", record.supportedValues)
                    put("zonesRaw", record.zones)
                    put("updatedAt", record.updatedAtMillis.jsonTime())
                    put("favorite", record.favoriteKey in state.favoriteKeys)
                    put("error", record.error)
                })
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

    private fun JSONObject.putCommonValue(
        id: Int,
        apiName: String,
        title: String,
        value: VehicleDisplayValue,
        source: VehiclePropertySource,
    ) {
        put("id", id)
        put("apiName", apiName)
        put("title", title)
        put("source", source.name)
        put("display", value.display)
        put("raw", value.raw)
    }

    private fun Long?.jsonTime(): Any = this?.let(::isoTime) ?: JSONObject.NULL

    private fun isoTime(timestamp: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US,
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timestamp))
}
