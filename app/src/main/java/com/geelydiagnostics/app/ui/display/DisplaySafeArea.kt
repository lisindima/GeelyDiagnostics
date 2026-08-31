package com.geelydiagnostics.app.ui.display

data class DisplaySafeArea(
    val leftPx: Int = 0,
    val topPx: Int = 0,
    val rightPx: Int = 0,
    val bottomPx: Int = 0,
)

enum class SafeAreaSource {
    NAVIGATION_BARS,
    SYSTEM_BARS,
    SAFE_DRAWING,
    SAFE_CONTENT,
    TAPPABLE_ELEMENT,
    SYSTEM_GESTURES,
    MANDATORY_SYSTEM_GESTURES,
    OEM_OVERRIDE,
    NONE,
}

enum class DisplaySafeAreaMode(val title: String) {
    AUTO("Авто"),
    SYSTEM_ONLY("Только System"),
    OEM_OVERRIDE("OEM override"),
}

data class EdgeInsetsPx(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

data class WindowInsetsSnapshot(
    val statusBars: EdgeInsetsPx = EdgeInsetsPx(),
    val navigationBars: EdgeInsetsPx = EdgeInsetsPx(),
    val systemBars: EdgeInsetsPx = EdgeInsetsPx(),
    val safeDrawing: EdgeInsetsPx = EdgeInsetsPx(),
    val safeContent: EdgeInsetsPx = EdgeInsetsPx(),
    val tappableElement: EdgeInsetsPx = EdgeInsetsPx(),
    val systemGestures: EdgeInsetsPx = EdgeInsetsPx(),
    val mandatorySystemGestures: EdgeInsetsPx = EdgeInsetsPx(),
    val ime: EdgeInsetsPx = EdgeInsetsPx(),
) {
    fun persistentInsets(): List<Pair<SafeAreaSource, EdgeInsetsPx>> = listOf(
        SafeAreaSource.NAVIGATION_BARS to navigationBars,
        SafeAreaSource.SYSTEM_BARS to systemBars,
        SafeAreaSource.SAFE_DRAWING to safeDrawing,
        SafeAreaSource.SAFE_CONTENT to safeContent,
        SafeAreaSource.TAPPABLE_ELEMENT to tappableElement,
        SafeAreaSource.SYSTEM_GESTURES to systemGestures,
        SafeAreaSource.MANDATORY_SYSTEM_GESTURES to mandatorySystemGestures,
    )

    fun namedInsets(): List<Pair<String, EdgeInsetsPx>> = listOf(
        "statusBars" to statusBars,
        "navigationBars" to navigationBars,
        "systemBars" to systemBars,
        "safeDrawing" to safeDrawing,
        "safeContent" to safeContent,
        "tappableElement" to tappableElement,
        "systemGestures" to systemGestures,
        "mandatorySystemGestures" to mandatorySystemGestures,
        "ime" to ime,
    )
}

data class PixelBounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

data class DisplayMetricsSnapshot(
    val displayId: Int? = null,
    val physicalWidthPx: Int = 0,
    val physicalHeightPx: Int = 0,
    val windowWidthPx: Int = 0,
    val windowHeightPx: Int = 0,
    val density: Float = 1f,
    val densityDpi: Int = 160,
    val currentWindowBounds: PixelBounds = PixelBounds(),
    val maximumWindowBounds: PixelBounds = PixelBounds(),
    val decorViewWidthPx: Int = 0,
    val decorViewHeightPx: Int = 0,
    val rootViewWidthPx: Int = 0,
    val rootViewHeightPx: Int = 0,
    val visibleDisplayFrame: PixelBounds = PixelBounds(),
) {
    val widthDp: Float get() = windowWidthPx / density.coerceAtLeast(0.01f)
    val heightDp: Float get() = windowHeightPx / density.coerceAtLeast(0.01f)
}

data class OemDisplayAreaConfig(
    val profile: String,
    val displayWidthPx: Int? = null,
    val displayHeightPx: Int? = null,
    val reservedLeftPx: Int = 0,
    val reservedTopPx: Int = 0,
    val reservedRightPx: Int = 0,
    val reservedBottomPx: Int = 0,
)

internal object OemDisplayAreaConfigs {
    fun forProfile(profile: String): OemDisplayAreaConfig? = when (profile) {
        "G426" -> OemDisplayAreaConfig(
            profile = profile,
            // Unknown until runtime validation on Cityray. Do not guess.
            reservedBottomPx = 0,
        )
        else -> null
    }
}

data class DisplaySafeAreaState(
    val safeArea: DisplaySafeArea = DisplaySafeArea(),
    val bottomSource: SafeAreaSource = SafeAreaSource.NONE,
    val mode: DisplaySafeAreaMode = DisplaySafeAreaMode.AUTO,
    val systemBottomPx: Int = 0,
    val oemBottomPx: Int = 0,
    val oemProfile: String? = null,
    val insets: WindowInsetsSnapshot = WindowInsetsSnapshot(),
    val display: DisplayMetricsSnapshot = DisplayMetricsSnapshot(),
)
