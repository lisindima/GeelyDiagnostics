package com.cityray.diagnosticsprobe

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

class MainActivity : ComponentActivity(), ProbeSink {

    private var uiState by mutableStateOf(ProbeUiState())
    private var client: Closeable? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProbeTheme {
                ProbeScreen(
                    state = uiState,
                    onRetry = ::startProbe,
                    onClearLog = {
                        uiState = uiState.copy(logLines = emptyList())
                    },
                )
            }
        }

        startProbe()
    }

    private fun startProbe() {
        client?.close()
        client = null
        uiState = uiState.copy(
            carStatus = ProbeStatus.CHECKING,
            carDetail = "",
            diagnosticsStatus = ProbeStatus.CHECKING,
            diagnosticsDetail = "",
            dtcManagerStatus = ProbeStatus.CHECKING,
            dtcManagerDetail = "",
            dtcs = emptyList(),
        )
        onLog("=== New read-only probe run ===")

        try {
            client = EcarxDiagnosticsClient(applicationContext, this).also { it.start() }
        } catch (error: Throwable) {
            // Also catches class verification/linkage errors if ECARX is absent.
            onCarStatus(ProbeStatus.ERROR, describe(error))
            onDiagnosticsStatus(ProbeStatus.ERROR, "ECARX API unavailable")
            onDtcManagerStatus(ProbeStatus.ERROR, "ECARX API unavailable")
            onLog("Probe initialization failed: ${describe(error)}", error)
        }
    }

    override fun onDestroy() {
        client?.close()
        client = null
        super.onDestroy()
    }

    override fun onCarStatus(status: ProbeStatus, detail: String) = onMain {
        uiState = uiState.copy(carStatus = status, carDetail = detail)
    }

    override fun onDiagnosticsStatus(status: ProbeStatus, detail: String) = onMain {
        uiState = uiState.copy(diagnosticsStatus = status, diagnosticsDetail = detail)
    }

    override fun onDtcManagerStatus(status: ProbeStatus, detail: String) = onMain {
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
        private const val LOG_TAG = "CityrayDiagProbe"
    }
}

private val ProbeColors = darkColorScheme(
    primary = Color(0xFF58D68D),
    onPrimary = Color(0xFF062612),
    background = Color(0xFF101418),
    surface = Color(0xFF171D22),
    onBackground = Color(0xFFF0F4F7),
    onSurface = Color(0xFFF0F4F7),
    error = Color(0xFFFF6B6B),
)

@Composable
private fun ProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ProbeColors, content = content)
}

@Composable
private fun ProbeScreen(
    state: ProbeUiState,
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
                SectionTitle("Диагностические записи: ${state.dtcs.size}")
            }

            if (state.dtcs.isEmpty()) {
                item {
                    Text(
                        text = "Записей пока нет. Ноль ошибок и ошибка чтения различаются по статусу DTC Manager и журналу ниже.",
                        color = Color(0xFFB8C2CC),
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
                text = "Cityray Diagnostics Probe",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ECARX AdaptAPI · только чтение",
                color = Color(0xFF9FAAB4),
                fontSize = 16.sp,
            )
        }
        Button(onClick = onRetry) {
            Text("Повторить", fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onClearLog,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34404A)),
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
    status: ProbeStatus,
    detail: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B232A)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color(0xFF8FA0AE), fontSize = 13.sp)
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
                    color = Color(0xFFD4DBE1),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171D22)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (noCodesReturned) {
                Text(
                    text = "ECARX вернул записи без кодов неисправностей. Это не означает, что в автомобиле подтверждено ${dtcs.size} ошибок.",
                    color = Color(0xFFFFD166),
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(12.dp))
            }

            DtcRow("№", "Код DTC", "ID", "ECU", "Статус*", "Время*", isHeader = true)
            HorizontalDivider(color = Color(0xFF34404A))

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
                    HorizontalDivider(color = Color(0xFF252E35))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "* Значения показаны без неподтверждённой расшифровки vendor mapping.",
                color = Color(0xFF8FA0AE),
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
    val color = if (isHeader) Color(0xFF8FA0AE) else Color(0xFFF0F4F7)
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
            if (!isHeader && code == "не передан") Color(0xFFFFD166) else color,
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
            .background(Color(0xFF090C0F), RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        SelectionContainer {
            Text(
                text = if (lines.isEmpty()) "Журнал пуст" else lines.joinToString("\n"),
                color = Color(0xFFB7F7CA),
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

private val ProbeStatus.label: String
    get() = when (this) {
        ProbeStatus.NOT_CHECKED -> "НЕ ПРОВЕРЕНО"
        ProbeStatus.CHECKING -> "ПРОВЕРКА"
        ProbeStatus.AVAILABLE -> "ДОСТУПЕН"
        ProbeStatus.ERROR -> "ОШИБКА"
    }

private val ProbeStatus.color: Color
    get() = when (this) {
        ProbeStatus.NOT_CHECKED -> Color(0xFF8FA0AE)
        ProbeStatus.CHECKING -> Color(0xFFFFD166)
        ProbeStatus.AVAILABLE -> Color(0xFF58D68D)
        ProbeStatus.ERROR -> Color(0xFFFF6B6B)
    }

private val String.displayText: String
    get() = when (this) {
        "CONNECTED" -> "Связь установлена"
        "AVAILABLE" -> "Сервис отвечает"
        else -> this
    }
