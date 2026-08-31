package com.geelydiagnostics.app.ui.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DisplaySafeAreaProvider {
    val safeArea: StateFlow<DisplaySafeAreaState>
}

internal class MutableDisplaySafeAreaProvider(
    private val log: (String) -> Unit = {},
) : DisplaySafeAreaProvider {
    private val mutableSafeArea = MutableStateFlow(DisplaySafeAreaState())
    override val safeArea: StateFlow<DisplaySafeAreaState> = mutableSafeArea.asStateFlow()

    fun update(
        insets: WindowInsetsSnapshot,
        display: DisplayMetricsSnapshot,
        mode: DisplaySafeAreaMode,
        oemConfig: OemDisplayAreaConfig?,
        manualBottomPx: Int,
    ) {
        val updated = DisplaySafeAreaPolicy.calculate(
            insets = insets,
            mode = mode,
            oemConfig = oemConfig,
            manualBottomPx = manualBottomPx,
            display = display,
        )
        if (updated == mutableSafeArea.value) return
        mutableSafeArea.value = updated
        log(updated.logMessage())
    }
}

private fun DisplaySafeAreaState.logMessage(): String = buildString {
    append("[DisplaySafeArea] ")
    append("displayId=${display.displayId ?: "unknown"} ")
    append("window=${display.windowWidthPx}x${display.windowHeightPx} ")
    append("navigationBars.bottom=${insets.navigationBars.bottom} ")
    append("systemBars.bottom=${insets.systemBars.bottom} ")
    append("safeDrawing.bottom=${insets.safeDrawing.bottom} ")
    append("safeContent.bottom=${insets.safeContent.bottom} ")
    append("tappableElement.bottom=${insets.tappableElement.bottom} ")
    append("systemGestures.bottom=${insets.systemGestures.bottom} ")
    append("mandatorySystemGestures.bottom=${insets.mandatorySystemGestures.bottom} ")
    append("OEM.bottom=$oemBottomPx ")
    append("selected.bottom=${safeArea.bottomPx} ")
    append("selected.source=$bottomSource")
}
