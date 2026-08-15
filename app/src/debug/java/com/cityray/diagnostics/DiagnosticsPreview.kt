package com.cityray.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

private const val SAMPLE_TICK_TIME = 1786695380305L

@Preview(
    name = "ГУ 1440×1920 · несколько DTC в одном ECU",
    device = "spec:width=1440px,height=1920px,dpi=160",
    locale = "ru",
    showSystemUi = false,
)
@Composable
private fun HeadUnitDiagnosticsPreview() {
    DiagnosticsApp(
        state = DiagnosticsUiState(
            carStatus = DiagnosticsStatus.AVAILABLE,
            carDetail = "CONNECTED",
            diagnosticsStatus = DiagnosticsStatus.AVAILABLE,
            diagnosticsDetail = "AVAILABLE",
            dtcManagerStatus = DiagnosticsStatus.AVAILABLE,
            dtcManagerDetail = "AVAILABLE",
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
            logLines = listOf(
                "17:26:14.108  Diagnostics started on Android 11 (API 30)",
                "17:26:14.110  Display: 1440x1920px, density=1.0, fontScale=1.0, orientation=1",
                "17:26:14.170  Car.create(): OK",
                "17:26:14.206  getDtcInfos(): 9 records",
            ),
        ),
        onRetry = {},
        onClearLog = {},
    )
}

private fun sampleDtc(
    ecuType: Int,
    id: String,
    code: String = "",
    status: Int,
) = DtcRecord(
    code = code,
    id = id,
    ecuType = ecuType,
    status = status,
    tickTime = SAMPLE_TICK_TIME,
)
