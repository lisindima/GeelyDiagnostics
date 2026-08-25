package com.geelydiagnostics.app.ui.parameters

import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehicleSourceReading
import com.geelydiagnostics.app.vehicle.property.favoriteKey
import com.geelydiagnostics.app.vehicle.property.primaryReading
import java.util.Locale

internal val VehicleParameter.selectionKey: String
    get() = favoriteKey

internal val VehicleParameter.sourceLabels: List<String>
    get() = sourceReadings.map { it.badgeLabel }.distinct()

internal val VehicleParameter.fieldName: String
    get() = primaryReading.signalName.takeUnless {
        it.startsWith("VHAL_0x", ignoreCase = true)
    }.orEmpty()

internal val VehicleParameter.cardIdLabel: String
    get() = when {
        propertyId != null -> "внутренний ID ${propertyId.rawValue}" + areaSuffix
        primaryReading.source == VehiclePropertySource.VHAL ->
            String.format(Locale.US, "0x%08X", primaryReading.signalId) + areaSuffix
        else -> primaryReading.signalId.toString()
    }

internal val VehicleSourceReading.badgeLabel: String
    get() = when {
        source == VehiclePropertySource.VHAL && profile != null -> "VHAL · $profile"
        source == VehiclePropertySource.VHAL -> "VHAL · RAW"
        else -> source.label
    }

internal val VehicleSourceReading.signalLabel: String
    get() = buildString {
        if (source == VehiclePropertySource.VHAL) {
            append(String.format(Locale.US, "0x%08X", signalId))
        } else {
            append(signalId)
        }
        append(" · ")
        append(signalName)
        if (areaId != 0) append(String.format(Locale.US, " · area 0x%08X", areaId))
    }

private val VehicleParameter.areaSuffix: String
    get() = if (areaId == 0) "" else String.format(Locale.US, " · area 0x%08X", areaId)
