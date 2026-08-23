package com.geelydiagnostics.app

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val diagnosticsDetail = listOf(
            "подключение: ${state.carDetail.ifBlank { state.carStatus.detailLabel }}",
            "сервис: ${state.diagnosticsDetail.ifBlank { state.diagnosticsStatus.detailLabel }}",
            "DTC: ${state.dtcManagerDetail.ifBlank { state.dtcManagerStatus.detailLabel }}",
        ).joinToString(" · ")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
        }
    }

    @Composable
    private fun DtcTable(dtcs: List<DtcRecord>) {
        val recordsByEcu = dtcs.groupBy(DtcRecord::ecuType).toSortedMap()
        val noCodesReturned = dtcs.all { it.code.isBlank() }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (noCodesReturned) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        text = "Получены данные по ${recordsByEcu.size} блокам, но ни одного DTC-кода не передано. Вероятно, активных кодов ошибок нет; точная семантика vendor API не документирована.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            recordsByEcu.forEach { (ecuType, records) ->
                DtcBlockCard(ecuType = ecuType, records = records)
            }
            Text(
                text = "* Статус показан raw. Время преобразовано из tickTime как Unix milliseconds; нестандартные значения останутся raw.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }

    @Composable
    private fun DtcBlockCard(ecuType: Int, records: List<DtcRecord>) {
        val codeCount = records.count { it.code.isNotBlank() }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Блок $ecuType",
                            modifier = Modifier.weight(1f),
                            fontSize = 18.sp,
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
        val fontSize = if (isHeader) 13.sp else 15.sp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
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

    private val ReadStatus.detailLabel: String
        get() = when (this) {
            ReadStatus.NOT_CHECKED -> "не проверено"
            ReadStatus.CHECKING -> "проверка"
            ReadStatus.PARTIAL -> "частично доступен"
            ReadStatus.AVAILABLE -> "доступен"
            ReadStatus.ERROR -> "ошибка"
        }
}
