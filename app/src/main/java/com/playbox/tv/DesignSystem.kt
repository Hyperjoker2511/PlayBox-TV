package com.playbox.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- P1 — ONE DESIGN SYSTEM (2026-11-24, plan v2 Patch 1) ----------
// Before P1, Nova / Qwen / Aura were three separate UIs: three rails
// (NovaRail, QwenRail, AuraRail), three token sets, three component
// files, and text-glyph "icons" (⌂ ⌕ ▣ ⚙). P1 makes them ONE system:
//   - One navigation rail (DesignRail below), one focus treatment,
//     one glass treatment, one spacing scale, one set of radii.
//   - One font across the app: Plus Jakarta Sans (OFL, bundled in
//     res/font/plus_jakarta_sans.ttf), applied app-wide through
//     LocalTextStyle in MainActivity.
//   - One line-icon set: Material Outlined icons (designIconFor).
//   - The three designs are now PALETTES (colour + accent) on top of
//     this one system — they stop being three separate UIs. A design
//     switch changes the palette, not the structure.
// The per-design screens (NovaHomeScreen, AuraHomeScreen, HomeScreen)
// keep their own layouts for now — that structure is what the later
// patches (P2–P8) rebuild onto this system, one patch at a time.
// Motion stays transform + opacity only, 60fps, no live blur.

/** One font across the app: Plus Jakarta Sans (variable font — the
 * FontWeight entries pick the matching weight instance). */
internal val AppFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

/** Shared spacing scale — one scale for every screen. */
internal object DsSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Shared corner radii. */
internal object DsRadius {
    val card = 20.dp
    val button = 14.dp
    val sheet = 24.dp
    val pill = 28.dp
}

/** The rail: one width collapsed (icon-only), one expanded (labels). */
internal val DesignRailCollapsedWidth = 76.dp
internal val DesignRailExpandedWidth = 208.dp

/** Shared focus: a small scale-up on focus + a ring in the palette's
 * focus colour. Transform + opacity only. */
internal const val DesignFocusScale = 1.06f

/** A design palette — the ONLY thing that differs between Nova, Qwen
 * and Aura now. Structure (rail, glass, focus, spacing, font, icons)
 * is shared; each design is a palette (colour + accent) on top. */
internal data class DesignPalette(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val muted: Color,
    val outline: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val focusBorder: Color,
    val focusGlow: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassEdge: Color,
    val glassRim: Color
)

/** Nova — warm dark, solid surfaces, violet accent. */
internal val NovaPalette = DesignPalette(
    bg = NovaBg,
    surface = NovaSurface,
    surfaceHigh = NovaSurfaceHigh,
    onSurface = NovaWhite,
    muted = NovaMuted,
    outline = NovaOutline,
    primary = NovaViolet,
    primaryContainer = NovaVioletDark,
    onPrimary = NovaWhite,
    focusBorder = NovaViolet,
    focusGlow = NovaViolet.copy(alpha = 0.25f),
    glass = NovaSurface.copy(alpha = 0.88f),
    glassStrong = NovaSurface.copy(alpha = 0.96f),
    glassEdge = Color(0x14FFFFFF),
    glassRim = Color(0x40FFFFFF)
)

/** Qwen — the kept option, deep ink + violet glass (Lumen tokens). */
internal val QwenPalette = DesignPalette(
    bg = Bg,
    surface = Surface,
    surfaceHigh = SurfaceHigh,
    onSurface = White,
    muted = Muted,
    outline = Color(0xFF262B38),
    primary = Primary,
    primaryContainer = PrimaryContainer,
    onPrimary = White,
    focusBorder = FocusBorder,
    focusGlow = FocusGlow,
    glass = Glass,
    glassStrong = GlassStrong,
    glassEdge = GlassEdge,
    glassRim = GlassRim
)

/** Aura — Material 3 dark, lavender primary on near-black. */
internal val AuraPalette = DesignPalette(
    bg = AuraSurface,
    surface = AuraSurface2,
    surfaceHigh = AuraSurface3,
    onSurface = AuraOnSurface,
    muted = AuraOnSurfaceVariant,
    outline = AuraOutlineVariant,
    primary = AuraPrimary,
    primaryContainer = AuraPrimaryContainer,
    onPrimary = AuraOnPrimary,
    focusBorder = AuraPrimary,
    focusGlow = AuraPrimary.copy(alpha = 0.25f),
    glass = AuraSurface2.copy(alpha = 0.88f),
    glassStrong = AuraSurface2.copy(alpha = 0.96f),
    glassEdge = Color(0x14FFFFFF),
    glassRim = Color(0x40FFFFFF)
)

internal fun paletteFor(design: AppDesign): DesignPalette = when (design) {
    AppDesign.NOVA -> NovaPalette
    AppDesign.QWEN -> QwenPalette
    AppDesign.AURA -> AuraPalette
}

/** One line-icon set (Material Outlined) for the navigation. */
internal fun designIconFor(tab: Tab): ImageVector = when (tab) {
    Tab.Home -> Icons.Outlined.Home
    Tab.Search -> Icons.Outlined.Search
    Tab.Explore -> Icons.Outlined.Explore
    Tab.Library -> Icons.Outlined.VideoLibrary
    Tab.Settings -> Icons.Outlined.Settings
}

internal fun designLabelFor(tab: Tab): String = when (tab) {
    Tab.Home -> "Home"
    Tab.Search -> "Search"
    Tab.Explore -> "Explore"
    Tab.Library -> "Library"
    Tab.Settings -> "Settings"
}

// ---------- The ONE navigation rail ----------
// Shared by Nova, Qwen and Aura. Collapsible: icon-only (76dp) by
// default, expands to 208dp with labels when it has focus (D-pad)
// or the pointer hovers it. The logo is a play tile in the palette's
// primary + "Zeph" when expanded. Five items — Home / Search /
// Explore / Library / Settings — the same five in every design
// (Nova and Qwen used to hide Explore in the rail even though the
// tab exists in all three). The selected item is a solid primary
// fill with on-primary content; focus is a ring + a small scale-up.
// The Player button is pinned at the bottom: a circle when collapsed,
// a full-width button when expanded. Transform + opacity only.
@Composable
internal fun DesignRail(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    palette: DesignPalette,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        if (expanded) DesignRailExpandedWidth else DesignRailCollapsedWidth,
        spring(dampingRatio = 0.85f, stiffness = 300f), label = "designRailWidth"
    )
    Column(
        modifier = modifier.width(width).fillMaxHeight()
            .background(palette.surface)
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = DsSpace.xl, horizontal = DsSpace.md),
        verticalArrangement = Arrangement.spacedBy(DsSpace.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = DsSpace.sm)
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .clip(RoundedCornerShape(DsRadius.button))
                    .background(palette.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow, contentDescription = null,
                    tint = palette.onPrimary, modifier = Modifier.size(24.dp)
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Zeph", fontSize = 22.sp, color = palette.onSurface,
                    fontWeight = FontWeight.Bold, fontFamily = AppFont
                )
            }
        }
        Spacer(modifier = Modifier.height(DsSpace.xl))
        listOf(Tab.Home, Tab.Search, Tab.Explore, Tab.Library, Tab.Settings).forEach { t ->
            val selected = tab == t
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val scale by animateFloatAsState(
                if (focused) DesignFocusScale else 1f,
                spring(dampingRatio = 0.75f, stiffness = 400f), label = "designRailScale"
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(DsRadius.button))
                    .background(if (selected) palette.primary else Color.Transparent)
                    .border(
                        if (focused) 2.dp else 0.dp, palette.focusBorder,
                        RoundedCornerShape(DsRadius.button)
                    )
                    .clickable(interactionSource = interaction, indication = null) { onTab(t) }
                    .padding(horizontal = DsSpace.md),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        designIconFor(t), contentDescription = designLabelFor(t),
                        tint = if (selected) palette.onPrimary else palette.muted,
                        modifier = Modifier.size(22.dp)
                    )
                    if (expanded) {
                        Spacer(modifier = Modifier.width(DsSpace.md))
                        Text(
                            designLabelFor(t), fontSize = 18.sp,
                            color = if (selected) palette.onPrimary else palette.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = AppFont, maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (expanded) {
            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(DsRadius.button))
                    .background(palette.primary)
                    .clickable { onPlayer() }
                    .padding(horizontal = DsSpace.md),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = null,
                        tint = palette.onPrimary, modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(DsSpace.md))
                    Text(
                        "Player", fontSize = 18.sp, color = palette.onPrimary,
                        fontWeight = FontWeight.SemiBold, fontFamily = AppFont
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(palette.primary)
                    .clickable { onPlayer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow, contentDescription = null,
                    tint = palette.onPrimary, modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
