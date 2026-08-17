package com.geelydiagnostics.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

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

@Composable
private fun PreviewApp(tab: AppTab) {
    GeelyDiagnosticsApp(
        state = previewState(),
        onRefresh = {},
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
        sensor(2097664, "SENSOR_TYPE_GEAR", "Передача", "3", "event/int"),
    ),
    vehicleInfo = listOf(
        info(1049088, "INT_INFO_VEHICLE_TYPES", "Тип силовой установки", "Бензин/ДВС · raw: 1049089"),
        info(1049600, "INT_INFO_DRIVE_MODE", "Тип привода", "Передний привод · raw: 1049601"),
        info(2097408, "FLT_INFO_FUEL_CAPACITY", "Объём топливного бака", "54"),
        info(3149824, "STRING_INFO_CAR_TIRE_CONFIG", "Конфигурация шин", "235/45 R19"),
        info(8389888, "CONFIG_INFO_360CAM", "Камеры 360°", "Установлено · raw: 8388610"),
        info(8391424, "CONFIG_INFO_RADAR", "Радары", "Установлено · raw: 8388610"),
        info(8390912, "CONFIG_INFO_SUNROOF", "Люк", "Установлено · raw: 8388610"),
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
    ),
)

private fun sampleDtc(
    ecuType: Int,
    id: String,
    code: String = "",
    status: Int,
) = DtcRecord(code, id, ecuType, status, SAMPLE_TICK_TIME)

private fun sensor(id: Int, apiName: String, title: String, value: String, kind: String) =
    SensorRecord(id, apiName, title, value, kind, ApiSupportStatus.ACTIVE)

private fun info(id: Int, apiName: String, title: String, value: String) =
    VehicleInfoRecord(id, apiName, title, value, ApiSupportStatus.ACTIVE)

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
    value = value,
    supportedValues = supportedValues,
    support = ApiSupportStatus.ACTIVE,
)
