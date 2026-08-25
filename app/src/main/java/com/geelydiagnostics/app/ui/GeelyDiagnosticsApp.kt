package com.geelydiagnostics.app.ui

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.tabs.*
import com.geelydiagnostics.app.ui.theme.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.OutlinedButton
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
import com.geelydiagnostics.app.vehicle.mapping.VehicleProfile
import com.geelydiagnostics.app.vehicle.vhal.VhalGatewayBackend
import com.geelydiagnostics.app.vehicle.property.VehicleParameter
import kotlinx.coroutines.flow.Flow

private const val MIN_HEAD_UNIT_FONT_SCALE = 1.5f

internal enum class AppTab(val title: String) {
    DIAGNOSTICS("Диагностика"),
    DATA("Данные"),
    LOG("Журнал"),
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
    onObserveParameter: (VehicleParameter) -> Flow<VehicleParameter?>,
    onClearLog: () -> Unit,
    initialTab: AppTab = AppTab.DIAGNOSTICS,
    initialDataCategory: DataCategory = DataCategory.PARAMETERS,
) {
    val systemDensity = LocalDensity.current
    val readableDensity = Density(
        density = systemDensity.density,
        fontScale = maxOf(systemDensity.fontScale, MIN_HEAD_UNIT_FONT_SCALE),
    )
    CompositionLocalProvider(LocalDensity provides readableDensity) {
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = GeelyDiagnosticsShapes,
        ) {
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppSpacing.ScreenHorizontal),
                ) {
                    AppHeader(
                        onRefresh = onRefresh,
                        onExport = onExport,
                        isRefreshInProgress = state.isScanInProgress,
                    )
                    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                        AppTabs(selectedTabIndex) { selectedTabIndex = it }
                    }
                    val selectedTab = AppTab.entries[selectedTabIndex]
                    AppTabContent(
                        tab = selectedTab,
                        state = state,
                        initialDataCategory = initialDataCategory,
                        onVhalProfileSelected = onVhalProfileSelected,
                        onVhalBackendSelected = onVhalBackendSelected,
                        onFavoriteToggle = onFavoriteToggle,
                        onObserveParameter = onObserveParameter,
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
                    fontSize = AppType.Standard,
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
    initialDataCategory: DataCategory,
    onVhalProfileSelected: (VehicleProfile) -> Unit,
    onVhalBackendSelected: (VhalGatewayBackend) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onObserveParameter: (VehicleParameter) -> Flow<VehicleParameter?>,
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
        AppTab.DATA -> DataTab.Content(
            state = state,
            initialCategory = initialDataCategory,
            onVhalProfileSelected = onVhalProfileSelected,
            onVhalBackendSelected = onVhalBackendSelected,
            onFavoriteToggle = onFavoriteToggle,
            onObserveParameter = onObserveParameter,
        )
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
            .padding(vertical = AppSpacing.Medium),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        BoxWithConstraints {
            if (maxWidth < AppBreakpoints.TwoColumns) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = AppSpacing.Large,
                        vertical = AppSpacing.Default,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
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
                    modifier = Modifier.padding(
                        horizontal = AppSpacing.Large,
                        vertical = AppSpacing.Default,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderTitle(Modifier.weight(1f))
                    ReadOnlyBadge()
                    Spacer(Modifier.width(AppSpacing.Small))
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
            fontSize = AppType.HeaderTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Диагностика и данные автомобиля",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = AppType.Standard,
        )
    }
}

@Composable
private fun ReadOnlyBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(
            AppSizes.Border,
            MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(
            text = "READ ONLY",
            modifier = Modifier.padding(
                horizontal = AppSpacing.Small,
                vertical = AppSpacing.XSmall,
            ),
            fontSize = AppType.Label,
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
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isRefreshInProgress,
        ) {
            Text(
                text = if (isRefreshInProgress) "Обновление…" else "Обновить",
                fontSize = AppType.Action,
            )
        }
        OutlinedButton(onClick = onExport) {
            Text("Экспорт", fontSize = AppType.Action)
        }
    }
}
