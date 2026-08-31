package com.geelydiagnostics.app.ui.display

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geelydiagnostics.app.ui.components.CatalogFilterRow
import com.geelydiagnostics.app.ui.theme.AppSizes
import com.geelydiagnostics.app.ui.theme.AppSpacing
import com.geelydiagnostics.app.ui.theme.AppType
import java.util.Locale

@Composable
internal fun DisplayInsetsDebugDialog(
    state: DisplaySafeAreaState,
    manualBottomPx: Int,
    showOverlay: Boolean,
    onModeSelected: (DisplaySafeAreaMode) -> Unit,
    onManualBottomChanged: (Int) -> Unit,
    onOverlayChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .padding(AppSpacing.XLarge)
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.94f),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = AppSizes.DialogElevation,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.XLarge),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Large),
            ) {
                DialogHeader(onDismiss)
                DisplaySection(state.display)
                InsetsSection(state)
                CalculatedSafeAreaSection(state)
                SafeAreaControls(
                    mode = state.mode,
                    manualBottomPx = manualBottomPx,
                    showOverlay = showOverlay,
                    onModeSelected = onModeSelected,
                    onManualBottomChanged = onManualBottomChanged,
                    onOverlayChanged = onOverlayChanged,
                )
                ValidationSection()
            }
        }
    }
}

@Composable
private fun DialogHeader(onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Display / Insets Debug",
                fontSize = AppType.DialogTitle,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Измерение системной и OEM safe area в реальном времени",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = AppType.Supporting,
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { contentDescription = "Закрыть" },
        ) {
            Text("×", fontSize = AppType.DialogClose)
        }
    }
}

@Composable
private fun DisplaySection(display: DisplayMetricsSnapshot) = DebugCard("DISPLAY") {
    DebugValue("Display ID", display.displayId?.toString() ?: "unknown")
    DebugValue("Physical", "${display.physicalWidthPx} × ${display.physicalHeightPx} px")
    DebugValue("Window", "${display.windowWidthPx} × ${display.windowHeightPx} px")
    DebugValue(
        "Window dp",
        "${display.widthDp.oneDecimal()} × ${display.heightDp.oneDecimal()} dp",
    )
    DebugValue("Density", "${display.density.twoDecimals()} · ${display.densityDpi} dpi")
    DebugValue("Current metrics", display.currentWindowBounds.formatted())
    DebugValue("Maximum metrics", display.maximumWindowBounds.formatted())
    DebugValue("Decor view", "${display.decorViewWidthPx} × ${display.decorViewHeightPx} px")
    DebugValue("Root view", "${display.rootViewWidthPx} × ${display.rootViewHeightPx} px")
    DebugValue("Visible frame", display.visibleDisplayFrame.formatted())
}

@Composable
private fun InsetsSection(state: DisplaySafeAreaState) = DebugCard("WINDOW INSETS") {
    state.insets.namedInsets().forEach { (name, insets) ->
        InsetsValue(name, insets, state.display.density)
    }
    Text(
        text = "IME показывается для диагностики, но не входит в постоянную safe area.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = AppType.Technical,
    )
}

@Composable
private fun CalculatedSafeAreaSection(state: DisplaySafeAreaState) =
    DebugCard("CALCULATED SAFE AREA") {
        InsetsValue(
            name = "selected",
            insets = EdgeInsetsPx(
                left = state.safeArea.leftPx,
                top = state.safeArea.topPx,
                right = state.safeArea.rightPx,
                bottom = state.safeArea.bottomPx,
            ),
            density = state.display.density,
        )
        DebugValue("Bottom source", state.bottomSource.name)
        DebugValue("System bottom", "${state.systemBottomPx} px")
        DebugValue("OEM bottom", "${state.oemBottomPx} px")
        DebugValue("OEM profile", state.oemProfile ?: "none")
    }

@Composable
private fun SafeAreaControls(
    mode: DisplaySafeAreaMode,
    manualBottomPx: Int,
    showOverlay: Boolean,
    onModeSelected: (DisplaySafeAreaMode) -> Unit,
    onManualBottomChanged: (Int) -> Unit,
    onOverlayChanged: (Boolean) -> Unit,
) = DebugCard("DISPLAY SAFE AREA") {
    Text(
        text = "Mode",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = AppType.Label,
        fontWeight = FontWeight.Bold,
    )
    CatalogFilterRow(
        labels = DisplaySafeAreaMode.entries.map(DisplaySafeAreaMode::title),
        selectedIndex = mode.ordinal,
        onSelected = { onModeSelected(DisplaySafeAreaMode.entries[it]) },
    )
    var manualText by rememberSaveable(manualBottomPx) {
        mutableStateOf(manualBottomPx.toString())
    }
    OutlinedTextField(
        value = manualText,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(6)
            manualText = digits
            onManualBottomChanged(digits.toIntOrNull() ?: 0)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = mode == DisplaySafeAreaMode.OEM_OVERRIDE,
        label = { Text("OEM bottom inset, px") },
        supportingText = {
            Text("G426 production fallback пока равен 0 px: высота не угадана.")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Show Safe Area Overlay",
                fontSize = AppType.BodyEmphasis,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Закройте окно после включения: граница останется поверх основного UI.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = AppType.Technical,
            )
        }
        Switch(checked = showOverlay, onCheckedChange = onOverlayChanged)
    }
}

@Composable
private fun ValidationSection() = DebugCard("CLIMATE OVERLAY VALIDATION") {
    Text(
        text = "1. Оставьте OEM climate panel видимой.\n" +
            "2. Сверьте calculated bottom с её верхней границей.\n" +
            "3. Сделайте screenshot и экспортируйте JSON.\n" +
            "4. Включите overlay и закройте это окно.\n" +
            "5. Если system bottom = 0, выберите OEM override и подберите px вручную.",
        fontSize = AppType.Supporting,
        lineHeight = AppType.BodyLine,
    )
}

@Composable
private fun DebugCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(AppSizes.Border, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Small),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = AppType.Label,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
            )
            content()
        }
    }
}

@Composable
private fun DebugValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = AppType.Supporting,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            fontSize = AppType.Supporting,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun InsetsValue(name: String, insets: EdgeInsetsPx, density: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall)) {
        Text(name, fontSize = AppType.Body, fontWeight = FontWeight.Bold)
        Text(
            text = listOf(
                "L ${insets.left.unitPair(density)}",
                "T ${insets.top.unitPair(density)}",
                "R ${insets.right.unitPair(density)}",
                "B ${insets.bottom.unitPair(density)}",
            ).joinToString("  ·  "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = AppType.Technical,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
internal fun DisplaySafeAreaOverlay(state: DisplaySafeAreaState) {
    val safe = state.safeArea
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val left = safe.leftPx.coerceIn(0, size.width.toInt()).toFloat()
            val top = safe.topPx.coerceIn(0, size.height.toInt()).toFloat()
            val right = safe.rightPx.coerceIn(0, size.width.toInt()).toFloat()
            val bottom = safe.bottomPx.coerceIn(0, size.height.toInt()).toFloat()
            val contentWidth = (size.width - left - right).coerceAtLeast(0f)
            val contentHeight = (size.height - top - bottom).coerceAtLeast(0f)
            drawRect(
                color = Color(0xFF35C46A).copy(alpha = 0.12f),
                topLeft = Offset(left, top),
                size = Size(contentWidth, contentHeight),
            )
            if (bottom > 0f) {
                drawRect(
                    color = Color(0xFFE14B4B).copy(alpha = 0.3f),
                    topLeft = Offset(0f, size.height - bottom),
                    size = Size(size.width, bottom),
                )
                drawLine(
                    color = Color(0xFFFFC107),
                    start = Offset(0f, size.height - bottom),
                    end = Offset(size.width, size.height - bottom),
                    strokeWidth = with(density) { 3.dp.toPx() },
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { safe.bottomPx.toDp() } + AppSpacing.XSmall),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = "SAFE BOTTOM ${safe.bottomPx}px · ${state.bottomSource}",
                modifier = Modifier.padding(horizontal = AppSpacing.Small, vertical = AppSpacing.XSmall),
                fontSize = AppType.Technical,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun PixelBounds.formatted(): String =
    "[$left,$top — $right,$bottom] · ${width}×${height} px"

private fun Int.unitPair(density: Float): String =
    "$this px / ${(this / density.coerceAtLeast(0.01f)).oneDecimal()} dp"

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
private fun Float.twoDecimals(): String = String.format(Locale.US, "%.2f", this)
