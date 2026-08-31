package com.geelydiagnostics.app.ui.display

internal object DisplaySafeAreaPolicy {
    fun calculate(
        insets: WindowInsetsSnapshot,
        mode: DisplaySafeAreaMode,
        oemConfig: OemDisplayAreaConfig?,
        manualBottomPx: Int,
        display: DisplayMetricsSnapshot = DisplayMetricsSnapshot(),
    ): DisplaySafeAreaState {
        val candidates = insets.persistentInsets()
        val (systemSource, systemBottom) = candidates.fold(
            SafeAreaSource.NONE to 0,
        ) { selected, candidate ->
            // Strict comparison intentionally keeps the first source on equal values.
            if (candidate.second.bottom > selected.second) {
                candidate.first to candidate.second.bottom
            } else {
                selected
            }
        }
        val configuredOem = oemConfig?.reservedBottomPx?.coerceAtLeast(0) ?: 0
        val selected = when (mode) {
            DisplaySafeAreaMode.SYSTEM_ONLY -> systemSource to systemBottom
            DisplaySafeAreaMode.AUTO -> if (systemBottom > 0) {
                systemSource to systemBottom
            } else if (configuredOem > 0) {
                SafeAreaSource.OEM_OVERRIDE to configuredOem
            } else {
                SafeAreaSource.NONE to 0
            }
            DisplaySafeAreaMode.OEM_OVERRIDE -> manualBottomPx.coerceAtLeast(0).let { bottom ->
                if (bottom > 0) SafeAreaSource.OEM_OVERRIDE to bottom
                else SafeAreaSource.NONE to 0
            }
        }
        val persistentValues = candidates.map { it.second }
        val maxHorizontal = display.windowWidthPx.takeIf { it > 0 } ?: Int.MAX_VALUE
        val maxVertical = display.windowHeightPx.takeIf { it > 0 } ?: Int.MAX_VALUE
        return DisplaySafeAreaState(
            safeArea = DisplaySafeArea(
                leftPx = (persistentValues.maxOfOrNull(EdgeInsetsPx::left) ?: 0)
                    .coerceIn(0, maxHorizontal),
                topPx = (persistentValues.maxOfOrNull(EdgeInsetsPx::top) ?: 0)
                    .coerceIn(0, maxVertical),
                rightPx = (persistentValues.maxOfOrNull(EdgeInsetsPx::right) ?: 0)
                    .coerceIn(0, maxHorizontal),
                bottomPx = selected.second.coerceIn(0, maxVertical),
            ),
            bottomSource = selected.first,
            mode = mode,
            systemBottomPx = systemBottom,
            oemBottomPx = when (mode) {
                DisplaySafeAreaMode.OEM_OVERRIDE -> manualBottomPx.coerceAtLeast(0)
                else -> configuredOem
            },
            oemProfile = oemConfig?.profile,
            insets = insets,
            display = display,
        )
    }
}
