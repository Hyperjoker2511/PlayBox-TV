package com.playbox.tv

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- ZEPH — Material 3 Expressive design system (1.7.0) ----------
// FULL Material 3: the real M3 tonal colour system, the real M3
// components (see M3Components.kt), the M3 shape and type scales.
// The 1.6.0 look failed because it kept Nova's identity — near-black
// background, one violet accent, one hue everywhere. This scheme is
// a NEW identity: a teal seed with DISTINCT hues — teal primary,
// sage secondary, warm-peach tertiary — and stepped tonal surfaces
// so cards, sheets and rails read as M3 elevation, not flat black.
// The seed is one value to change if a different colour is wanted.
// Motion stays transform + opacity only, 60fps, no live blur.

/** The Zeph colour scheme: M3 expressive dark, teal seed.
 * Tonal values follow the Material 3 colour system for a teal seed
 * (#006A6A): primary is the bright teal 80, containers are the
 * deep teal 30, secondary is sage, tertiary is warm peach. */
internal val ZephColorScheme = darkColorScheme(
    primary = Color(0xFF4DD0C4),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFF7DF1E4),
    secondary = Color(0xFFB0CCC6),
    onSecondary = Color(0xFF1D3531),
    secondaryContainer = Color(0xFF324B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFE8B48C),
    onTertiary = Color(0xFF422E22),
    tertiaryContainer = Color(0xFF5C4033),
    onTertiaryContainer = Color(0xFFFFDCC6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF262D2B),
    surfaceContainerHighest = Color(0xFF313836),
    inverseSurface = Color(0xFFE0E3E1),
    inverseOnSurface = Color(0xFF191C1A),
    inversePrimary = Color(0xFF006A6A),
    scrim = Color.Black
)

/** The Zeph type scale: Plus Jakarta Sans throughout, expressive
 * sizes — big bold display and headline type for the TV, clear
 * body and label sizes for the phone. */
internal val ZephTypography = Typography(
    displayLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp)
)

/** The Zeph shape scale: M3 Expressive — large, soft, rounded.
 * Buttons and chips are full (pill), cards are extraLarge. */
internal val ZephShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

/** The app theme. Wrap the whole app in this — it is the ONLY
 * MaterialTheme in the design. */
@Composable
internal fun ZephTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZephColorScheme,
        typography = ZephTypography,
        shapes = ZephShapes,
        content = content
    )
}
