package com.geelydiagnostics.app

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.property.CarPropertyId
import com.geelydiagnostics.app.vehicle.property.CarPropertySnapshot
import com.geelydiagnostics.app.vehicle.property.CarValue
import com.geelydiagnostics.app.vehicle.property.EcarxNormalizedPropertyRegistry
import com.geelydiagnostics.app.vehicle.property.RawVehicleValue
import com.geelydiagnostics.app.vehicle.property.VehicleDisplayValue
import com.geelydiagnostics.app.vehicle.property.VehicleParameterSample
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource
import com.geelydiagnostics.app.vehicle.property.VehiclePropertyStatus
import com.geelydiagnostics.app.vehicle.repository.UnifiedParameterCache

private const val SAMPLE_TICK_TIME = 1786695380305L
private const val CITYRAY_ATLAS_DEVICE = "spec:width=1440px,height=1920px,dpi=160"
private const val MONJARO_2023_2025_DEVICE = "spec:width=1920px,height=720px,dpi=160"

// The refreshed 15.4-inch Flyme Auto display is commonly specified as a 2.5K 2560x1600 panel.
// Keep this as a preview profile until the exact Android Display metrics are captured in-car.
private const val MONJARO_2026_DEVICE = "spec:width=2560px,height=1600px,dpi=160"

@Preview(
    group = "Cityray / Atlas · 1440×1920",
    device = CITYRAY_ATLAS_DEVICE,
    locale = "ru",
    showSystemUi = false,
)
@Preview(
    group = "Monjaro 2023–2025 · 1920×720",
    device = MONJARO_2023_2025_DEVICE,
    locale = "ru",
    showSystemUi = false,
)
@Preview(
    group = "Monjaro 2026+ · 2560×1600",
    device = MONJARO_2026_DEVICE,
    locale = "ru",
    showSystemUi = false,
)
private annotation class HeadUnitPreviews

@HeadUnitPreviews
@Composable
private fun DiagnosticsPreview() = PreviewApp(AppTab.DIAGNOSTICS)

@HeadUnitPreviews
@Composable
private fun ParametersPreview() = PreviewApp(AppTab.PARAMETERS)

@Preview(
    name = "Cityray / Atlas · частичная доступность",
    group = "Состояния параметров · Cityray / Atlas",
    device = CITYRAY_ATLAS_DEVICE,
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun ParametersPartialPreview() {
    PreviewApp(
        AppTab.PARAMETERS,
        previewState().copy(
            ecarxParameterStatus = ReadStatus.ERROR,
            ecarxParameterDetail = "ECARX API недоступен",
            vhalStatus = ReadStatus.AVAILABLE,
        ),
    )
}

@Preview(
    name = "Cityray / Atlas · RAW",
    group = "Состояния параметров · Cityray / Atlas",
    device = CITYRAY_ATLAS_DEVICE,
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun ParametersRawPreview() {
    PreviewApp(AppTab.PARAMETERS, rawProfilePreviewState())
}

@Preview(
    name = "Cityray / Atlas · источники недоступны",
    group = "Состояния параметров · Cityray / Atlas",
    device = CITYRAY_ATLAS_DEVICE,
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun ParametersErrorPreview() {
    PreviewApp(
        AppTab.PARAMETERS,
        previewState().copy(
            ecarxParameterStatus = ReadStatus.ERROR,
            ecarxParameterDetail = "ECARX API недоступен",
            vhalStatus = ReadStatus.ERROR,
            vhalDetail = "VHAL недоступен",
            parameters = emptyList(),
        ),
    )
}

@HeadUnitPreviews
@Composable
private fun FullscreenSensorPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = 1.5f),
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            FullscreenValueScreen(
                title = "Оставшееся топливо",
                apiName = "pafulvlindcdfulvlvalfromfutbl",
                idText = "свойство 10012",
                value = VehicleDisplayValue(display = "41.7 л", raw = "41700"),
                sourceLabels = listOf("VHAL · G426", "ECARX"),
                modeLabel = "АВТООБНОВЛЕНИЕ",
                isFavorite = true,
                onFavoriteToggle = {},
                onDismiss = {},
                chart = {
                    SensorHistoryChart(
                        samples = previewChartSamples(),
                        isLive = true,
                    )
                },
            ) {
                ValueLine("Тип", "int")
                ValueLine("Обновлено", "17:26:16 · только что")
                ValueLine("Расшифровка", "профиль G426")
                ValueLine("VHAL-сигнал", "0x2170901E")
                ValueLine("ID свойства", "10012")
            }
        }
    }
}

private fun previewChartSamples(): List<VehicleParameterSample> = listOf(
    41.2, 41.3, 41.3, 41.5, 41.4, 41.6, 41.7,
).mapIndexed { index, value ->
    VehicleParameterSample(
        timestampMillis = SAMPLE_TICK_TIME + index * 10_000L,
        value = value,
    )
}

@HeadUnitPreviews
@Composable
private fun VehiclePreview() = PreviewApp(AppTab.VEHICLE)

@HeadUnitPreviews
@Composable
private fun FunctionsPreview() = PreviewApp(AppTab.FUNCTIONS)

@HeadUnitPreviews
@Composable
private fun LogPreview() = PreviewApp(AppTab.LOG)

@Preview(
    name = "Cityray / Atlas · Светлая тема",
    group = "Темы · Cityray / Atlas",
    device = CITYRAY_ATLAS_DEVICE,
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showSystemUi = false,
)
@Composable
private fun LightThemePreview() = PreviewApp(AppTab.DIAGNOSTICS)

@Preview(
    name = "Cityray / Atlas · Тёмная тема",
    group = "Темы · Cityray / Atlas",
    device = CITYRAY_ATLAS_DEVICE,
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showSystemUi = false,
)
@Composable
private fun DarkThemePreview() = PreviewApp(AppTab.DIAGNOSTICS)

@Composable
private fun PreviewApp(tab: AppTab, state: AppUiState = previewState()) {
    GeelyDiagnosticsApp(
        state = state,
        onRefresh = {},
        onExport = {},
        onVhalProfileSelected = {},
        onFavoriteToggle = {},
        onClearLog = {},
        initialTab = tab,
    )
}

private fun previewState() = AppUiState(
    carStatus = ReadStatus.AVAILABLE,
    carDetail = "CONNECTED",
    diagnosticsStatus = ReadStatus.AVAILABLE,
    diagnosticsDetail = "AVAILABLE",
    dtcManagerStatus = ReadStatus.AVAILABLE,
    dtcManagerDetail = "AVAILABLE",
    ecarxParameterStatus = ReadStatus.AVAILABLE,
    ecarxParameterDetail = "21 из 102; live-подписок: 21",
    vhalStatus = ReadStatus.AVAILABLE,
    vhalDetail = "84 значения · 48 расшифровано G426 · auto: 36 (callback)",
    carInfoStatus = ReadStatus.AVAILABLE,
    carInfoDetail = "18 из 34",
    functionStatus = ReadStatus.AVAILABLE,
    functionDetail = "37 из 272",
    selectedVhalProfile = VehicleProfile.G426,
    dtcs = listOf(
        sampleDtc(ecuType = 1, id = "1-1", code = "P0016", status = 1),
        sampleDtc(ecuType = 1, id = "1-2", code = "P0300", status = 1),
        sampleDtc(ecuType = 2, id = "2", status = 1),
        sampleDtc(ecuType = 3, id = "3", status = 1),
        sampleDtc(ecuType = 4, id = "4", status = 1),
        sampleDtc(ecuType = 5, id = "5", status = 1),
        sampleDtc(ecuType = 6, id = "6", status = 1),
        sampleDtc(ecuType = 7, id = "7", status = 1),
        sampleDtc(ecuType = 8, id = "8", status = 0),
    ),
    parameters = previewParameters(parameterSnapshots()),
    vehicleInfo = listOf(
        info(1049088, "INT_INFO_VEHICLE_TYPES", "Тип силовой установки", "Бензин/ДВС", "1049089"),
        info(1049600, "INT_INFO_DRIVE_MODE", "Тип привода", "Передний привод", "1049601"),
        info(2097408, "FLT_INFO_FUEL_CAPACITY", "Объём топливного бака", "54"),
        info(3149824, "STRING_INFO_CAR_TIRE_CONFIG", "Конфигурация шин", "235/45 R19"),
        info(8389888, "CONFIG_INFO_360CAM", "Камеры 360°", "Установлено", "8388610"),
        info(8391424, "CONFIG_INFO_RADAR", "Радары", "Установлено", "8388610"),
        info(8390912, "CONFIG_INFO_SUNROOF", "Люк", "Установлено", "8388610"),
        info(1050624, "INT_INFO_SPEAKER_TOTAL_COUNT", "Количество динамиков", "8"),
    ),
    functions = listOf(
        function(537265152, "SETTING_FUNC_AUTO_HOLD", "Auto Hold", "1", "0, 1"),
        function(
            537002240,
            "SETTING_FUNC_ENGINE_STOP_START",
            "Старт-стоп двигателя",
            "0",
            "0, 1",
            ApiSupportStatus.NOT_ACTIVE,
        ),
        function(537921792, "SETTING_FUNC_CENTRAL_LOCK", "Центральный замок", "1", "0, 1"),
        function(537461248, "SETTING_FUNC_MIRROR_AUTO_FOLDING", "Автоскладывание зеркал", "1", "0, 1"),
        function(537133824, "SETTING_FUNC_LAMP_AUTOLIGHT", "Автоматический свет", "537133826", "537133825, 537133826, 537133827"),
        function(537333248, "SETTING_FUNC_AUTONOMOUS_EMERGENCY_BRAKING", "Автоматическое экстренное торможение", "1", "0, 1"),
        function(537329920, "SETTING_FUNC_LANE_KEEPING_AID", "Удержание в полосе", "1", "0, 1"),
        function(537723136, "SETTING_FUNC_PARK_ASSIST_SYS_ACTIVATED", "Система помощи при парковке", "1", "0, 1"),
    ),
    logLines = listOf(
        "17:26:14.108  СИСТЕМА · Новый опрос источников",
        "17:26:14.170  ECARX · Car.create(): OK",
        "17:26:14.206  ECARX · getDtcInfos(): 9 records",
        "17:26:14.420  ECARX · Sensors: 21 supported of 102",
        "17:26:14.512  VHAL initial id=0x2170901E mapping=G426 display=41.7 л raw=41700",
        "17:26:16.034  VHAL live id=0x21400400 mapping=G426 display=D raw=3",
    ),
    favoriteKeys = setOf("property:10001:0", "property:10012:0"),
    scanStartedAtMillis = System.currentTimeMillis(),
)

private fun parameterSnapshots(vhalMapped: Boolean = true) = listOf(
        sensor(1048832, "SENSOR_TYPE_CAR_SPEED", "Скорость автомобиля", "0", "float"),
        sensor(1050880, "SENSOR_TYPE_RPM", "Обороты двигателя", "748", "float"),
        sensor(1050368, "SENSOR_TYPE_ODOMETER", "Пробег", "18432.7", "float"),
        sensor(1050112, "SENSOR_TYPE_FUEL_LEVEL", "Уровень топлива", "63", "float"),
        sensor(1050624, "SENSOR_TYPE_ENDURANCE_MILEAGE", "Запас хода", "481", "float"),
        sensor(1052416, "SENSOR_TYPE_ENGINE_COOLANT_TEMPERATURE", "Температура охлаждающей жидкости", "91", "float"),
        sensor(1051392, "SENSOR_TYPE_TEMPERATURE_AMBIENT", "Температура снаружи", "18.5", "float"),
        sensor(2097664, "SENSOR_TYPE_GEAR", "Передача", "2097696", "event/int"),
        vhalSensor(557874334, 10003, "GearLvrIndcn", "Передача", "D", "3", "char", vhalMapped),
        vhalSensor(561025054, 10012, "pafulvlindcdfulvlvalfromfutbl", "Оставшееся топливо", "41.7 л", "41700", "int", vhalMapped),
        vhalSensor(561024410, 10013, "patirepressurefrontleft", "Давление в передней левой шине", "236 кПа", "2360", "float", vhalMapped),
        vhalSensor(557850019, 10021, "CLUSTER_POWERFLOW_ENGSPDDISPD", "Обороты двигателя", "748 об/мин", "1496", "int", vhalMapped),
        rawVhalSensor(557842947, "[1, 0, 42]", "int32[]"),
)

private fun previewParameters(values: List<CarPropertySnapshot>) = UnifiedParameterCache().apply {
    replaceSource(VehiclePropertySource.ECARX, values.filter { it.source == VehiclePropertySource.ECARX })
    replaceSource(VehiclePropertySource.VHAL, values.filter { it.source == VehiclePropertySource.VHAL })
}.parameters()

private fun rawProfilePreviewState(): AppUiState {
    return previewState().copy(
        selectedVhalProfile = VehicleProfile.RAW,
        vhalDetail = "84 исходных сигнала · профиль не выбран",
        parameters = previewParameters(parameterSnapshots(vhalMapped = false)),
    )
}

private fun sampleDtc(
    ecuType: Int,
    id: String,
    code: String = "",
    status: Int,
) = DtcRecord(code, id, ecuType, status, SAMPLE_TICK_TIME)

private fun sensor(id: Int, apiName: String, title: String, value: String, kind: String): CarPropertySnapshot {
    val display = if (kind == "event/int") {
            VendorValueDecoder.sensor(apiName, value.toInt())
        } else {
            VendorValueDecoder.sensor(apiName, value.toFloat())
        }
    val number = display.raw.toDoubleOrNull()
    return CarPropertySnapshot(
        propertyId = EcarxNormalizedPropertyRegistry.sensorProperty(apiName),
        value = number?.let { if (kind == "float") CarValue.FloatValue(it) else CarValue.IntValue(it.toInt()) },
        displayValue = display.display,
        rawValue = RawVehicleValue(display.raw, number),
        status = VehiclePropertyStatus.AVAILABLE,
        source = VehiclePropertySource.ECARX,
        sourceSignalId = id,
        sourceSignalName = apiName,
        sourceTitle = title,
        receivedAtMillis = System.currentTimeMillis(),
        expectedUpdateIntervalMillis = if (kind == "float") 15_000L else null,
        autoUpdates = kind == "float",
        valueKind = kind,
    )
}

private fun info(id: Int, apiName: String, title: String, value: String, raw: String = value) =
    VehicleInfoRecord(
        id,
        apiName,
        title,
        VehicleDisplayValue(value, raw),
        ApiSupportStatus.ACTIVE,
        updatedAtMillis = System.currentTimeMillis(),
    )

private fun vhalSensor(
    id: Int,
    propertyId: Int,
    apiName: String,
    title: String,
    value: String,
    raw: String,
    kind: String,
    mapped: Boolean,
) = CarPropertySnapshot(
    propertyId = CarPropertyId(propertyId).takeIf { mapped },
    value = raw.toDoubleOrNull()?.let { CarValue.FloatValue(it) } ?: CarValue.StringValue(raw),
    displayValue = value,
    rawValue = RawVehicleValue(raw, raw.toDoubleOrNull()),
    status = VehiclePropertyStatus.AVAILABLE,
    source = VehiclePropertySource.VHAL,
    sourceSignalId = id,
    sourceSignalName = apiName,
    sourceTitle = title,
    profileKey = VehicleProfile.G426.key.takeIf { mapped },
    receivedAtMillis = System.currentTimeMillis(),
    expectedUpdateIntervalMillis = 15_000L,
    autoUpdates = true,
    valueKind = kind,
)

private fun rawVhalSensor(id: Int, raw: String, kind: String) = CarPropertySnapshot(
    propertyId = null,
    value = CarValue.StringValue(raw),
    displayValue = raw,
    rawValue = RawVehicleValue(raw),
    status = VehiclePropertyStatus.AVAILABLE,
    source = VehiclePropertySource.VHAL,
    sourceSignalId = id,
    sourceSignalName = "VHAL_0x${id.toUInt().toString(16).uppercase()}",
    sourceTitle = "Неизвестный VHAL-сигнал 0x${id.toUInt().toString(16).uppercase()}",
    receivedAtMillis = System.currentTimeMillis(),
    valueKind = kind,
)

private fun function(
    id: Int,
    apiName: String,
    title: String,
    value: String,
    supportedValues: String,
    support: ApiSupportStatus = ApiSupportStatus.ACTIVE,
) = VehicleFunctionRecord(
    id = id,
    apiName = apiName,
    title = title,
    value = VendorValueDecoder.function(
        apiName,
        value.toInt(),
        supportedValues.split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray(),
    ),
    supportedValues = supportedValues,
    support = support,
    updatedAtMillis = System.currentTimeMillis(),
)
