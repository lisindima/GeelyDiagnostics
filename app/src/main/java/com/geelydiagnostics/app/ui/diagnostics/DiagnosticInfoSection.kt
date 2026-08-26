package com.geelydiagnostics.app.ui.diagnostics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import com.geelydiagnostics.app.model.ReadStatus
import com.geelydiagnostics.app.ui.components.StatusCard

internal fun LazyListScope.diagnosticInfoItems(state: DiagnosticsUiState) {
    val details = state.ecarxDiagnosticDetails
    item("info-status") {
        StatusCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Идентификация и API",
            description = "Служебные сведения ECARX · не коды неисправностей",
            status = details.partInfoStatus,
            detail = details.partInfoDetail.ifBlank {
                state.diagnosticsDetail.takeIf { state.diagnosticsStatus == ReadStatus.ERROR }.orEmpty()
            },
        )
    }
    item("info-parts") {
        DiagnosticSectionCard(
            title = "Идентификация · PartInfo",
            subtitle = "Номера сборок и программных модулей",
            initiallyExpanded = true,
        ) {
            if (details.parts.isEmpty()) DiagnosticNote("Идентификационные данные пока не получены")
            details.parts.forEach { field ->
                DiagnosticDetailLine(field.title, field.value?.takeIf(String::isNotBlank) ?: "Не передано")
                if (field.error.isNotBlank()) DiagnosticNote(field.error, isError = true)
            }
        }
    }
    item("info-api") {
        DiagnosticSectionCard(
            title = "Возможности диагностического API",
            subtitle = "Найдено методов: ${details.apis.count { it.present == true }} из ${details.apis.size}",
        ) {
            DiagnosticNote("Наличие метода не означает доступность его вызова на этой ГУ.")
            if (details.apis.isEmpty()) DiagnosticNote("API ещё не проверены")
            details.apis.forEach { api ->
                DiagnosticDetailLine(api.name, when (api.present) {
                    true -> "Найден"
                    false -> "Нет"
                    null -> "Не проверен"
                })
                if (api.detail.isNotBlank()) DiagnosticNote(api.detail)
            }
        }
    }
}
