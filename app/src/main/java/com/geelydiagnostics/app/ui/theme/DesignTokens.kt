package com.geelydiagnostics.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette used by the approved UI mockup. Components consume semantic Material roles. */
internal val GeelyDiagnosticsLightColors = lightColorScheme(
    primary = Color(0xFF1F5FAE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF0D2E5D),
    secondary = Color(0xFF40683E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E8D4),
    onSecondaryContainer = Color(0xFF18391D),
    tertiary = Color(0xFF755B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF2A2100),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEFF1F6),
    onSurfaceVariant = Color(0xFF626872),
    outline = Color(0xFF777D88),
    outlineVariant = Color(0xFFD0D4DC),
    scrim = Color(0xFF000000),
)

internal val GeelyDiagnosticsDarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF062E63),
    primaryContainer = Color(0xFF304566),
    onPrimaryContainer = Color(0xFFD8E5FF),
    secondary = Color(0xFFC0EFC6),
    onSecondary = Color(0xFF18391D),
    secondaryContainer = Color(0xFF244C31),
    onSecondaryContainer = Color(0xFFC0EFC6),
    tertiary = Color(0xFFF5C746),
    onTertiary = Color(0xFF3D2F00),
    tertiaryContainer = Color(0xFF554300),
    onTertiaryContainer = Color(0xFFFFE08A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C21),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF292C32),
    onSurfaceVariant = Color(0xFFAEB4C0),
    outline = Color(0xFF8D929D),
    outlineVariant = Color(0xFF454A55),
    scrim = Color(0xFF000000),
)

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
    val ScreenHorizontal = Large
    val ScreenTop = Default
    val ScreenBottom = XXXLarge
}

/** Fixed component and drawing dimensions; these are not layout spacing. */
internal object AppSizes {
    val Border = 1.dp
    val CardElevation = 0.dp
    val DialogElevation = 8.dp
    val HeaderActionMinHeight = 40.dp
    val FavoriteButton = 36.dp
    val FilterChipMinHeight = 44.dp
    val SearchFieldMinHeight = 52.dp
    val SettingsDialogMaxWidth = 560.dp
    val ChartHeight = 300.dp
    val ChartLine = 4.dp
    val ChartPoint = 8.dp
    val ChartGridLine = 1.dp
}

internal object AppBreakpoints {
    val TwoColumns = 900.dp
    val HeaderInlineActions = 1200.dp
}

/** Semantic type scale. Font sizes are independent from the 4 dp layout grid. */
internal object AppType {
    val Technical = 12.sp
    val Label = 13.sp
    val Supporting = 15.sp
    val Body = 16.sp
    val BodyEmphasis = 17.sp
    val Standard = 18.sp
    val Action = 19.sp
    val CardTitle = 21.sp
    val Count = 34.sp
    val SectionTitle = 26.sp
    val DialogTitle = 30.sp
    val HeaderTitle = 32.sp
    val DialogFavorite = 34.sp
    val DialogClose = 38.sp
    val CardValue = 42.sp
    val DialogValue = 64.sp

    val ValueLabelLine = 20.sp
    val SupportingLine = 20.sp
    val BodyLine = 22.sp
    val CardTitleLine = 27.sp
    val DialogRawLine = 32.sp
    val DialogTitleLine = 38.sp
    val DialogCloseLine = 40.sp
    val CardValueLine = 50.sp
    val DialogValueLine = 72.sp
}

/** Material 3 shape scale; cards use the 12 dp medium radius. */
internal val GeelyDiagnosticsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
