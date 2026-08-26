package com.geelydiagnostics.app.ui.tabs

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.catalog.*
import com.geelydiagnostics.app.ui.components.*
import com.geelydiagnostics.app.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_REASONABLE_EPOCH_MILLIS = 946684800000L
private const val MAX_REASONABLE_EPOCH_MILLIS = 4102444800000L

internal object DiagnosticsTab {

    @Composable
    fun Content(state: AppUiState) {
        val ecuCount = state.dtcs.map(DtcRecord::ecuType).distinct().size
        val dtcCodeCount = state.dtcs.count { it.code.isNotBlank() }
        val diagnosticsStatus = aggregateReadStatus(
            listOf(state.carStatus, state.diagnosticsStatus, state.dtcManagerStatus),
        )
        val diagnosticsDetail = listOfNotNull(
            statusAttentionLine("Подключение", state.carStatus, state.carDetail),
            statusAttentionLine("Сервис", state.diagnosticsStatus, state.diagnosticsDetail),
            statusAttentionLine("DTC", state.dtcManagerStatus, state.dtcManagerDetail),
        ).joinToString(" · ")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = AppSpacing.ScreenTop,
                bottom = AppSpacing.ScreenBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Default),
        ) {
            item {
                StatusCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Коды неисправностей",
                    description = "Штатный сервис DTC · источник ECARX",
                    status = diagnosticsStatus,
                    detail = diagnosticsDetail,
                )
            }
            item {
                CountSummary(
                    title = "Блоки",
                    count = ecuCount,
                    detail = "Кодов ошибок $dtcCodeCount · записей API ${state.dtcs.size}",
                )
            }
            if (state.dtcs.isEmpty()) {
                item { EmptyMessage("Данные по блокам пока не получены.") }
            } else {
                item { DtcTable(state.dtcs) }
            }
            item { PartInfoCard(state.ecarxDiagnosticDetails, state.diagnosticsDetail.takeIf { state.diagnosticsStatus == ReadStatus.ERROR }.orEmpty()) }
            item { DiagnosticApiCard(state.ecarxDiagnosticDetails) }
            item { Obd2OverviewCard(state.obd2, state.vhalDetail.takeIf { state.vhalStatus == ReadStatus.ERROR }.orEmpty()) }
            state.obd2.live?.let { frame -> item("obd2-live") { Obd2FrameCard(frame, freeze = false) } }
            state.obd2.freezeFrames.forEachIndexed { index, frame ->
                item("obd2-freeze-$index") { Obd2FrameCard(frame, freeze = true) }
            }
        }
    }

    @Composable
    private fun DtcTable(dtcs: List<DtcRecord>) {
        val recordsByEcu = dtcs.groupBy(DtcRecord::ecuType).toSortedMap()
        val noCodesReturned = dtcs.all { it.code.isBlank() }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Default),
        ) {
            if (noCodesReturned) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = "Получены данные по ${recordsByEcu.size} блокам, но ни одного DTC-кода не передано. Вероятно, активных кодов ошибок нет; точная семантика vendor API не документирована.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = AppType.BodyEmphasis,
                        modifier = Modifier.padding(AppSpacing.CardContent),
                    )
                }
            }
            recordsByEcu.forEach { (ecuType, records) ->
                DtcBlockCard(ecuType = ecuType, records = records)
            }
            Text(
                text = "* Статус показан raw. Время преобразовано из tickTime как Unix milliseconds; нестандартные значения останутся raw.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = AppType.Label,
            )
        }
    }

    @Composable
    private fun DtcBlockCard(ecuType: Int, records: List<DtcRecord>) {
        val codeCount = records.count { it.code.isNotBlank() }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = AppSizes.CardElevation),
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.CardContent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${EcarxEcuNames.name(ecuType)} · ECU $ecuType",
                            modifier = Modifier.weight(1f),
                            fontSize = AppType.CardTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            color = if (codeCount == 0) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (codeCount == 0) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = if (codeCount == 0) {
                                    "Коды не переданы"
                                } else {
                                    "Кодов ошибок: $codeCount"
                                },
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.Small,
                                    vertical = AppSpacing.XSmall,
                                ),
                                fontSize = AppType.Supporting,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Column(
                    Modifier.padding(
                        horizontal = AppSpacing.CardContent,
                        vertical = AppSpacing.Medium,
                    ),
                ) {
                    DtcRecordRow("Код ошибки", "DTC ID", "Статус*", "Время ГУ*", true)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    records.forEachIndexed { index, record ->
                        DtcRecordRow(
                            code = record.code.ifBlank { "не передан" },
                            id = record.id.ifBlank { "—" },
                            status = record.status.toString(),
                            time = formatTickTime(record.tickTime),
                        )
                        if (index != records.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DtcRecordRow(
        code: String,
        id: String,
        status: String,
        time: String,
        isHeader: Boolean = false,
    ) {
        val color = if (isHeader) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
        val fontSize = if (isHeader) AppType.Supporting else AppType.BodyEmphasis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCell(
                code,
                1.45f,
                if (!isHeader && code == "не передан") MaterialTheme.colorScheme.tertiary else color,
                fontWeight,
                fontSize,
            )
            TableCell(id, 0.75f, color, fontWeight, fontSize)
            TableCell(status, 0.75f, color, fontWeight, fontSize)
            TableCell(time, 1.55f, color, fontWeight, fontSize)
        }
    }

    @Composable
    private fun RowScope.TableCell(
        value: String,
        cellWeight: Float,
        color: Color,
        fontWeight: FontWeight,
        fontSize: TextUnit,
    ) {
        SelectionContainer(modifier = Modifier.weight(cellWeight)) {
            Text(
                text = value,
                color = color,
                fontWeight = fontWeight,
                fontSize = fontSize,
                maxLines = 1,
            )
        }
    }

    private fun formatTickTime(tickTime: Long): String {
        if (tickTime !in MIN_REASONABLE_EPOCH_MILLIS..MAX_REASONABLE_EPOCH_MILLIS) {
            return "$tickTime (raw)"
        }
        return SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(tickTime))
    }

    private fun statusAttentionLine(
        label: String,
        status: ReadStatus,
        detail: String,
    ): String? = statusAttentionDetail(status, detail)
        .ifBlank { null }
        ?.let { "$label: $it" }
}
