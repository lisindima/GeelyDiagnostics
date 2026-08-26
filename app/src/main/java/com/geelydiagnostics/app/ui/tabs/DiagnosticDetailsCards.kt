package com.geelydiagnostics.app.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.theme.*
import com.geelydiagnostics.app.vehicle.vhal.Obd2Properties
import com.geelydiagnostics.app.vehicle.vhal.formatVhalNumber

@Composable
internal fun PartInfoCard(details: EcarxDiagnosticDetails, unavailableReason: String = "") {
    DiagnosticSectionCard("Идентификация ECARX · PartInfo", details.partInfoDetail.ifBlank {
        unavailableReason.ifBlank { "Данные ещё не получены" }
    }) {
        details.parts.forEach { field ->
            DiagnosticDetailLine(field.title, field.value?.takeIf { it.isNotBlank() } ?: "Не передано")
            if (field.error.isNotBlank()) DiagnosticNote(field.error)
        }
    }
}

@Composable
internal fun DiagnosticApiCard(details: EcarxDiagnosticDetails) {
    DiagnosticSectionCard("Возможности диагностического API", "Наличие методов не означает, что их можно вызвать на этой ГУ") {
        if (details.apis.isEmpty()) DiagnosticNote("API ещё не проверены")
        details.apis.forEach { api ->
            DiagnosticDetailLine(api.name, when (api.present) { true -> "Найден"; false -> "Нет"; null -> "Не проверен" })
        }
    }
}

@Composable
internal fun Obd2OverviewCard(snapshot: Obd2Snapshot, unavailableReason: String = "") {
    DiagnosticSectionCard("Диагностика OBD2", snapshot.backend.ifBlank { "VHAL" }, initiallyExpanded = true) {
        DiagnosticNote("Кадры OBD2 показаны отдельно от кодов ECARX. Пустой кадр не означает отсутствие неисправностей.")
        if (snapshot.capabilities.isEmpty()) DiagnosticNote(unavailableReason.ifBlank { "Поддержка ещё не проверена" })
        snapshot.capabilities.forEach { capability ->
            DiagnosticDetailLine(Obd2Properties.name(capability.propertyId), when {
                capability.status == ReadStatus.ERROR -> "Ошибка чтения"
                capability.supported == false -> "Недоступно"
                capability.status == ReadStatus.AVAILABLE -> "Прочитано"
                capability.status == ReadStatus.PARTIAL -> "Частично"
                capability.supported == true -> "Поддерживается"
                else -> "Не проверено"
            })
            if (capability.detail.isNotBlank()) DiagnosticNote(capability.detail)
        }
        if (snapshot.detail.isNotBlank()) DiagnosticNote(snapshot.detail)
        DiagnosticNote(if (snapshot.autoUpdates) "Live frame: обновление по подписке" else "Live frame: ручное обновление")
    }
}

@Composable
internal fun Obd2FrameCard(frame: Obd2Frame, freeze: Boolean) {
    val count = frame.integers.size + frame.floats.size
    DiagnosticSectionCard(
        if (freeze) "Стоп-кадр${frame.dtc.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}" else "Текущий кадр OBD2",
        "$count значений · timestamp ${frame.timestampNanos ?: "—"} нс",
    ) {
        if (frame.error.isNotBlank()) DiagnosticNote(frame.error)
        if (count == 0 && frame.error.isBlank()) DiagnosticNote("В кадре нет значений датчиков")
        frame.integers.toSortedMap().forEach { (id, value) ->
            DiagnosticDetailLine(obdIntegerNames[id] ?: "OBD int[$id]", value.toString())
        }
        frame.floats.toSortedMap().forEach { (id, value) ->
            DiagnosticDetailLine(obdFloatNames[id] ?: "OBD float[$id]", formatVhalNumber(value))
        }
        DiagnosticNote("Показаны значения OBD2 без дополнительных пересчётов; индексы и исходные данные сохранены в JSON.")
    }
}

@Composable
private fun DiagnosticSectionCard(
    title: String, subtitle: String, initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(AppSpacing.CardContent)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
                Text(title, Modifier.fillMaxWidth(), fontSize = AppType.CardTitle, fontWeight = FontWeight.Bold)
                Text(subtitle, Modifier.fillMaxWidth(), fontSize = AppType.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (expanded) "−" else "+", fontSize = AppType.CardTitle)
        }
        if (expanded) SelectionContainer {
            Column(Modifier.padding(start = AppSpacing.CardContent, end = AppSpacing.CardContent, bottom = AppSpacing.CardContent),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Small), content = content)
        }
    }
}

@Composable
private fun DiagnosticDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Default)) {
        Text(label, Modifier.weight(1f), fontSize = AppType.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontSize = AppType.BodyEmphasis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiagnosticNote(text: String) = Text(text, fontSize = AppType.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)

// AOSP Android 11 IntegerSensorIndex / FloatSensorIndex. Unknown/vendor indices remain visible.
private val obdIntegerNames = mapOf(1 to "Индикатор неисправности · int[1]", 4 to "Температура впуска · int[4]",
    7 to "Время работы двигателя · int[7]", 13 to "Температура снаружи · int[13]", 23 to "Температура масла · int[23]")
private val obdFloatNames = mapOf(0 to "Нагрузка двигателя · float[0]", 1 to "Температура охлаждающей жидкости · float[1]",
    8 to "Обороты двигателя · float[8]", 9 to "Скорость · float[9]", 12 to "Положение дросселя · float[12]",
    42 to "Уровень топлива · float[42]")
