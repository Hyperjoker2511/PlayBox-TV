package com.playbox.tv

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- ZEPH — Material 3 Expressive design system (1.9.0) ----------
// FULL Material 3, including M3's own motion: the theme sets
// MotionScheme.expressive(), so M3 components animate on M3's
// spatial/effects springs, and the app's own transitions use M3's
// duration tokens + emphasized easing (see Motion.kt).
// Colour is the user's choice, not one imposed seed:
//   - Nocturne Violet is the DEFAULT (the M3 baseline seed #6750A4).
//   - Presets: Violet, Teal (the 1.7.0 look), Peach, Green, Blue, Pink.
//   - Dynamic colour (Android 12+): the scheme follows the device.
//   - Free picker: any seed hex, derived into a tonal dark scheme.
// The choice lives in "playbox_settings" (theme_preset / theme_seed)
// and is picked in Settings > Appearance. Motion stays transform +
// opacity only, 60fps, no live blur.

// ---------- Theme choice (prefs) ----------

internal const val ThemePresetViolet = "violet"
internal const val ThemePresetTeal = "teal"
internal const val ThemePresetPeach = "peach"
internal const val ThemePresetGreen = "green"
internal const val ThemePresetBlue = "blue"
internal const val ThemePresetPink = "pink"
internal const val ThemePresetDynamic = "dynamic"
internal const val ThemePresetCustom = "custom"

internal val ThemePresets = listOf(
    ThemePresetViolet to "Violet",
    ThemePresetTeal to "Teal",
    ThemePresetPeach to "Peach",
    ThemePresetGreen to "Green",
    ThemePresetBlue to "Blue",
    ThemePresetPink to "Pink"
)

internal fun themePreset(context: Context): String =
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
        .getString("theme_preset", ThemePresetViolet) ?: ThemePresetViolet

internal fun saveThemePreset(context: Context, preset: String) {
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE).edit()
        .putString("theme_preset", preset).apply()
}

internal fun themeSeed(context: Context): String =
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
        .getString("theme_seed", "#6750A4") ?: "#6750A4"

internal fun saveThemeSeed(context: Context, seedHex: String) {
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE).edit()
        .putString("theme_seed", seedHex).apply()
}

// ---------- Preset schemes (M3 tonal dark) ----------

/** Nocturne Violet — the DEFAULT. The M3 baseline seed #6750A4. */
internal val ZephVioletScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6750A4),
    scrim = Color.Black
)

/** Teal — the 1.7.0 look, kept as a preset. */
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

/** Peach — warm, same tonal steps as Violet. */
internal val ZephPeachScheme = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5F1500),
    primaryContainer = Color(0xFF7E2A15),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D3F37),
    onSecondaryContainer = Color(0xFFFFDAD2),
    tertiary = Color(0xFFE2C48B),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5A451F),
    onTertiaryContainer = Color(0xFFFDEBCF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF171311),
    onBackground = Color(0xFFE9E0DC),
    surface = Color(0xFF171311),
    onSurface = Color(0xFFE9E0DC),
    surfaceVariant = Color(0xFF51443D),
    onSurfaceVariant = Color(0xFFD1C2B8),
    outline = Color(0xFF9A8B81),
    outlineVariant = Color(0xFF51443D),
    surfaceContainerLowest = Color(0xFF110E0D),
    surfaceContainerLow = Color(0xFF201B18),
    surfaceContainer = Color(0xFF251F1C),
    surfaceContainerHigh = Color(0xFF302A26),
    surfaceContainerHighest = Color(0xFF3B3430),
    inverseSurface = Color(0xFFE9E0DC),
    inverseOnSurface = Color(0xFF362F2B),
    inversePrimary = Color(0xFF8B4A2B),
    scrim = Color.Black
)

/** Green. */
internal val ZephGreenScheme = darkColorScheme(
    primary = Color(0xFFA9D1A6),
    onPrimary = Color(0xFF1E3A1C),
    primaryContainer = Color(0xFF2F522C),
    onPrimaryContainer = Color(0xFFC4EFC0),
    secondary = Color(0xFFB8CCB4),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF394B37),
    onSecondaryContainer = Color(0xFFD4E8D0),
    tertiary = Color(0xFFA0CFDD),
    onTertiary = Color(0xFF0A3542),
    tertiaryContainer = Color(0xFF1F4D5B),
    onTertiaryContainer = Color(0xFFC6E9F5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121512),
    onBackground = Color(0xFFE2E5E1),
    surface = Color(0xFF121512),
    onSurface = Color(0xFFE2E5E1),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BE),
    outline = Color(0xFF8C948A),
    outlineVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFF0C0F0C),
    surfaceContainerLow = Color(0xFF1A1F1A),
    surfaceContainer = Color(0xFF1E241E),
    surfaceContainerHigh = Color(0xFF292F29),
    surfaceContainerHighest = Color(0xFF343A34),
    inverseSurface = Color(0xFFE2E5E1),
    inverseOnSurface = Color(0xFF2F332E),
    inversePrimary = Color(0xFF3E6B3A),
    scrim = Color.Black
)

/** Blue. */
internal val ZephBlueScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0A2E6B),
    primaryContainer = Color(0xFF284779),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF272F42),
    secondaryContainer = Color(0xFF3A4358),
    onSecondaryContainer = Color(0xFFDCE2F0),
    tertiary = Color(0xFFC9B6E8),
    onTertiary = Color(0xFF3A2B52),
    tertiaryContainer = Color(0xFF51406D),
    onTertiaryContainer = Color(0xFFE9DEF8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1C22),
    surfaceContainer = Color(0xFF1E2027),
    surfaceContainerHigh = Color(0xFF282A33),
    surfaceContainerHighest = Color(0xFF33353E),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF3B5BA9),
    scrim = Color.Black
)

/** Pink. */
internal val ZephPinkScheme = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5F1133),
    primaryContainer = Color(0xFF7E2A4C),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE5B8C5),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3A44),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFE8B48C),
    onTertiary = Color(0xFF422E22),
    tertiaryContainer = Color(0xFF5C4033),
    onTertiaryContainer = Color(0xFFFFDCC6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF161214),
    onBackground = Color(0xFFE9E0E3),
    surface = Color(0xFF161214),
    onSurface = Color(0xFFE9E0E3),
    surfaceVariant = Color(0xFF50444A),
    onSurfaceVariant = Color(0xFFD2C2C8),
    outline = Color(0xFF9B8B91),
    outlineVariant = Color(0xFF50444A),
    surfaceContainerLowest = Color(0xFF100D0F),
    surfaceContainerLow = Color(0xFF201B1F),
    surfaceContainer = Color(0xFF251F24),
    surfaceContainerHigh = Color(0xFF30282E),
    surfaceContainerHighest = Color(0xFF3B3339),
    inverseSurface = Color(0xFFE9E0E3),
    inverseOnSurface = Color(0xFF362F33),
    inversePrimary = Color(0xFF9B4A6B),
    scrim = Color.Black
)

// ---------- Custom seed ----------

/** Derive a tonal dark scheme from any seed hex (#RRGGBB). The
 * seed's hue drives primary / secondary / tertiary; the neutrals
 * stay the Violet dark steps. A rough derivation, not M3's HCT —
 * the presets above are the curated ones. */
internal fun schemeFromSeed(seedHex: String): ColorScheme {
    val seed = runCatching {
        val hex = seedHex.trim().removePrefix("#")
        val argb = android.graphics.Color.parseColor("#$hex")
        Color(argb)
    }.getOrNull() ?: Color(0xFF6750A4)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (seed.red * 255).toInt(), (seed.green * 255).toInt(), (seed.blue * 255).toInt()
        ), hsv
    )
    val h = hsv[0]
    val s = hsv[1]
    fun tone(hue: Float, sat: Float, value: Float): Color {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf((hue + 360f) % 360f, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
        return Color(rgb)
    }
    return darkColorScheme(
        primary = tone(h, s * 0.45f, 0.85f),
        onPrimary = tone(h, s * 0.7f, 0.22f),
        primaryContainer = tone(h, s * 0.55f, 0.32f),
        onPrimaryContainer = tone(h, s * 0.35f, 0.95f),
        secondary = tone(h + 25f, s * 0.35f, 0.78f),
        onSecondary = tone(h + 25f, s * 0.4f, 0.2f),
        secondaryContainer = tone(h + 25f, s * 0.35f, 0.3f),
        onSecondaryContainer = tone(h + 25f, s * 0.3f, 0.92f),
        tertiary = tone(h + 55f, s * 0.45f, 0.8f),
        onTertiary = tone(h + 55f, s * 0.5f, 0.22f),
        tertiaryContainer = tone(h + 55f, s * 0.4f, 0.32f),
        onTertiaryContainer = tone(h + 55f, s * 0.35f, 0.93f),
        background = ZephVioletScheme.background,
        onBackground = ZephVioletScheme.onBackground,
        surface = ZephVioletScheme.surface,
        onSurface = ZephVioletScheme.onSurface,
        surfaceVariant = ZephVioletScheme.surfaceVariant,
        onSurfaceVariant = ZephVioletScheme.onSurfaceVariant,
        outline = ZephVioletScheme.outline,
        outlineVariant = ZephVioletScheme.outlineVariant,
        surfaceContainerLowest = ZephVioletScheme.surfaceContainerLowest,
        surfaceContainerLow = ZephVioletScheme.surfaceContainerLow,
        surfaceContainer = ZephVioletScheme.surfaceContainer,
        surfaceContainerHigh = ZephVioletScheme.surfaceContainerHigh,
        surfaceContainerHighest = ZephVioletScheme.surfaceContainerHighest,
        inverseSurface = ZephVioletScheme.inverseSurface,
        inverseOnSurface = ZephVioletScheme.inverseOnSurface,
        inversePrimary = seed,
        scrim = Color.Black
    )
}

/** The primary colour of a preset — for the picker swatches. */
internal fun presetPrimary(preset: String): Color = when (preset) {
    ThemePresetTeal -> ZephColorScheme.primary
    ThemePresetPeach -> ZephPeachScheme.primary
    ThemePresetGreen -> ZephGreenScheme.primary
    ThemePresetBlue -> ZephBlueScheme.primary
    ThemePresetPink -> ZephPinkScheme.primary
    else -> ZephVioletScheme.primary
}

/** Resolve the scheme for a preset. Dynamic follows the device
 * (Android 12+); anything unknown falls back to Violet. */
@Composable
internal fun zephColorScheme(preset: String, seedHex: String): ColorScheme {
    val context = LocalContext.current
    return when (preset) {
        ThemePresetTeal -> ZephColorScheme
        ThemePresetPeach -> ZephPeachScheme
        ThemePresetGreen -> ZephGreenScheme
        ThemePresetBlue -> ZephBlueScheme
        ThemePresetPink -> ZephPinkScheme
        ThemePresetCustom -> schemeFromSeed(seedHex)
        ThemePresetDynamic -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else ZephVioletScheme
        else -> ZephVioletScheme
    }
}

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
 * MaterialTheme in the design. `preset` / `seedHex` come from the
 * theme choice in Settings (see themePreset / themeSeed); the
 * defaults are Nocturne Violet. The motion scheme is M3 Expressive
 * — M3 components animate on M3's own springs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ZephTheme(
    preset: String = ThemePresetViolet,
    seedHex: String = "#6750A4",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = zephColorScheme(preset, seedHex),
        typography = ZephTypography,
        shapes = ZephShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
