package com.geelydiagnostics.app.ui.catalog

import com.geelydiagnostics.app.model.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
internal fun rememberCurrentTimeMillis(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1_000L)
        value = System.currentTimeMillis()
    }
}

internal fun formatUpdateTime(updatedAtMillis: Long?, nowMillis: Long): String {
    if (updatedAtMillis == null) return "нет данных"
    val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(updatedAtMillis))
    val ageSeconds = max(0L, nowMillis - updatedAtMillis) / 1_000L
    val age = when {
        ageSeconds < 2 -> "только что"
        ageSeconds < 60 -> "$ageSeconds с назад"
        ageSeconds < 3_600 -> "${ageSeconds / 60} мин назад"
        else -> "${ageSeconds / 3_600} ч назад"
    }
    return "$clock · $age"
}

internal fun VehicleParameter.isStale(nowMillis: Long): Boolean {
    if (!autoUpdates) return false
    val updatedAt = updatedAtMillis ?: return expectedUpdateIntervalMillis != null
    val limit = expectedUpdateIntervalMillis ?: return false
    return nowMillis - updatedAt > limit
}
