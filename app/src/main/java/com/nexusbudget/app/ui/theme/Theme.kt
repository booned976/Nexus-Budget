package com.nexusbudget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C5CAB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE2FB),
    onPrimaryContainer = Color(0xFF0D366B),
    inversePrimary = Color(0xFF86B6EF),
    secondary = Color(0xFF256ABF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EEFB),
    onSecondaryContainer = Color(0xFF104281),
    tertiary = Color(0xFF127A55),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F2E6),
    onTertiaryContainer = Color(0xFF0B4A33),
    background = Color(0xFFF9F9F7),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFFCFCFB),
    onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFF0EFEC),
    onSurfaceVariant = Color(0xFF52514E),
    surfaceTint = Color(0xFF1C5CAB),
    inverseSurface = Color(0xFF2C2C2A),
    inverseOnSurface = Color(0xFFF9F9F7),
    error = Color(0xFFB42323),
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E4),
    onErrorContainer = Color(0xFF6B1111),
    outline = Color(0xFF898781),
    outlineVariant = Color(0xFFE1E0D9),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFCFCFB),
    surfaceDim = Color(0xFFE8E7E3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF4F4F1),
    surfaceContainerHigh = Color(0xFFEEEEEA),
    surfaceContainerHighest = Color(0xFFE8E7E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86B6EF),
    onPrimary = Color(0xFF0D366B),
    primaryContainer = Color(0xFF184F95),
    onPrimaryContainer = Color(0xFFCDE2FB),
    inversePrimary = Color(0xFF1C5CAB),
    secondary = Color(0xFF9EC5F4),
    onSecondary = Color(0xFF0D366B),
    secondaryContainer = Color(0xFF1F3552),
    onSecondaryContainer = Color(0xFFCDE2FB),
    tertiary = Color(0xFF5FD3A6),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF0E4A34),
    onTertiaryContainer = Color(0xFFD5F2E6),
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    surface = Color(0xFF1A1A19),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2A),
    onSurfaceVariant = Color(0xFFC3C2B7),
    surfaceTint = Color(0xFF86B6EF),
    inverseSurface = Color(0xFFF0EFEC),
    inverseOnSurface = Color(0xFF1A1A19),
    error = Color(0xFFF08A8A),
    onError = Color(0xFF4A0B0B),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFCE4E4),
    outline = Color(0xFF898781),
    outlineVariant = Color(0xFF383835),
    scrim = Color.Black,
    surfaceBright = Color(0xFF383835),
    surfaceDim = Color(0xFF0D0D0D),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF1A1A19),
    surfaceContainer = Color(0xFF212120),
    surfaceContainerHigh = Color(0xFF2A2A28),
    surfaceContainerHighest = Color(0xFF333331),
)

/**
 * Colors for charts and status, taken from a colorblind-validated palette. Status colors are
 * reserved for state (good / warning / serious / critical) and always shown with an icon and label.
 */
@Immutable
data class ChartColors(
    val series1: Color,
    val series2: Color,
    val series3: Color,
    val grid: Color,
    val baseline: Color,
    val surface: Color,
    val good: Color,
    val warning: Color,
    val serious: Color,
    val critical: Color,
    val positiveText: Color,
    val negativeText: Color,
    val track: Color,
)

private val LightChart = ChartColors(
    series1 = Color(0xFF2A78D6),
    series2 = Color(0xFFEB6834),
    series3 = Color(0xFF1BAF7A),
    grid = Color(0xFFE1E0D9),
    baseline = Color(0xFFC3C2B7),
    surface = Color(0xFFFFFFFF),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFFAB219),
    serious = Color(0xFFEC835A),
    critical = Color(0xFFD03B3B),
    positiveText = Color(0xFF006300),
    negativeText = Color(0xFFB42323),
    track = Color(0xFFE4EEFB),
)

private val DarkChart = ChartColors(
    series1 = Color(0xFF3987E5),
    series2 = Color(0xFFD95926),
    series3 = Color(0xFF199E70),
    grid = Color(0xFF2C2C2A),
    baseline = Color(0xFF383835),
    surface = Color(0xFF1A1A19),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFFAB219),
    serious = Color(0xFFEC835A),
    critical = Color(0xFFD03B3B),
    positiveText = Color(0xFF3FC23F),
    negativeText = Color(0xFFF08A8A),
    track = Color(0xFF1F3552),
)

val LocalChartColors = staticCompositionLocalOf { LightChart }

/** When true, every amount is masked (privacy mode). */
val LocalHideAmounts = staticCompositionLocalOf { false }

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Large "hero" figure style, used once per screen. */
val HeroNumber = TextStyle(fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)

@Composable
fun NexusTheme(hideAmounts: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalChartColors provides if (dark) DarkChart else LightChart,
        LocalHideAmounts provides hideAmounts,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
