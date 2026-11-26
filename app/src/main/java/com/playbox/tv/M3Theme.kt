package com.playbox.tv

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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

// ---------- ZEPH — Material 3 Expressive design system (2.0.0) ----------
// FULL Material 3, including M3's own motion: the theme sets
// MotionScheme.expressive(), so M3 components animate on M3's
// spatial/effects springs, and the app's own transitions use M3's
// duration tokens + emphasized easing (see Motion.kt).
// Colour is the user's choice, not one imposed seed:
//   - Nocturne Violet is the DEFAULT (the M3 baseline seed #6750A4).
//   - Presets are the design's seed swatches: Violet, Ocean, Forest,
//     Orange, Rose, Amber (theme.ts SEEDS). Violet uses the exact M3
//     baseline scheme; the others are derived from their seed.
//   - Dynamic colour (Android 12+): the scheme follows the device.
//   - Free picker: any seed hex, derived into a tonal scheme.
//   - Dark AND Light: every seed has a light scheme (the design's
//     buildScheme generates both from the seed via HCT).
// The choice lives in "playbox_settings" (theme_preset / theme_seed /
// theme_dark) and is picked in Settings > Appearance. Motion stays
// transform + opacity only, 60fps, no live blur.

// ---------- Theme choice (prefs) ----------

internal const val ThemePresetViolet = "violet"
internal const val ThemePresetOcean = "ocean"
internal const val ThemePresetForest = "forest"
internal const val ThemePresetOrange = "orange"
internal const val ThemePresetRose = "rose"
internal const val ThemePresetAmber = "amber"
internal const val ThemePresetDynamic = "dynamic"
internal const val ThemePresetCustom = "custom"

internal val ThemePresets = listOf(
    ThemePresetViolet to "Violet",
    ThemePresetOcean to "Ocean",
    ThemePresetForest to "Forest",
    ThemePresetOrange to "Orange",
    ThemePresetRose to "Rose",
    ThemePresetAmber to "Amber"
)

/** The design's seed hexes (theme.ts SEEDS). */
internal fun presetSeedHex(preset: String): String = when (preset) {
    ThemePresetOcean -> "#0b6fa4"
    ThemePresetForest -> "#2e7d4f"
    ThemePresetOrange -> "#e8590c"
    ThemePresetRose -> "#b4236a"
    ThemePresetAmber -> "#b78100"
    else -> "#6750a4"
}

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

/** Dark mode on by default (the design's DEFAULTS: dark: true). */
internal fun themeDark(context: Context): Boolean =
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
        .getBoolean("theme_dark", true)

internal fun saveThemeDark(context: Context, dark: Boolean) {
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE).edit()
        .putBoolean("theme_dark", dark).apply()
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

/** Nocturne Violet — LIGHT. The exact M3 baseline light scheme
 * (the design's buildScheme generates the light scheme from the
 * same seed via HCT; this is its Violet output). */
internal val ZephVioletLightScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF4F378B),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    inversePrimary = Color(0xFFD0BCFF),
    scrim = Color.Black
)

// ---------- Custom seed ----------

/** Parse a #RRGGBB seed hex; falls back to Violet. */
internal fun seedColor(seedHex: String): Color = runCatching {
    val hex = seedHex.trim().removePrefix("#")
    Color(android.graphics.Color.parseColor("#$hex"))
}.getOrNull() ?: Color(0xFF6750A4)

/** Derive a tonal dark scheme from any seed hex (#RRGGBB). The
 * seed's hue drives primary / secondary / tertiary; the neutrals
 * stay the Violet dark steps. A rough derivation, not M3's HCT —
 * the presets above are the curated ones. */
internal fun schemeFromSeed(seedHex: String): ColorScheme {
    val seed = seedColor(seedHex)
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

/** Derive a tonal LIGHT scheme from any seed hex (#RRGGBB) —
 * the light mirror of schemeFromSeed: the seed's hue drives
 * primary / secondary / tertiary at light tonal values, the
 * neutrals stay the Violet light steps. The design generates
 * its light scheme from the same seed via HCT; this is the
 * app's approximation of it. */
internal fun schemeFromSeedLight(seedHex: String): ColorScheme {
    val seed = seedColor(seedHex)
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
    return lightColorScheme(
        primary = tone(h, s * 0.6f, 0.42f),
        onPrimary = Color.White,
        primaryContainer = tone(h, s * 0.35f, 0.92f),
        onPrimaryContainer = tone(h, s * 0.6f, 0.28f),
        secondary = tone(h + 25f, s * 0.4f, 0.42f),
        onSecondary = Color.White,
        secondaryContainer = tone(h + 25f, s * 0.3f, 0.9f),
        onSecondaryContainer = tone(h + 25f, s * 0.4f, 0.25f),
        tertiary = tone(h + 55f, s * 0.5f, 0.45f),
        onTertiary = Color.White,
        tertiaryContainer = tone(h + 55f, s * 0.4f, 0.9f),
        onTertiaryContainer = tone(h + 55f, s * 0.5f, 0.28f),
        background = ZephVioletLightScheme.background,
        onBackground = ZephVioletLightScheme.onBackground,
        surface = ZephVioletLightScheme.surface,
        onSurface = ZephVioletLightScheme.onSurface,
        surfaceVariant = ZephVioletLightScheme.surfaceVariant,
        onSurfaceVariant = ZephVioletLightScheme.onSurfaceVariant,
        outline = ZephVioletLightScheme.outline,
        outlineVariant = ZephVioletLightScheme.outlineVariant,
        surfaceContainerLowest = ZephVioletLightScheme.surfaceContainerLowest,
        surfaceContainerLow = ZephVioletLightScheme.surfaceContainerLow,
        surfaceContainer = ZephVioletLightScheme.surfaceContainer,
        surfaceContainerHigh = ZephVioletLightScheme.surfaceContainerHigh,
        surfaceContainerHighest = ZephVioletLightScheme.surfaceContainerHighest,
        inverseSurface = ZephVioletLightScheme.inverseSurface,
        inverseOnSurface = ZephVioletLightScheme.inverseOnSurface,
        inversePrimary = tone(h, s * 0.45f, 0.85f),
        scrim = Color.Black
    )
}

/** The primary colour of a preset — for the picker swatches. */
internal fun presetPrimary(preset: String): Color = when (preset) {
    ThemePresetViolet -> ZephVioletScheme.primary
    ThemePresetDynamic -> ZephVioletScheme.primary
    ThemePresetCustom -> schemeFromSeed("#6750A4").primary
    else -> schemeFromSeed(presetSeedHex(preset)).primary
}

/** Resolve the scheme for a preset. Dynamic follows the device
 * (Android 12+); anything unknown falls back to Violet. Light
 * schemes mirror the dark ones — Violet uses the exact M3
 * baseline light scheme, the rest are derived from their seed. */
@Composable
internal fun zephColorScheme(preset: String, seedHex: String, dark: Boolean): ColorScheme {
    val context = LocalContext.current
    if (!dark) {
        return when (preset) {
            ThemePresetViolet -> ZephVioletLightScheme
            ThemePresetCustom -> schemeFromSeedLight(seedHex)
            ThemePresetDynamic -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else ZephVioletLightScheme
            else -> schemeFromSeedLight(presetSeedHex(preset))
        }
    }
    return when (preset) {
        ThemePresetCustom -> schemeFromSeed(seedHex)
        ThemePresetDynamic -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else ZephVioletScheme
        ThemePresetViolet -> ZephVioletScheme
        else -> schemeFromSeed(presetSeedHex(preset))
    }
}

/** The Zeph type scale: Roboto Flex throughout, the design's
 * M3 type scale (index.css): display and headline are Regular
 * 400, title and label are Medium 500, body is Regular 400 —
 * with the design's exact letter-spacing. */
internal val ZephTypography = Typography(
    displayLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

/** The Zeph shape scale — the design's six radii (index.css):
 * xs 4, sm 8, md 12, lg 16, xl 28, 2xl 36. M3's Shapes has five
 * slots, so extraSmall=xs, small=sm, medium=md, large=lg,
 * extraLarge=xl; 2xl (36dp) is used directly where the design
 * needs it. */
internal val ZephShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

/** The app theme. Wrap the whole app in this — it is the ONLY
 * MaterialTheme in the design. `preset` / `seedHex` / `dark` come
 * from the theme choice in Settings (see themePreset / themeSeed /
 * themeDark); the defaults are Nocturne Violet, dark. The motion
 * scheme is M3 Expressive — M3 components animate on M3's own
 * springs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ZephTheme(
    preset: String = ThemePresetViolet,
    seedHex: String = "#6750A4",
    dark: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = zephColorScheme(preset, seedHex, dark),
        typography = ZephTypography,
        shapes = ZephShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
