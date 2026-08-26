package com.geelydiagnostics.app.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.geelydiagnostics.app.ui.diagnostics.DiagnosticsUiState
import com.geelydiagnostics.app.ui.components.CatalogFilterRow
import com.geelydiagnostics.app.ui.diagnostics.ecuDiagnosticsItems
import com.geelydiagnostics.app.ui.diagnostics.obd2DiagnosticsItems
import com.geelydiagnostics.app.ui.diagnostics.diagnosticInfoItems
import com.geelydiagnostics.app.ui.theme.AppSpacing

internal enum class DiagnosticsCategory(val title: String) {
    ECU("Блоки ECU"),
    OBD2("OBD2"),
    INFO("Сведения"),
}

internal object DiagnosticsTab {
    @Composable
    fun Content(state: DiagnosticsUiState, initialCategory: DiagnosticsCategory = DiagnosticsCategory.ECU) {
        var selectedIndex by rememberSaveable { mutableIntStateOf(initialCategory.ordinal) }
        // Keep each section's scroll position independent, including live OBD2 updates.
        val ecuScroll = rememberLazyListState()
        val obd2Scroll = rememberLazyListState()
        val infoScroll = rememberLazyListState()
        val category = DiagnosticsCategory.entries[selectedIndex]
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = AppSpacing.ScreenTop, bottom = AppSpacing.Small)) {
                CatalogFilterRow(
                    labels = DiagnosticsCategory.entries.map(DiagnosticsCategory::title),
                    selectedIndex = selectedIndex,
                    onSelected = { selectedIndex = it },
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = when (category) {
                    DiagnosticsCategory.ECU -> ecuScroll
                    DiagnosticsCategory.OBD2 -> obd2Scroll
                    DiagnosticsCategory.INFO -> infoScroll
                },
                contentPadding = PaddingValues(bottom = AppSpacing.ScreenBottom),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Default),
            ) {
                when (category) {
                    DiagnosticsCategory.ECU -> ecuDiagnosticsItems(state)
                    DiagnosticsCategory.OBD2 -> obd2DiagnosticsItems(state)
                    DiagnosticsCategory.INFO -> diagnosticInfoItems(state)
                }
            }
        }
    }
}
