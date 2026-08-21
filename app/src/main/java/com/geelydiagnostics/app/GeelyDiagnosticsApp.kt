package com.geelydiagnostics.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MIN_HEAD_UNIT_FONT_SCALE = 1.5f

internal enum class AppTab(val title: String) {
    DIAGNOSTICS("Диагностика"),
    SENSORS("Сенсоры"),
    VEHICLE("Автомобиль"),
    FUNCTIONS("Функции"),
    LOG("Лог"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun GeelyDiagnosticsApp(
    state: AppUiState,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onVhalProfileSelected: (VhalProfile) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onClearLog: () -> Unit,
    initialTab: AppTab = AppTab.DIAGNOSTICS,
) {
    val systemDensity = LocalDensity.current
    val readableDensity = Density(
        density = systemDensity.density,
        fontScale = maxOf(systemDensity.fontScale, MIN_HEAD_UNIT_FONT_SCALE),
    )
    CompositionLocalProvider(LocalDensity provides readableDensity) {
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) {
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                ) {
                    AppHeader(onRefresh = onRefresh, onExport = onExport)
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
                    PullToRefreshBox(
                        isRefreshing = state.isScanInProgress,
                        onRefresh = { if (!state.isScanInProgress) onRefresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (AppTab.entries[selectedTabIndex]) {
                            AppTab.DIAGNOSTICS -> {
                                val tabState = remember(
                                    state.carStatus,
                                    state.carDetail,
                                    state.diagnosticsStatus,
                                    state.diagnosticsDetail,
                                    state.dtcManagerStatus,
                                    state.dtcManagerDetail,
                                    state.dtcs,
                                ) { state }
                                DiagnosticsTab.Content(tabState)
                            }
                            AppTab.SENSORS -> SensorsTab.Content(
                                state,
                                onVhalProfileSelected,
                                onFavoriteToggle,
                            )
                            AppTab.VEHICLE -> {
                                val tabState = remember(
                                    state.carInfoStatus,
                                    state.carInfoDetail,
                                    state.vehicleInfo,
                                    state.favoriteKeys,
                                ) { state }
                                VehicleTab.Content(tabState, onFavoriteToggle)
                            }
                            AppTab.FUNCTIONS -> {
                                val tabState = remember(
                                    state.functionStatus,
                                    state.functionDetail,
                                    state.functions,
                                    state.favoriteKeys,
                                ) { state }
                                FunctionsTab.Content(tabState, onFavoriteToggle)
                            }
                            AppTab.LOG -> LogTab.Content(state.logLines, onClearLog)
                        }
                    }
                }
            }
        }
    }
}

private val AppUiState.isScanInProgress: Boolean
    get() = listOf(
        carStatus,
        diagnosticsStatus,
        dtcManagerStatus,
        sensorStatus,
        vhalStatus,
        carInfoStatus,
        functionStatus,
    ).any { it == ReadStatus.CHECKING }

@Composable
private fun AppHeader(onRefresh: () -> Unit, onExport: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Geely Diagnostics",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "ECARX AdaptAPI + VHAL · строго только чтение",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Text("Экспорт", fontSize = 17.sp)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Text("Обновить", fontSize = 17.sp)
            }
        }
    }
}
