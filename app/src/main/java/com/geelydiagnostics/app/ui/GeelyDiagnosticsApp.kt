package com.geelydiagnostics.app.ui

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.tabs.*
import com.geelydiagnostics.app.ui.theme.*
import com.geelydiagnostics.app.ui.settings.VhalSettingsDialog
import com.geelydiagnostics.app.ui.diagnostics.diagnosticsUiState
import com.geelydiagnostics.app.vehicle.property.VehiclePropertySource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    initialDiagnosticsCategory: DiagnosticsCategory = DiagnosticsCategory.ECU,
) {
    val systemDensity = LocalDensity.current
    val readableDensity = Density(
        density = systemDensity.density,
        fontScale = maxOf(systemDensity.fontScale, MIN_HEAD_UNIT_FONT_SCALE),
    )
    CompositionLocalProvider(LocalDensity provides readableDensity) {
        val colorScheme = if (isSystemInDarkTheme()) {
            GeelyDiagnosticsDarkColors
        } else {
            GeelyDiagnosticsLightColors
        }
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = GeelyDiagnosticsShapes,
        ) {
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
            var showVhalSettings by rememberSaveable { mutableStateOf(false) }
            val tabStateHolder = rememberSaveableStateHolder()
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppSpacing.ScreenHorizontal),
                ) {
                    AppHeader(
                        onRefresh = onRefresh,
                        onExport = onExport,
                        onVhalSettings = { showVhalSettings = true },
                        isRefreshInProgress = state.isScanInProgress,
                    )
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        AppTabs(selectedTabIndex) { selectedTabIndex = it }
                    }
                    val selectedTab = AppTab.entries[selectedTabIndex]
                    tabStateHolder.SaveableStateProvider(selectedTab.name) {
                        AppTabContent(
                            tab = selectedTab,
                            state = state,
                            initialDataCategory = initialDataCategory,
                            initialDiagnosticsCategory = initialDiagnosticsCategory,
                            onFavoriteToggle = onFavoriteToggle,
                            onObserveParameter = onObserveParameter,
                            onClearLog = onClearLog,
                        )
                    }
                }
            }
            if (showVhalSettings) {
                val vhalReadings = (state.parameters + state.vehicleInfo + state.functions)
                    .flatMap(VehicleParameter::sourceReadings)
                    .filter { it.source == VehiclePropertySource.VHAL }
                VhalSettingsDialog(
                    selectedProfile = state.selectedVhalProfile,
                    selectedBackend = state.selectedVhalBackend,
                    decodedCount = vhalReadings.count { it.decoded },
                    totalCount = vhalReadings.size,
                    onProfileSelected = onVhalProfileSelected,
                    onBackendSelected = onVhalBackendSelected,
                    onDismiss = { showVhalSettings = false },
                )
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
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
    initialDiagnosticsCategory: DiagnosticsCategory,
    onFavoriteToggle: (String) -> Unit,
    onObserveParameter: (VehicleParameter) -> Flow<VehicleParameter?>,
    onClearLog: () -> Unit,
) {
    when (tab) {
        AppTab.DIAGNOSTICS -> {
            val diagnosticState = state.diagnosticsUiState()
            val tabState = remember(diagnosticState) { diagnosticState }
            DiagnosticsTab.Content(tabState, initialDiagnosticsCategory)
        }
        AppTab.DATA -> DataTab.Content(
            state = state,
            initialCategory = initialDataCategory,
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
    onVhalSettings: () -> Unit,
    isRefreshInProgress: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Default),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        BoxWithConstraints {
            val inlineActionsMinWidth = AppBreakpoints.HeaderInlineActions *
                (LocalDensity.current.fontScale / MIN_HEAD_UNIT_FONT_SCALE)
            if (maxWidth < inlineActionsMinWidth) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = AppSpacing.Large,
                        vertical = AppSpacing.Default,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderTitle(Modifier.weight(1f))
                        ReadOnlyBadge()
                    }
                    HeaderActions(
                        onRefresh = onRefresh,
                        onExport = onExport,
                        onVhalSettings = onVhalSettings,
                        isRefreshInProgress = isRefreshInProgress,
                        modifier = Modifier.fillMaxWidth(),
                        expandButtons = true,
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
                        onVhalSettings = onVhalSettings,
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
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "Диагностика и данные автомобиля",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = AppType.Supporting,
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
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun HeaderActions(
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onVhalSettings: () -> Unit,
    isRefreshInProgress: Boolean,
    modifier: Modifier = Modifier,
    expandButtons: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
    ) {
        val buttonModifier = Modifier
            .heightIn(min = AppSizes.HeaderActionMinHeight)
            .then(if (expandButtons) Modifier.weight(1f) else Modifier)
        val buttonColors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
        )
        val buttonBorder = BorderStroke(
            AppSizes.Border,
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.58f),
        )
        OutlinedButton(
            onClick = onVhalSettings,
            modifier = buttonModifier,
            shape = MaterialTheme.shapes.medium,
            colors = buttonColors,
            border = buttonBorder,
        ) {
            Text(
                text = "Настройки VHAL",
                fontSize = AppType.Supporting,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isRefreshInProgress,
            modifier = buttonModifier,
            shape = MaterialTheme.shapes.medium,
            colors = buttonColors,
            border = buttonBorder,
        ) {
            Text(
                text = if (isRefreshInProgress) "Обновление…" else "Обновить",
                fontSize = AppType.Supporting,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(
            onClick = onExport,
            modifier = buttonModifier,
            shape = MaterialTheme.shapes.medium,
            colors = buttonColors,
            border = buttonBorder,
        ) {
            Text(
                text = "Экспорт",
                fontSize = AppType.Supporting,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
