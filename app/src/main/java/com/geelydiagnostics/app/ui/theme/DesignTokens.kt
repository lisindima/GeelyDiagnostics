package com.geelydiagnostics.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Layout spacing follows a 4 dp baseline grid. Prefer semantic aliases at call sites
 * when a value has a stable role (for example [CardContent] or [Technical]).
 */
internal object AppSpacing {
    val None = 0.dp
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Default = 16.dp
    val Large = 20.dp
    val XLarge = 24.dp
    val XXLarge = 28.dp
    val XXXLarge = 32.dp
    val Huge = 40.dp

    val Technical = Small
    val CardContent = Default
    val ScreenHorizontal = XLarge
    val ScreenTop = Default
    val ScreenBottom = XXXLarge
}

/** Fixed component and drawing dimensions; these are not layout spacing. */
internal object AppSizes {
    val Border = 1.dp
    val CardElevation = 1.dp
    val DialogElevation = 8.dp
    val SettingsDialogMaxWidth = 560.dp
    val ChartHeight = 300.dp
    val ChartLine = 4.dp
    val ChartPoint = 8.dp
    val ChartGridLine = 1.dp
}

internal object AppBreakpoints {
    val TwoColumns = 900.dp
}

/** Semantic type scale. Font sizes are independent from the 4 dp layout grid. */
internal object AppType {
    val Technical = 11.sp
    val Label = 12.sp
    val Supporting = 13.sp
    val Body = 14.sp
    val BodyEmphasis = 15.sp
    val Standard = 16.sp
    val Action = 17.sp
    val CardTitle = 18.sp
    val Count = 28.sp
    val SectionTitle = 22.sp
    val DialogTitle = 26.sp
    val HeaderTitle = 28.sp
    val DialogFavorite = 30.sp
    val DialogClose = 34.sp
    val CardValue = 38.sp
    val DialogValue = 58.sp

    val ValueLabelLine = 17.sp
    val SupportingLine = 18.sp
    val BodyLine = 19.sp
    val CardTitleLine = 24.sp
    val DialogRawLine = 28.sp
    val DialogTitleLine = 32.sp
    val DialogCloseLine = 34.sp
    val CardValueLine = 44.sp
    val DialogValueLine = 66.sp
}

/** Material 3 shape scale; cards use the 12 dp medium radius. */
internal val GeelyDiagnosticsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
