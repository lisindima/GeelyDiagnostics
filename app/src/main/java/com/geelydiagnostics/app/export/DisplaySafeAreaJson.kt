package com.geelydiagnostics.app.export

import com.geelydiagnostics.app.ui.display.DisplaySafeAreaState
import com.geelydiagnostics.app.ui.display.EdgeInsetsPx
import com.geelydiagnostics.app.ui.display.PixelBounds
import org.json.JSONObject

internal fun DisplaySafeAreaState.toJson() = JSONObject().apply {
    put("displayId", display.displayId ?: JSONObject.NULL)
    put("physicalWidthPx", display.physicalWidthPx)
    put("physicalHeightPx", display.physicalHeightPx)
    put("windowWidthPx", display.windowWidthPx)
    put("windowHeightPx", display.windowHeightPx)
    put("widthDp", display.widthDp.jsonNumber())
    put("heightDp", display.heightDp.jsonNumber())
    put("density", display.density.jsonNumber())
    put("densityDpi", display.densityDpi)
    put("currentWindowMetrics", display.currentWindowBounds.toJson())
    put("maximumWindowMetrics", display.maximumWindowBounds.toJson())
    put("decorViewWidthPx", display.decorViewWidthPx)
    put("decorViewHeightPx", display.decorViewHeightPx)
    put("rootViewWidthPx", display.rootViewWidthPx)
    put("rootViewHeightPx", display.rootViewHeightPx)
    put("visibleDisplayFrame", display.visibleDisplayFrame.toJson())
    put("insets", JSONObject().apply {
        insets.namedInsets().forEach { (name, value) ->
            put(name, value.toJson(display.density))
        }
    })
    put("calculatedSafeArea", JSONObject().apply {
        put("leftPx", safeArea.leftPx)
        put("topPx", safeArea.topPx)
        put("rightPx", safeArea.rightPx)
        put("bottomPx", safeArea.bottomPx)
        put("bottomSource", bottomSource.name)
        put("systemBottomPx", systemBottomPx)
        put("oemBottomPx", oemBottomPx)
        put("mode", mode.name)
        put("oemProfile", oemProfile ?: JSONObject.NULL)
    })
}

private fun EdgeInsetsPx.toJson(density: Float) = JSONObject().apply {
    put("leftPx", left)
    put("topPx", top)
    put("rightPx", right)
    put("bottomPx", bottom)
    put("leftDp", left.toDp(density))
    put("topDp", top.toDp(density))
    put("rightDp", right.toDp(density))
    put("bottomDp", bottom.toDp(density))
}

private fun PixelBounds.toJson() = JSONObject().apply {
    put("left", left)
    put("top", top)
    put("right", right)
    put("bottom", bottom)
    put("width", width)
    put("height", height)
}

private fun Int.toDp(density: Float): Double = this / density.coerceAtLeast(0.01f).toDouble()
private fun Float.jsonNumber(): Any = if (isFinite()) this else toString()
