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

// ---------- ZEPH — the new Material 3 design system (from scratch) ----------
// This is a NEW design, not the old Nova/Qwen/Aura look with M3 added.
// One system, one colour scheme, one typography scale, one shape set.
// M3 1.5.0 expressive: large rounded shapes, bold display type, a
// single violet accent (the user's one-accent rule — mint is retired).
// The violet is the brand: #8B5CF6, the same seed the app has used
// since Lumen. Everything else is white or muted grey on near-black.
// Motion stays transform + opacity only, 60fps, no live blur.

/** The Zeph colour scheme: M3 expressive dark, violet seed. */
internal val ZephColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    // Tertiary stays in the violet family (periwinkle) — one hue,
    // one accent. No second accent colour anywhere in the app.
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1E2A5C),
    tertiaryContainer = Color(0xFF2E3A6E),
    onTertiaryContainer = Color(0xFFDCE2FF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF171B26),
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF262B38),
    onSurfaceVariant = Color(0xFF9AA3B2),
    outline = Color(0xFF2A2F3D),
    outlineVariant = Color(0xFF262B38),
    surfaceContainerLowest = Color(0xFF0B0D12),
    surfaceContainerLow = Color(0xFF12141B),
    surfaceContainer = Color(0xFF1F2433),
    surfaceContainerHigh = Color(0xFF262B38),
    surfaceContainerHighest = Color(0xFF2E3442),
    inverseSurface = Color(0xFFF2F4F8),
    inverseOnSurface = Color(0xFF171B26),
    inversePrimary = Color(0xFF4C1D95),
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

/** The Zeph shape scale: expressive — large, soft, rounded. */
internal val ZephShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

/** The app theme. Wrap the whole app in this — it is the ONLY
 * MaterialTheme in the new design. The old designs' palettes are
 * not used by new screens; they are deleted in Phase 8. */
@Composable
internal fun ZephTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZephColorScheme,
        typography = ZephTypography,
        shapes = ZephShapes,
        content = content
    )
}
