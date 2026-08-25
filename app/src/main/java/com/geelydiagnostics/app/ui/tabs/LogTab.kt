package com.geelydiagnostics.app.ui.tabs

import com.geelydiagnostics.app.model.*
import com.geelydiagnostics.app.ui.catalog.*
import com.geelydiagnostics.app.ui.components.*
import com.geelydiagnostics.app.ui.theme.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal object LogTab {

    private enum class LogFilter(val title: String) {
        ALL("Все"),
        ERRORS("Ошибки"),
        ECARX("ECARX"),
        VHAL("VHAL"),
        SYSTEM("Система"),
    }

    @Composable
    fun Content(lines: List<String>, onClear: () -> Unit) {
        var selectedFilterIndex by rememberSaveable { mutableIntStateOf(LogFilter.ALL.ordinal) }
        val selectedFilter = LogFilter.entries[selectedFilterIndex]
        val filtered = lines.filter { line -> line.matches(selectedFilter) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = AppSpacing.ScreenTop,
                bottom = AppSpacing.ScreenBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Default),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.CardContent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Журнал",
                                fontSize = AppType.CardTitle,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = "Показано ${filtered.size} из ${lines.size} строк",
                                fontSize = AppType.Supporting,
                            )
                        }
                        OutlinedButton(onClick = onClear) {
                            Text("Очистить", fontSize = AppType.Action)
                        }
                    }
                }
            }
            item {
                CatalogFilterRow(
                    labels = LogFilter.entries.map(LogFilter::title),
                    selectedIndex = selectedFilterIndex,
                    onSelected = { selectedFilterIndex = it },
                )
            }
            item { LogPanel(filtered) }
        }
    }

    @Composable
    private fun LogPanel(lines: List<String>) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(Modifier.padding(AppSpacing.CardContent)) {
                SelectionContainer {
                    if (lines.isEmpty()) {
                        Text(
                            text = "Журнал пуст",
                            fontFamily = FontFamily.Monospace,
                            fontSize = AppType.Supporting,
                        )
                    } else {
                        Column {
                            lines.forEachIndexed { index, line ->
                                Text(
                                    text = line,
                                    modifier = Modifier.padding(vertical = AppSpacing.XSmall),
                                    color = if (line.isErrorLine) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = AppType.Supporting,
                                    lineHeight = AppType.SupportingLine,
                                )
                                if (index != lines.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun String.matches(filter: LogFilter): Boolean {
        val lower = lowercase()
        return when (filter) {
            LogFilter.ALL -> true
            LogFilter.ERRORS -> isErrorLine
            LogFilter.ECARX -> "ecarx" in lower || "adaptapi" in lower
            LogFilter.VHAL -> "vhal" in lower
            LogFilter.SYSTEM -> "ecarx" !in lower && "adaptapi" !in lower && "vhal" !in lower
        }
    }

    private val String.isErrorLine: Boolean
        get() {
            val lower = lowercase()
            return listOf("error", "failed", "exception", "denied", "ошиб").any(lower::contains)
        }
}
