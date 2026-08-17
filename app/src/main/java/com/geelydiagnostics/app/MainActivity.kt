package com.geelydiagnostics.app

import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_REASONABLE_EPOCH_MILLIS = 946684800000L
private const val MAX_REASONABLE_EPOCH_MILLIS = 4102444800000L
private const val MIN_HEAD_UNIT_FONT_SCALE = 1.5f

class MainActivity : ComponentActivity(), ReadOnlySink {

    private var uiState by mutableStateOf(AppUiState())
    private var client: Closeable? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeelyDiagnosticsApp(
                state = uiState,
                onRefresh = ::startReadOnlyScan,
                onClearLog = { uiState = uiState.copy(logLines = emptyList()) },
            )
        }
        startReadOnlyScan()
    }

    private fun startReadOnlyScan() {
        client?.close()
        client = null
        uiState = AppUiState(
            carStatus = ReadStatus.CHECKING,
            diagnosticsStatus = ReadStatus.CHECKING,
            dtcManagerStatus = ReadStatus.CHECKING,
            sensorStatus = ReadStatus.CHECKING,
            carInfoStatus = ReadStatus.CHECKING,
            functionStatus = ReadStatus.CHECKING,
            logLines = uiState.logLines,
        )
        onLog("=== New ECARX read-only scan ===")
        try {
            client = EcarxReadOnlyClient(applicationContext, this).also { it.start() }
        } catch (error: Throwable) {
            onCarStatus(ReadStatus.ERROR, describe(error))
            onDiagnosticsStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onDtcManagerStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onSensorStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onCarInfoStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onFunctionStatus(ReadStatus.ERROR, "ECARX API unavailable")
            onLog("Initialization failed: ${describe(error)}", error)
        }
    }

    override fun onDestroy() {
        client?.close()
        client = null
        super.onDestroy()
    }

    override fun onCarStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(carStatus = status, carDetail = detail)
    }

    override fun onDiagnosticsStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(diagnosticsStatus = status, diagnosticsDetail = detail)
    }

    override fun onDtcManagerStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(dtcManagerStatus = status, dtcManagerDetail = detail)
    }

    override fun onSensorStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(sensorStatus = status, sensorDetail = detail)
    }

    override fun onCarInfoStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(carInfoStatus = status, carInfoDetail = detail)
    }

    override fun onFunctionStatus(status: ReadStatus, detail: String) = onMain {
        uiState = uiState.copy(functionStatus = status, functionDetail = detail)
    }

    override fun onDtcsChanged(dtcs: List<DtcRecord>) = onMain {
        uiState = uiState.copy(dtcs = dtcs)
    }

    override fun onSensorsChanged(sensors: List<SensorRecord>) = onMain {
        uiState = uiState.copy(sensors = sensors)
    }

    override fun onSensorValueChanged(id: Int, value: String) = onMain {
        uiState = uiState.copy(
            sensors = uiState.sensors.map { sensor ->
                if (sensor.id == id) sensor.copy(value = value) else sensor
            },
        )
    }

    override fun onSensorSupportChanged(id: Int, support: ApiSupportStatus) = onMain {
        uiState = uiState.copy(
            sensors = uiState.sensors.map { sensor ->
                if (sensor.id == id) sensor.copy(support = support) else sensor
            },
        )
    }

    override fun onVehicleInfoChanged(items: List<VehicleInfoRecord>) = onMain {
        uiState = uiState.copy(vehicleInfo = items)
    }

    override fun onFunctionsChanged(functions: List<VehicleFunctionRecord>) = onMain {
        uiState = uiState.copy(functions = functions)
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
        private const val LOG_TAG = "GeelyDiagnostics"
    }
}

internal enum class AppTab(val title: String) {
    DIAGNOSTICS("Диагностика"),
    SENSORS("Сенсоры"),
    VEHICLE("Автомобиль"),
    FUNCTIONS("Функции"),
}

@Composable
internal fun GeelyDiagnosticsApp(
    state: AppUiState,
    onRefresh: () -> Unit,
    onClearLog: () -> Unit,
    initialTab: AppTab = AppTab.DIAGNOSTICS,
) {
    val systemDensity = LocalDensity.current
    val readableDensity = Density(
        density = systemDensity.density,
        fontScale = maxOf(systemDensity.fontScale, MIN_HEAD_UNIT_FONT_SCALE),
    )
    CompositionLocalProvider(LocalDensity provides readableDensity) {
        GeelyDiagnosticsTheme {
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                ) {
                    AppHeader(
                        onRefresh = onRefresh,
                        onClearLog = onClearLog,
                    )
                    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                        AppTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                },
                            )
                        }
                    }
                    when (AppTab.entries[selectedTabIndex]) {
                        AppTab.DIAGNOSTICS -> DiagnosticsScreen(state)
                        AppTab.SENSORS -> SensorsScreen(state)
                        AppTab.VEHICLE -> VehicleScreen(state)
                        AppTab.FUNCTIONS -> FunctionsScreen(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeelyDiagnosticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
private fun AppHeader(onRefresh: () -> Unit, onClearLog: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Geely Diagnostics",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ECARX AdaptAPI · строго только чтение",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
        }
        Button(onClick = onRefresh) {
            Text("Обновить", fontSize = 17.sp)
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
private fun DiagnosticsScreen(state: AppUiState) {
    val ecuCount = state.dtcs.map(DtcRecord::ecuType).distinct().size
    val dtcCodeCount = state.dtcs.count { it.code.isNotBlank() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Подключение",
                    description = "Вход в ECARX API",
                    status = state.carStatus,
                    detail = state.carDetail,
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Диагностика",
                    description = "Доступ к сервису",
                    status = state.diagnosticsStatus,
                    detail = state.diagnosticsDetail,
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "DTC",
                    description = "Read-only чтение кодов",
                    status = state.dtcManagerStatus,
                    detail = state.dtcManagerDetail,
                )
            }
        }
        item { SectionTitle("Блоки: $ecuCount · Коды ошибок: $dtcCodeCount") }
        if (state.dtcs.isEmpty()) {
            item { EmptyMessage("Данные по блокам пока не получены.") }
        } else {
            item { DtcTable(state.dtcs) }
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("Журнал")
        }
        item { LogPanel(lines = state.logLines) }
    }
}

@Composable
private fun SensorsScreen(state: AppUiState) {
    val supported = state.sensors.filter { it.support.isSupported }.sortedBy(SensorRecord::title)
    CatalogScreen(
        status = state.sensorStatus,
        detail = state.sensorDetail,
        title = "Live Data",
        subtitle = "Показаны только сенсоры, поддержку которых подтвердила текущая ГУ. Значения raw, без неподтверждённых единиц измерения.",
        totalCount = state.sensors.size,
        supportedCount = supported.size,
        emptyText = "Поддерживаемые сенсоры пока не найдены.",
        rows = supported.chunked(2),
    ) { row ->
        TwoColumnRow(row) { sensor -> SensorCard(sensor) }
    }
}

@Composable
private fun VehicleScreen(state: AppUiState) {
    val supported = state.vehicleInfo.filter { it.support.isSupported }.sortedBy(VehicleInfoRecord::title)
    CatalogScreen(
        status = state.carInfoStatus,
        detail = state.carInfoDetail,
        title = "Автомобиль и комплектация",
        subtitle = "Данные читаются из ICarInfo самой машины; неподдерживаемые поля скрыты.",
        totalCount = state.vehicleInfo.size,
        supportedCount = supported.size,
        emptyText = "Поддерживаемые сведения об автомобиле пока не найдены.",
        rows = supported.chunked(2),
    ) { row ->
        TwoColumnRow(row) { item -> VehicleInfoCard(item) }
    }
}

@Composable
private fun FunctionsScreen(state: AppUiState) {
    val supported = state.functions.filter { it.support.isSupported }.sortedBy(VehicleFunctionRecord::title)
    CatalogScreen(
        status = state.functionStatus,
        detail = state.functionDetail,
        title = "Поддерживаемые функции",
        subtitle = "Только проверка поддержки и чтение текущих raw-значений. Управление функциями в приложении отсутствует.",
        totalCount = state.functions.size,
        supportedCount = supported.size,
        emptyText = "Поддерживаемые функции пока не найдены.",
        rows = supported.chunked(2),
    ) { row ->
        TwoColumnRow(row) { function -> FunctionCard(function) }
    }
}

@Composable
private fun <T> CatalogScreen(
    status: ReadStatus,
    detail: String,
    title: String,
    subtitle: String,
    totalCount: Int,
    supportedCount: Int,
    emptyText: String,
    rows: List<List<T>>,
    rowContent: @Composable (List<T>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StatusCard(
                modifier = Modifier.fillMaxWidth(),
                title = title,
                description = subtitle,
                status = status,
                detail = detail,
            )
        }
        item { SectionTitle("Поддерживается: $supportedCount · Проверено: $totalCount") }
        if (rows.isEmpty()) {
            item { EmptyMessage(emptyText) }
        } else {
            items(rows) { row -> rowContent(row) }
        }
    }
}

@Composable
private fun <T> TwoColumnRow(items: List<T>, content: @Composable (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) { content(item) }
        }
        if (items.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SensorCard(sensor: SensorRecord) {
    DataCard(
        title = sensor.title,
        apiName = sensor.apiName,
        id = sensor.id,
        support = sensor.support,
    ) {
        ValueLine("Значение", sensor.value)
        ValueLine("Тип", sensor.valueKind)
        if (sensor.error.isNotBlank()) ValueLine("Ошибка", sensor.error)
    }
}

@Composable
private fun VehicleInfoCard(item: VehicleInfoRecord) {
    DataCard(
        title = item.title,
        apiName = item.apiName,
        id = item.id,
        support = item.support,
    ) {
        ValueLine("Значение", item.value)
        if (item.error.isNotBlank()) ValueLine("Ошибка", item.error)
    }
}

@Composable
private fun FunctionCard(function: VehicleFunctionRecord) {
    DataCard(
        title = function.title,
        apiName = function.apiName,
        id = function.id,
        support = function.support,
    ) {
        ValueLine("Текущее raw", function.value.ifBlank { "не получено" })
        if (function.supportedValues.isNotBlank()) {
            ValueLine("Допустимые raw", function.supportedValues)
        }
        if (function.zones.isNotBlank()) ValueLine("Зоны raw", function.zones)
        if (function.error.isNotBlank()) ValueLine("Примечание", function.error)
    }
}

@Composable
private fun DataCard(
    title: String,
    apiName: String,
    id: Int,
    support: ApiSupportStatus,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = support.label,
                    color = support.color(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "$apiName · ID $id",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ValueLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(132.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(text = value, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier,
    title: String,
    description: String,
    status: ReadStatus,
    detail: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = status.label,
                color = status.color(),
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = detail.displayText, fontSize = 13.sp, maxLines = 3)
            }
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ECU type $ecuType",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (codeCount == 0) "Коды не переданы" else "Кодов ошибок: $codeCount",
                    color = if (codeCount == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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

@Composable
private fun DtcRecordRow(
    code: String,
    id: String,
    status: String,
    time: String,
    isHeader: Boolean = false,
) {
    val color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
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

private fun formatTickTime(tickTime: Long): String {
    if (tickTime !in MIN_REASONABLE_EPOCH_MILLIS..MAX_REASONABLE_EPOCH_MILLIS) {
        return "$tickTime (raw)"
    }
    return SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS", Locale.getDefault()).format(Date(tickTime))
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
private fun EmptyMessage(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

private val ReadStatus.label: String
    get() = when (this) {
        ReadStatus.NOT_CHECKED -> "НЕ ПРОВЕРЕНО"
        ReadStatus.CHECKING -> "ПРОВЕРКА"
        ReadStatus.AVAILABLE -> "ДОСТУПЕН"
        ReadStatus.ERROR -> "ОШИБКА"
    }

@Composable
private fun ReadStatus.color(): Color = when (this) {
    ReadStatus.NOT_CHECKED -> MaterialTheme.colorScheme.onSurfaceVariant
    ReadStatus.CHECKING -> MaterialTheme.colorScheme.tertiary
    ReadStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
    ReadStatus.ERROR -> MaterialTheme.colorScheme.error
}

private val ApiSupportStatus.label: String
    get() = when (this) {
        ApiSupportStatus.ACTIVE -> "ACTIVE"
        ApiSupportStatus.NOT_ACTIVE -> "NOT ACTIVE"
        ApiSupportStatus.NOT_AVAILABLE -> "N/A"
        ApiSupportStatus.ERROR -> "ERROR"
        ApiSupportStatus.UNKNOWN -> "UNKNOWN"
    }

@Composable
private fun ApiSupportStatus.color(): Color = when (this) {
    ApiSupportStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    ApiSupportStatus.NOT_ACTIVE -> MaterialTheme.colorScheme.tertiary
    ApiSupportStatus.NOT_AVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    ApiSupportStatus.ERROR -> MaterialTheme.colorScheme.error
    ApiSupportStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val ApiSupportStatus.isSupported: Boolean
    get() = this == ApiSupportStatus.ACTIVE || this == ApiSupportStatus.NOT_ACTIVE

private val String.displayText: String
    get() = when (this) {
        "CONNECTED" -> "Связь установлена"
        "AVAILABLE" -> "Сервис отвечает"
        "CREATED" -> "Объект автомобиля создан"
        else -> this
    }
