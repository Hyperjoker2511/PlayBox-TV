package com.playbox.tv

import androidx.compose.ui.graphics.Color

// ---------- AURA (2026-11-23, 1.4.5 Patch 2) — the third design ----------
// The user's favourite of the three designs, sent as a clean HTML
// file (workspace/goals/playbox-tv/files/playbox-aura-tv.html —
// "AURA TV - Android TV Video App | Material 3 + ColorOS 17").
// Aura is a Material 3 Expressive TV design, and it differs from
// Nova and Qwen in STRUCTURE, not just in tokens:
//   - Navigation: an EXPANDING left rail (96dp icon-only, grows to
//     280dp with labels on hover/focus), a bottom nav bar on a
//     narrow phone — never Nova's tab strip or Qwen's pill bar.
//   - Home: a top bar (clock + location, mic + flag), a chips row,
//     a big 480dp cinematic hero (purple gradient + a poster card
//     tilted 3deg that straightens on hover), then horizontal rows
//     of 16:9 and poster cards — not Nova's list, not Qwen's grid.
//   - Focus: a visible 3px lavender FOCUS RING that glides to the
//     focused element (the Aura file's #focus-ring + D-pad focus
//     manager) — the TV navigation the other two designs lack.
//   - Player: a full-screen black player with a bottom UI — a 4px
//     progress track with a lavender fill and a symmetric transport.
// Aura is a THIRD OPTION (Settings > Appearance > App design:
// Nova / Qwen / Aura). Nova stays the default. The three designs
// share the data layer (MediaLibrary, LibraryStores, the players)
// and nothing else — Aura has its own tokens, components and
// screens. The Aura file is a streaming mock (Dune, IMDb, "4K HDR /
// Dolby Atmos" badges) — PlayBox is a local player, so the movie
// data and those badges do NOT carry over; only the look and the
// structure do, rebuilt around the real local library with honest
// extension-only format badges.

// Aura tokens: Material 3 dark, lavender primary on near-black.
internal val AuraPrimary = Color(0xFFD0BCFF)
internal val AuraOnPrimary = Color(0xFF381E72)
internal val AuraPrimaryContainer = Color(0xFF4F378B)
internal val AuraSecondary = Color(0xFFCCC2DC)
internal val AuraTertiary = Color(0xFFEFB8C8)
internal val AuraSurface = Color(0xFF141218)
internal val AuraSurface2 = Color(0xFF1C1B20)
internal val AuraSurface3 = Color(0xFF23222A)
internal val AuraSurface4 = Color(0xFF2B2930)
internal val AuraOnSurface = Color(0xFFE6E0E9)
internal val AuraOnSurfaceVariant = Color(0xFFCAC4D0)
internal val AuraOutline = Color(0xFF938F99)
internal val AuraOutlineVariant = Color(0xFF49454F)

// Aura hero gradient: the file's purple cinematic wash.
internal val AuraHeroGradStart = Color(0xFF1A1033)
internal val AuraHeroGradMid = Color(0xFF3D2461)
internal val AuraHeroGradEnd = Color(0xFF8A4A6A)
internal val AuraPosterGradStart = Color(0xFF2A1E4A)
internal val AuraPosterGradEnd = Color(0xFF5A2E5A)

// Aura radii (the file's tokens).
internal val AuraRadiusHero = 32
internal val AuraRadiusCard = 20
internal val AuraRadiusBtn = 16
