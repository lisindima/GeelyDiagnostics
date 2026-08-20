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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
internal fun GeelyDiagnosticsApp(
    state: AppUiState,
    onRefresh: () -> Unit,
    onVhalProfileSelected: (VhalProfile) -> Unit,
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
                    AppHeader(onRefresh = onRefresh)
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
                        AppTab.DIAGNOSTICS -> DiagnosticsTab.Content(state)
                        AppTab.SENSORS -> SensorsTab.Content(state, onVhalProfileSelected)
                        AppTab.VEHICLE -> VehicleTab.Content(state)
                        AppTab.FUNCTIONS -> FunctionsTab.Content(state)
                        AppTab.LOG -> LogTab.Content(state.logLines, onClearLog)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(onRefresh: () -> Unit) {
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
                text = "ECARX AdaptAPI + VHAL · строго только чтение",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onRefresh) {
            Text("Обновить", fontSize = 17.sp)
        }
    }
}
