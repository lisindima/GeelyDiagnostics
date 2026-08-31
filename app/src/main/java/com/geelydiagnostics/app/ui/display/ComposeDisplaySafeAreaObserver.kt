package com.geelydiagnostics.app.ui.display

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Composable
internal fun ObserveDisplaySafeArea(
    provider: MutableDisplaySafeAreaProvider,
    mode: DisplaySafeAreaMode,
    manualBottomPx: Int,
    oemProfile: String,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    @Suppress("UNUSED_VARIABLE")
    val configuration = LocalConfiguration.current
    var layoutVersion by remember(view) { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutVersion++
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }

    val insets = WindowInsetsSnapshot(
        statusBars = WindowInsets.statusBars.snapshot(density, layoutDirection),
        navigationBars = WindowInsets.navigationBars.snapshot(density, layoutDirection),
        systemBars = WindowInsets.systemBars.snapshot(density, layoutDirection),
        safeDrawing = WindowInsets.safeDrawing.snapshot(density, layoutDirection),
        safeContent = WindowInsets.safeContent.snapshot(density, layoutDirection),
        tappableElement = WindowInsets.tappableElement.snapshot(density, layoutDirection),
        systemGestures = WindowInsets.systemGestures.snapshot(density, layoutDirection),
        mandatorySystemGestures = WindowInsets.mandatorySystemGestures.snapshot(
            density,
            layoutDirection,
        ),
        ime = WindowInsets.ime.snapshot(density, layoutDirection),
    )
    val activity = remember(context) { context.findActivity() }
    val display = displayMetrics(activity, view, layoutVersion)
    val oemConfig = OemDisplayAreaConfigs.forProfile(oemProfile)

    SideEffect {
        provider.update(
            insets = insets,
            display = display,
            mode = mode,
            oemConfig = oemConfig,
            manualBottomPx = manualBottomPx,
        )
    }
}

private fun WindowInsets.snapshot(
    density: Density,
    layoutDirection: LayoutDirection,
): EdgeInsetsPx = EdgeInsetsPx(
    left = getLeft(density, layoutDirection),
    top = getTop(density),
    right = getRight(density, layoutDirection),
    bottom = getBottom(density),
)

@Suppress("DEPRECATION")
private fun displayMetrics(
    activity: Activity?,
    view: View,
    layoutVersion: Int,
): DisplayMetricsSnapshot {
    // layoutVersion deliberately participates in recomposition of this snapshot.
    @Suppress("UNUSED_VARIABLE")
    val currentLayoutVersion = layoutVersion
    val resourceMetrics = view.resources.displayMetrics
    val display = activity?.display ?: view.display
    val realMetrics = DisplayMetrics()
    display?.getRealMetrics(realMetrics)
    val currentBounds = activity?.windowManager?.currentWindowMetrics?.bounds
        ?: Rect(0, 0, view.width, view.height)
    val maximumBounds = activity?.windowManager?.maximumWindowMetrics?.bounds
        ?: currentBounds
    val visibleFrame = Rect().also(view::getWindowVisibleDisplayFrame)
    val decor = activity?.window?.decorView
    val root = view.rootView
    return DisplayMetricsSnapshot(
        displayId = display?.displayId,
        physicalWidthPx = realMetrics.widthPixels,
        physicalHeightPx = realMetrics.heightPixels,
        windowWidthPx = currentBounds.width(),
        windowHeightPx = currentBounds.height(),
        density = resourceMetrics.density,
        densityDpi = resourceMetrics.densityDpi,
        currentWindowBounds = currentBounds.toPixelBounds(),
        maximumWindowBounds = maximumBounds.toPixelBounds(),
        decorViewWidthPx = decor?.width ?: 0,
        decorViewHeightPx = decor?.height ?: 0,
        rootViewWidthPx = root.width,
        rootViewHeightPx = root.height,
        visibleDisplayFrame = visibleFrame.toPixelBounds(),
    )
}

private fun Rect.toPixelBounds() = PixelBounds(left, top, right, bottom)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
