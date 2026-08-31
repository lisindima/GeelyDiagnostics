package com.geelydiagnostics.app.ui.display

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplaySafeAreaPolicyTest {
    @Test
    fun systemInsetWinsWithoutAddingOemFallback() {
        val result = calculate(
            insets = WindowInsetsSnapshot(navigationBars = EdgeInsetsPx(bottom = 100)),
            oemBottom = 150,
        )

        assertEquals(100, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.NAVIGATION_BARS, result.bottomSource)
    }

    @Test
    fun tappableElementCanProvideClimateSafeArea() {
        val result = calculate(
            insets = WindowInsetsSnapshot(tappableElement = EdgeInsetsPx(bottom = 120)),
        )

        assertEquals(120, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.TAPPABLE_ELEMENT, result.bottomSource)
    }

    @Test
    fun oemValueIsFallbackWhenAllSystemInsetsAreZero() {
        val result = calculate(oemBottom = 120)

        assertEquals(120, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.OEM_OVERRIDE, result.bottomSource)
    }

    @Test
    fun noInformationProducesZeroAndNone() {
        val result = calculate()

        assertEquals(0, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.NONE, result.bottomSource)
    }

    @Test
    fun maximumMeaningfulSystemInsetIsSelected() {
        val result = calculate(
            insets = WindowInsetsSnapshot(
                navigationBars = EdgeInsetsPx(bottom = 80),
                safeDrawing = EdgeInsetsPx(bottom = 80),
                tappableElement = EdgeInsetsPx(bottom = 120),
            ),
        )

        assertEquals(120, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.TAPPABLE_ELEMENT, result.bottomSource)
    }

    @Test
    fun equalValuesUseDeterministicSourcePriority() {
        val result = calculate(
            insets = WindowInsetsSnapshot(
                navigationBars = EdgeInsetsPx(bottom = 120),
                safeDrawing = EdgeInsetsPx(bottom = 120),
                tappableElement = EdgeInsetsPx(bottom = 120),
            ),
        )

        assertEquals(SafeAreaSource.NAVIGATION_BARS, result.bottomSource)
    }

    @Test
    fun imeIsNeverPartOfPersistentSafeArea() {
        val result = calculate(
            insets = WindowInsetsSnapshot(ime = EdgeInsetsPx(bottom = 600)),
        )

        assertEquals(0, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.NONE, result.bottomSource)
    }

    @Test
    fun systemOnlyIgnoresOemFallback() {
        val result = DisplaySafeAreaPolicy.calculate(
            insets = WindowInsetsSnapshot(),
            mode = DisplaySafeAreaMode.SYSTEM_ONLY,
            oemConfig = OemDisplayAreaConfig(profile = "TEST", reservedBottomPx = 120),
            manualBottomPx = 0,
        )

        assertEquals(0, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.NONE, result.bottomSource)
    }

    @Test
    fun manualModeUsesManualOemValue() {
        val result = DisplaySafeAreaPolicy.calculate(
            insets = WindowInsetsSnapshot(navigationBars = EdgeInsetsPx(bottom = 80)),
            mode = DisplaySafeAreaMode.OEM_OVERRIDE,
            oemConfig = OemDisplayAreaConfig(profile = "G426", reservedBottomPx = 0),
            manualBottomPx = 144,
        )

        assertEquals(144, result.safeArea.bottomPx)
        assertEquals(SafeAreaSource.OEM_OVERRIDE, result.bottomSource)
    }

    @Test
    fun g426DoesNotContainGuessedFallbackHeight() {
        val config = OemDisplayAreaConfigs.forProfile("G426")

        assertEquals(0, config?.reservedBottomPx)
    }

    @Test
    fun providerLogsOnlyWhenMeasuredStateChanges() {
        val logs = mutableListOf<String>()
        val provider = MutableDisplaySafeAreaProvider(logs::add)
        repeat(2) {
            provider.update(
                insets = WindowInsetsSnapshot(tappableElement = EdgeInsetsPx(bottom = 120)),
                display = DisplayMetricsSnapshot(windowWidthPx = 1440, windowHeightPx = 1920),
                mode = DisplaySafeAreaMode.AUTO,
                oemConfig = OemDisplayAreaConfigs.forProfile("G426"),
                manualBottomPx = 0,
            )
        }

        assertEquals(1, logs.size)
        assertEquals(120, provider.safeArea.value.safeArea.bottomPx)
    }

    private fun calculate(
        insets: WindowInsetsSnapshot = WindowInsetsSnapshot(),
        oemBottom: Int = 0,
    ) = DisplaySafeAreaPolicy.calculate(
        insets = insets,
        mode = DisplaySafeAreaMode.AUTO,
        oemConfig = OemDisplayAreaConfig(profile = "TEST", reservedBottomPx = oemBottom),
        manualBottomPx = 0,
    )
}
