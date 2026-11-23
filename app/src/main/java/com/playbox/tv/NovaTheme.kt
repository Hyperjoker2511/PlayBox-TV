package com.playbox.tv

import android.content.Context
import androidx.compose.ui.graphics.Color

// ---------- NOVA (2026-11-23) — the full app, designed from scratch ----------
// The user asked for a real redesign, not a recolour: "You just
// changed colors in qwen design / I need you to design full app from
// scratch". So Nova differs from Qwen/Lumen in STRUCTURE, not just in
// tokens:
//   - Navigation: a left rail on wide screens (TV, phone landscape),
//     a top tab strip on narrow screens — never Qwen's top pill bar.
//   - Home: a "Continue watching" carousel of wide cards, then the
//     videos as a vertical LIST of rows — no hero banner, no grid.
//   - Surfaces: solid, flat, editorial — no glass, no blur, no rim
//     light, no floating capsule. A 1dp outline and a solid fill.
//   - Player: a full-width solid bottom BAR, edge to edge — not a
//     floating capsule.
//   - Motion: fade + a small rise (12dp), transform + opacity only.
// Qwen is NOT deleted: it stays as a customizable option
// (Settings > Appearance > App design: Nova / Qwen). The two designs
// share the data layer (MediaLibrary, LibraryStores, the players)
// and nothing else — Nova has its own tokens, components and screens.

// Nova tokens: warm dark, solid surfaces, violet + mint kept as the
// brand (the user likes them) — the newness is in the layout, not in
// a new palette.
internal val NovaBg = Color(0xFF0B0D12)
internal val NovaSurface = Color(0xFF171B26)
internal val NovaSurfaceHigh = Color(0xFF1F2433)
internal val NovaOutline = Color(0xFF2A2F3D)
internal val NovaViolet = Color(0xFF7C5CFF)
internal val NovaVioletDark = Color(0xFF4C1D95)
internal val NovaMint = Color(0xFF6FF2C8)
internal val NovaWhite = Color(0xFFF2F4F8)
internal val NovaMuted = Color(0xFF9AA3B2)
internal val NovaInk = Color(0xFF101524)

/** The whole-app design. NOVA is the default (the from-scratch
 * design); QWEN is the kept option. (unit-tested) */
internal enum class AppDesign { NOVA, QWEN }

internal fun appDesignFromName(name: String?): AppDesign = when (name) {
    "qwen" -> AppDesign.QWEN
    else -> AppDesign.NOVA
}

internal fun appDesign(context: Context): AppDesign =
    appDesignFromName(
        context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
            .getString("app_design", null)
    )

internal fun saveAppDesign(context: Context, design: AppDesign) {
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE).edit()
        .putString(
            "app_design",
            when (design) {
                AppDesign.NOVA -> "nova"
                AppDesign.QWEN -> "qwen"
            }
        )
        .apply()
}
