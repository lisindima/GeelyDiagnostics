package com.geelydiagnostics.app.ui.diagnostics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.components.CountSummary
import com.geelydiagnostics.app.ui.components.EmptyMessage
import com.geelydiagnostics.app.ui.components.StatusCard
import com.geelydiagnostics.app.ui.components.aggregateReadStatus
import com.geelydiagnostics.app.vehicle.vhal.Obd2Properties
import com.geelydiagnostics.app.vehicle.vhal.formatVhalNumber

internal fun LazyListScope.obd2DiagnosticsItems(state: DiagnosticsUiState) {
    val snapshot = state.obd2
    val status = if (snapshot.capabilities.isNotEmpty()) {
        aggregateReadStatus(snapshot.capabilities.map { it.status })
    } else if (state.vhalStatus == ReadStatus.ERROR) ReadStatus.ERROR else ReadStatus.NOT_CHECKED
    item("obd2-status") {
        StatusCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Диагностика OBD2",
            description = "VHAL · " + when (snapshot.backend) {
                "HIDL" -> "Прямой HIDL"
                "CAR_DIAGNOSTIC_MANAGER" -> "Car API"
                else -> state.selectedVhalBackend.title
            },
            status = status,
            detail = when {
                snapshot.detail.isNotBlank() -> snapshot.detail
                state.vhalStatus == ReadStatus.ERROR -> state.vhalDetail
                status == ReadStatus.ERROR || status == ReadStatus.PARTIAL ->
                    "Часть данных недоступна. Причины указаны в разделе «Доступ к OBD2» ниже."
                else -> ""
            },
        )
    }
    item("obd2-live") {
        if (snapshot.live != null) {
            Obd2FrameCard(snapshot.live, freeze = false, autoUpdates = snapshot.autoUpdates)
        } else {
            DiagnosticSectionCard("Текущий кадр", "Данные не получены", initiallyExpanded = true) {
                DiagnosticNote("Текущий кадр OBD2 пока недоступен. Состояние доступа показано ниже.")
            }
        }
    }
    item("obd2-freeze-title") {
        CountSummary(
            title = "Стоп-кадры",
            count = snapshot.freezeFrames.size,
            detail = "Снимки на момент ошибки · меток времени получено: ${snapshot.freezeTimestamps.size}",
        )
    }
    if (snapshot.freezeFrames.isEmpty()) {
        item("obd2-freeze-empty") {
            EmptyMessage(if (snapshot.capabilities.any {
                it.propertyId == Obd2Properties.INFO && it.status == ReadStatus.AVAILABLE
            } && snapshot.freezeTimestamps.isEmpty()) {
                "Список сохранённых стоп-кадров пуст."
            } else "Стоп-кадры пока не получены.")
        }
    } else snapshot.freezeFrames.forEachIndexed { index, frame ->
        item("obd2-freeze-$index-${frame.requestedTimestampNanos ?: frame.timestampNanos}") {
            Obd2FrameCard(frame, freeze = true)
        }
    }
    item("obd2-access") {
        DiagnosticSectionCard(
            title = "Доступ к OBD2",
            subtitle = "Поддержка свойств, разрешения и подробности чтения",
        ) {
            if (snapshot.capabilities.isEmpty()) DiagnosticNote("Поддержка ещё не проверена")
            snapshot.capabilities.forEach { capability ->
                DiagnosticDetailLine(Obd2Properties.name(capability.propertyId), when {
                    capability.status == ReadStatus.ERROR -> "Ошибка чтения"
                    capability.supported == false -> "Недоступно"
                    capability.status == ReadStatus.AVAILABLE -> "Прочитано"
                    capability.status == ReadStatus.PARTIAL -> "Частично"
                    capability.supported == true -> "Поддерживается"
                    else -> "Не проверено"
                })
                if (capability.detail.isNotBlank()) {
                    DiagnosticNote(capability.detail, isError = capability.status == ReadStatus.ERROR)
                }
            }
            if (snapshot.backend.isNotBlank()) DiagnosticDetailLine("Backend", snapshot.backend)
        }
    }
    item("obd2-note") {
        DiagnosticNote("Кадры OBD2 не объединяются с кодами ECARX. Пустой кадр не подтверждает отсутствие неисправностей.")
    }
}

@Composable
private fun Obd2FrameCard(frame: Obd2Frame, freeze: Boolean, autoUpdates: Boolean = false) {
    val count = frame.integers.size + frame.floats.size
    DiagnosticSectionCard(
        title = if (freeze) {
            "Стоп-кадр${frame.dtc.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}"
        } else "Текущий кадр",
        subtitle = if (freeze) {
            "$count значений · ${frame.requestedTimestampNanos ?: frame.timestampNanos ?: "—"} нс"
        } else {
            "$count значений · ${if (autoUpdates) "по подписке" else "ручное обновление"}"
        },
        initiallyExpanded = !freeze,
    ) {
        if (frame.error.isNotBlank()) DiagnosticNote(frame.error, isError = true)
        if (count == 0 && frame.error.isBlank()) DiagnosticNote("В кадре нет значений датчиков")
        frame.integers.toSortedMap().forEach { (id, value) ->
            DiagnosticDetailLine(obdIntegerNames[id] ?: "OBD int[$id]", value.toString())
        }
        frame.floats.toSortedMap().forEach { (id, value) ->
            DiagnosticDetailLine(obdFloatNames[id] ?: "OBD float[$id]", formatVhalNumber(value))
        }
        DiagnosticNote("Timestamp: ${frame.timestampNanos ?: "—"} нс")
        DiagnosticNote("Значения без дополнительных пересчётов. Индексы и исходные данные сохранены в JSON.")
    }
}

// AOSP Android 11 IntegerSensorIndex / FloatSensorIndex. Unknown/vendor indices remain visible.
private val obdIntegerNames = mapOf(1 to "Индикатор неисправности · int[1]", 4 to "Температура впуска · int[4]",
    7 to "Время работы двигателя · int[7]", 13 to "Температура снаружи · int[13]", 23 to "Температура масла · int[23]")
private val obdFloatNames = mapOf(0 to "Нагрузка двигателя · float[0]", 1 to "Температура охлаждающей жидкости · float[1]",
    8 to "Обороты двигателя · float[8]", 9 to "Скорость · float[9]", 12 to "Положение дросселя · float[12]",
    42 to "Уровень топлива · float[42]")
