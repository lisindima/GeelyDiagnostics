package com.geelydiagnostics.app.ui

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.tabs.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend

private const val MIN_HEAD_UNIT_FONT_SCALE = 1.5f

internal enum class AppTab(val title: String) {
    DIAGNOSTICS("Диагностика"),
    PARAMETERS("Параметры"),
    VEHICLE("Автомобиль"),
    FUNCTIONS("Возможности"),
    LOG("Лог"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun GeelyDiagnosticsApp(
    state: AppUiState,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onVhalProfileSelected: (VehicleProfile) -> Unit,
    onVhalBackendSelected: (VhalGatewayBackend) -> Unit,
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
                    AppHeader(
                        onRefresh = onRefresh,
                        onExport = onExport,
                        isRefreshInProgress = state.isScanInProgress,
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 1000.dp) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = selectedTabIndex,
                                edgePadding = 0.dp,
                                minTabWidth = 150.dp,
                            ) {
                                AppTabs(selectedTabIndex) { selectedTabIndex = it }
                            }
                        } else {
                            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                                AppTabs(selectedTabIndex) { selectedTabIndex = it }
                            }
                        }
                    }
                    val selectedTab = AppTab.entries[selectedTabIndex]
                    AppTabContent(
                        tab = selectedTab,
                        state = state,
                        onVhalProfileSelected = onVhalProfileSelected,
                        onVhalBackendSelected = onVhalBackendSelected,
                        onFavoriteToggle = onFavoriteToggle,
                        onClearLog = onClearLog,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTabs(selectedTabIndex: Int, onSelected: (Int) -> Unit) {
    AppTab.entries.forEachIndexed { index, tab ->
        Tab(
            selected = selectedTabIndex == index,
            onClick = { onSelected(index) },
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

@Composable
private fun AppTabContent(
    tab: AppTab,
    state: AppUiState,
    onVhalProfileSelected: (VehicleProfile) -> Unit,
    onVhalBackendSelected: (VhalGatewayBackend) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onClearLog: () -> Unit,
) {
    when (tab) {
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
        AppTab.PARAMETERS -> ParametersTab.Content(
            state,
            onVhalProfileSelected,
            onVhalBackendSelected,
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

private val AppUiState.isScanInProgress: Boolean
    get() = listOf(
        carStatus,
        diagnosticsStatus,
        dtcManagerStatus,
        ecarxParameterStatus,
        vhalStatus,
        carInfoStatus,
        functionStatus,
    ).any { it == ReadStatus.CHECKING }

@Composable
private fun AppHeader(
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    isRefreshInProgress: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        BoxWithConstraints {
            if (maxWidth < 900.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HeaderTitle()
                    ReadOnlyBadge()
                    HeaderActions(
                        onRefresh = onRefresh,
                        onExport = onExport,
                        isRefreshInProgress = isRefreshInProgress,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderTitle(Modifier.weight(1f))
                    ReadOnlyBadge()
                    Spacer(Modifier.width(10.dp))
                    HeaderActions(
                        onRefresh = onRefresh,
                        onExport = onExport,
                        isRefreshInProgress = isRefreshInProgress,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderTitle(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Geely Diagnostics",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Диагностика и параметры автомобиля",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun ReadOnlyBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "ТОЛЬКО ЧТЕНИЕ",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HeaderActions(
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    isRefreshInProgress: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onExport,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                contentColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text("Экспорт", fontSize = 17.sp)
        }
        Button(
            onClick = onRefresh,
            enabled = !isRefreshInProgress,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                contentColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text(
                text = if (isRefreshInProgress) "Обновление…" else "Обновить",
                fontSize = 17.sp,
            )
        }
    }
}
