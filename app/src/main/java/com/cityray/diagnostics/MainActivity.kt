package com.cityray.diagnostics

import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), DiagnosticsSink {

    private var uiState by mutableStateOf(DiagnosticsUiState())
    private var client: Closeable? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiagnosticsTheme {
                DiagnosticsScreen(
                    state = uiState,
                    onRetry = ::startDiagnostics,
                    onClearLog = {
                        uiState = uiState.copy(logLines = emptyList())
                    },
                )
            }
        }

        startDiagnostics()
    }

    private fun startDiagnostics() {
        client?.close()
        client = null
        uiState = uiState.copy(
            carStatus = DiagnosticsStatus.CHECKING,
            carDetail = "",
            diagnosticsStatus = DiagnosticsStatus.CHECKING,
            diagnosticsDetail = "",
            dtcManagerStatus = DiagnosticsStatus.CHECKING,
            dtcManagerDetail = "",
            dtcs = emptyList(),
        )
        onLog("=== New read-only diagnostics run ===")

        try {
            client = EcarxDiagnosticsClient(applicationContext, this).also { it.start() }
        } catch (error: Throwable) {
            // Also catches class verification/linkage errors if ECARX is absent.
            onCarStatus(DiagnosticsStatus.ERROR, describe(error))
            onDiagnosticsStatus(DiagnosticsStatus.ERROR, "ECARX API unavailable")
            onDtcManagerStatus(DiagnosticsStatus.ERROR, "ECARX API unavailable")
            onLog("Diagnostics initialization failed: ${describe(error)}", error)
        }
    }

    override fun onDestroy() {
        client?.close()
        client = null
        super.onDestroy()
    }

    override fun onCarStatus(status: DiagnosticsStatus, detail: String) = onMain {
        uiState = uiState.copy(carStatus = status, carDetail = detail)
    }

    override fun onDiagnosticsStatus(status: DiagnosticsStatus, detail: String) = onMain {
        uiState = uiState.copy(diagnosticsStatus = status, diagnosticsDetail = detail)
    }

    override fun onDtcManagerStatus(status: DiagnosticsStatus, detail: String) = onMain {
        uiState = uiState.copy(dtcManagerStatus = status, dtcManagerDetail = detail)
    }

    override fun onDtcsChanged(dtcs: List<DtcRecord>) = onMain {
        uiState = uiState.copy(dtcs = dtcs)
    }

    override fun onLog(message: String, error: Throwable?) = onMain {
        if (error == null) Log.i(LOG_TAG, message) else Log.e(LOG_TAG, message, error)
        val timestamp = timeFormat.format(Date())
        uiState = uiState.copy(
            logLines = (uiState.logLines + "$timestamp  $message").takeLast(MAX_LOG_LINES),
        )
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else runOnUiThread(action)
    }

    private fun describe(error: Throwable): String =
        error.javaClass.name + (error.message?.let { ": $it" } ?: "")

    companion object {
        private const val MAX_LOG_LINES = 300
        private const val LOG_TAG = "CityrayDiagnostics"
    }
}

@Composable
private fun DiagnosticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
private fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onRetry: () -> Unit,
    onClearLog: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Header(onRetry = onRetry, onClearLog = onClearLog)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusCard(
                        modifier = Modifier.weight(1f),
                        title = "Подключение к автомобилю",
                        description = "Вход в ECARX API",
                        status = state.carStatus,
                        detail = state.carDetail,
                    )
                    StatusCard(
                        modifier = Modifier.weight(1f),
                        title = "Сервис диагностики",
                        description = "Доступ к диагностике",
                        status = state.diagnosticsStatus,
                        detail = state.diagnosticsDetail,
                    )
                    StatusCard(
                        modifier = Modifier.weight(1f),
                        title = "Коды неисправностей",
                        description = "Read-only чтение DTC",
                        status = state.dtcManagerStatus,
                        detail = state.dtcManagerDetail,
                    )
                }
            }

            item {
                SectionTitle("Диагностические блоки: ${state.dtcs.size}")
            }

            if (state.dtcs.isEmpty()) {
                item {
                    Text(
                        text = "Данные по блокам пока не получены. Результат чтения смотрите по статусу сервиса и журналу ниже.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
                    )
                }
            } else {
                item {
                    DtcTable(state.dtcs)
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("Журнал")
            }

            item {
                LogPanel(lines = state.logLines)
            }
        }
    }
}

@Composable
private fun Header(onRetry: () -> Unit, onClearLog: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Cityray Diagnostics",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ECARX AdaptAPI · только чтение",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
        }
        Button(onClick = onRetry) {
            Text("Повторить", fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onClearLog,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text("Очистить лог", fontSize = 17.sp)
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier,
    title: String,
    description: String,
    status: DiagnosticsStatus,
    detail: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = status.label,
                color = status.color,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail.displayText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun DtcTable(dtcs: List<DtcRecord>) {
    val noCodesReturned = dtcs.all { it.code.isBlank() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (noCodesReturned) {
                Text(
                    text = "Получены данные по ${dtcs.size} блокам, но ни одного DTC-кода не передано. Вероятно, активных кодов ошибок нет; точная семантика vendor API не документирована.",
                    color = Color.Yellow,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(12.dp))
            }

            DtcRow("№", "Код ошибки", "ID блока", "ECU type", "Статус*", "Время*", isHeader = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            dtcs.forEachIndexed { index, dtc ->
                DtcRow(
                    number = (index + 1).toString(),
                    code = dtc.code.ifBlank { "не передан" },
                    id = dtc.id.ifBlank { "—" },
                    ecu = dtc.ecuType.toString(),
                    status = dtc.status.toString(),
                    time = dtc.tickTime.toString(),
                )
                if (index != dtcs.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "* Значения показаны без неподтверждённой расшифровки vendor mapping.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DtcRow(
    number: String,
    code: String,
    id: String,
    ecu: String,
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
        TableCell(number, 0.45f, color, fontWeight, fontSize)
        TableCell(
            code,
            1.5f,
            if (!isHeader && code == "не передан") Color.Yellow else color,
            fontWeight,
            fontSize,
        )
        TableCell(id, 0.7f, color, fontWeight, fontSize)
        TableCell(ecu, 0.7f, color, fontWeight, fontSize)
        TableCell(status, 0.8f, color, fontWeight, fontSize)
        TableCell(time, 1.6f, color, fontWeight, fontSize)
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

@Composable
private fun LogPanel(lines: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        SelectionContainer {
            Text(
                text = if (lines.isEmpty()) "Журнал пуст" else lines.joinToString("\n"),
                color = Color.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

private val DiagnosticsStatus.label: String
    get() = when (this) {
        DiagnosticsStatus.NOT_CHECKED -> "НЕ ПРОВЕРЕНО"
        DiagnosticsStatus.CHECKING -> "ПРОВЕРКА"
        DiagnosticsStatus.AVAILABLE -> "ДОСТУПЕН"
        DiagnosticsStatus.ERROR -> "ОШИБКА"
    }

private val DiagnosticsStatus.color: Color
    get() = when (this) {
        DiagnosticsStatus.NOT_CHECKED -> Color.Gray
        DiagnosticsStatus.CHECKING -> Color.Yellow
        DiagnosticsStatus.AVAILABLE -> Color.Green
        DiagnosticsStatus.ERROR -> Color.Red
    }

private val String.displayText: String
    get() = when (this) {
        "CONNECTED" -> "Связь установлена"
        "AVAILABLE" -> "Сервис отвечает"
        else -> this
    }
