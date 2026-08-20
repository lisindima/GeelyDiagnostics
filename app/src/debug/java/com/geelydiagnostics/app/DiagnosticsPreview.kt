package com.geelydiagnostics.app

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density

private const val SAMPLE_TICK_TIME = 1786695380305L

@Preview(
    name = "ГУ · Диагностика",
    group = "1440×1920",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun DiagnosticsPreview() = PreviewApp(AppTab.DIAGNOSTICS)

@Preview(
    name = "ГУ · Сенсоры",
    group = "1440×1920",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun SensorsPreview() = PreviewApp(AppTab.SENSORS)

@Preview(
    name = "ГУ · Значение на весь экран",
    group = "1440×1920",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showSystemUi = false,
)
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
                idText = "id 561025054",
                value = ApiValue(display = "41.7 л", raw = "41700"),
                sourceLabel = "VHAL · маппинг G426",
                modeLabel = "АВТООБНОВЛЕНИЕ",
                onDismiss = {},
            ) {
                ValueLine("Тип", "int")
                ValueLine("Расшифровка", "профиль G426")
                ValueLine("VHAL ID", "0x2170901E")
                ValueLine("Поле профиля", "10012")
            }
        }
    }
}

@Preview(
    name = "ГУ · Автомобиль",
    group = "1440×1920",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun VehiclePreview() = PreviewApp(AppTab.VEHICLE)

@Preview(
    name = "ГУ · Функции",
    group = "1440×1920",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun FunctionsPreview() = PreviewApp(AppTab.FUNCTIONS)

@Preview(
    name = "ГУ · Лог",
    group = "1440×1920",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun LogPreview() = PreviewApp(AppTab.LOG)

@Preview(
    name = "ГУ · Светлая тема",
    group = "Темы",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showSystemUi = false,
)
@Composable
private fun LightThemePreview() = PreviewApp(AppTab.DIAGNOSTICS)

@Preview(
    name = "ГУ · Тёмная тема",
    group = "Темы",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showSystemUi = false,
)
@Composable
private fun DarkThemePreview() = PreviewApp(AppTab.DIAGNOSTICS)

@Composable
private fun PreviewApp(tab: AppTab) {
    GeelyDiagnosticsApp(
        state = previewState(),
        onRefresh = {},
        onVhalProfileSelected = {},
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
    sensorStatus = ReadStatus.AVAILABLE,
    sensorDetail = "21 из 102; live-подписок: 21",
    vhalStatus = ReadStatus.AVAILABLE,
    vhalDetail = "84 значения · 48 расшифровано G426 · auto: 36 (callback)",
    carInfoStatus = ReadStatus.AVAILABLE,
    carInfoDetail = "18 из 34",
    functionStatus = ReadStatus.AVAILABLE,
    functionDetail = "37 из 272",
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
    sensors = listOf(
        sensor(1048832, "SENSOR_TYPE_CAR_SPEED", "Скорость автомобиля", "0", "float"),
        sensor(1050880, "SENSOR_TYPE_RPM", "Обороты двигателя", "748", "float"),
        sensor(1050368, "SENSOR_TYPE_ODOMETER", "Пробег", "18432.7", "float"),
        sensor(1050112, "SENSOR_TYPE_FUEL_LEVEL", "Уровень топлива", "63", "float"),
        sensor(1050624, "SENSOR_TYPE_ENDURANCE_MILEAGE", "Запас хода", "481", "float"),
        sensor(1052416, "SENSOR_TYPE_ENGINE_COOLANT_TEMPERATURE", "Температура охлаждающей жидкости", "91", "float"),
        sensor(1051392, "SENSOR_TYPE_TEMPERATURE_AMBIENT", "Температура снаружи", "18.5", "float"),
        sensor(2097664, "SENSOR_TYPE_GEAR", "Передача", "2097696", "event/int"),
        vhalSensor(557874334, 10003, "GearLvrIndcn", "Передача", "D", "3", "char"),
        vhalSensor(561025054, 10012, "pafulvlindcdfulvlvalfromfutbl", "Оставшееся топливо", "41.7 л", "41700", "int"),
        vhalSensor(561024410, 10013, "patirepressurefrontleft", "Давление в передней левой шине", "236 кПа", "2360", "float"),
        vhalSensor(557850019, 10021, "CLUSTER_POWERFLOW_ENGSPDDISPD", "Обороты двигателя", "748 об/мин", "1496", "int"),
        rawVhalSensor(557842947, "[1, 0, 42]", "int32[]"),
    ),
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
        function(537002240, "SETTING_FUNC_ENGINE_STOP_START", "Старт-стоп двигателя", "0", "0, 1"),
        function(537921792, "SETTING_FUNC_CENTRAL_LOCK", "Центральный замок", "1", "0, 1"),
        function(537461248, "SETTING_FUNC_MIRROR_AUTO_FOLDING", "Автоскладывание зеркал", "1", "0, 1"),
        function(537133824, "SETTING_FUNC_LAMP_AUTOLIGHT", "Автоматический свет", "537133826", "537133825, 537133826, 537133827"),
        function(537333248, "SETTING_FUNC_AUTONOMOUS_EMERGENCY_BRAKING", "Автоматическое экстренное торможение", "1", "0, 1"),
        function(537329920, "SETTING_FUNC_LANE_KEEPING_AID", "Удержание в полосе", "1", "0, 1"),
        function(537723136, "SETTING_FUNC_PARK_ASSIST_SYS_ACTIVATED", "Система помощи при парковке", "1", "0, 1"),
    ),
    logLines = listOf(
        "17:26:14.108  Read-only scan started on Android 11 (API 30)",
        "17:26:14.170  Car.create(): OK",
        "17:26:14.206  getDtcInfos(): 9 records",
        "17:26:14.420  Sensors: 21 supported of 102",
        "17:26:14.512  VHAL initial id=0x2170901E mapping=G426 display=41.7 л raw=41700",
        "17:26:16.034  VHAL live id=0x21400400 mapping=G426 display=D raw=3",
    ),
)

private fun sampleDtc(
    ecuType: Int,
    id: String,
    code: String = "",
    status: Int,
) = DtcRecord(code, id, ecuType, status, SAMPLE_TICK_TIME)

private fun sensor(id: Int, apiName: String, title: String, value: String, kind: String) =
    SensorRecord(
        id,
        apiName,
        title,
        if (kind == "event/int") {
            VendorValueDecoder.sensor(apiName, value.toInt())
        } else {
            VendorValueDecoder.sensor(apiName, value.toFloat())
        },
        kind,
        ApiSupportStatus.ACTIVE,
    )

private fun info(id: Int, apiName: String, title: String, value: String, raw: String = value) =
    VehicleInfoRecord(id, apiName, title, ApiValue(value, raw), ApiSupportStatus.ACTIVE)

private fun vhalSensor(
    id: Int,
    propertyId: Int,
    apiName: String,
    title: String,
    value: String,
    raw: String,
    kind: String,
) = SensorRecord(
    id = id,
    apiName = apiName,
    title = title,
    value = ApiValue(value, raw),
    valueKind = kind,
    support = ApiSupportStatus.ACTIVE,
    source = VehicleDataSource.VHAL,
    sourceProfile = VhalProfile.G426.key,
    profilePropertyId = propertyId,
)

private fun rawVhalSensor(id: Int, raw: String, kind: String) = SensorRecord(
    id = id,
    apiName = "VHAL_0x${id.toUInt().toString(16).uppercase()}",
    title = "VHAL property 0x${id.toUInt().toString(16).uppercase()}",
    value = ApiValue.raw(raw),
    valueKind = kind,
    support = ApiSupportStatus.ACTIVE,
    source = VehicleDataSource.VHAL,
)

private fun function(
    id: Int,
    apiName: String,
    title: String,
    value: String,
    supportedValues: String,
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
    support = ApiSupportStatus.ACTIVE,
)
