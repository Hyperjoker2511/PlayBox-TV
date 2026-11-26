package com.playbox.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FolderShared
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
import androidx.compose.ui.focus.focusRequester
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

/** The rail: one width collapsed (icon-only), one expanded (labels).
 * Expanded is 132dp with 15sp sofa-sized labels (v5 design) — a TV
 * rail, not a tablet rail. */
internal val DesignRailCollapsedWidth = 76.dp
internal val DesignRailExpandedWidth = 132.dp

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

/** Zeph — the M3 Expressive palette (Nocturne Violet, the default).
 * The M3 baseline seed #6750A4, so the shared RailLayout keeps
 * working unchanged. */
internal val ZephPalette = DesignPalette(
    bg = Color(0xFF141218),
    surface = Color(0xFF211F26),
    surfaceHigh = Color(0xFF2B2930),
    onSurface = Color(0xFFE6E0E9),
    muted = Color(0xFFCAC4D0),
    outline = Color(0xFF49454F),
    primary = Color(0xFFD0BCFF),
    primaryContainer = Color(0xFF4F378B),
    onPrimary = Color(0xFF381E72),
    focusBorder = Color(0xFFD0BCFF),
    focusGlow = Color(0xFFD0BCFF).copy(alpha = 0.25f),
    glass = Color(0xFF211F26).copy(alpha = 0.88f),
    glassStrong = Color(0xFF211F26).copy(alpha = 0.96f),
    glassEdge = Color(0x14FFFFFF),
    glassRim = Color(0x40FFFFFF)
)


/** One line-icon set (Material Outlined) for the navigation. */
internal fun designIconFor(tab: Tab): ImageVector = when (tab) {
    Tab.Home -> Icons.Outlined.Home
    Tab.Search -> Icons.Outlined.Search
    Tab.Sources -> Icons.Outlined.FolderShared
    Tab.Library -> Icons.Outlined.VideoLibrary
    Tab.Settings -> Icons.Outlined.Settings
    // Explore is not in the rail (a local player has no Explore
    // section) but the tab still exists for old screens.
    Tab.Explore -> Icons.Outlined.Search
}

internal fun designLabelFor(tab: Tab): String = when (tab) {
    Tab.Home -> "Home"
    Tab.Search -> "Search"
    Tab.Sources -> "Sources"
    Tab.Library -> "Library"
    Tab.Settings -> "Settings"
    Tab.Explore -> "Explore"
}

// ---------- P2 — FLOATING RAIL + MENU LABELS (2026-11-24, plan v2 Patch 2) ----------
// The rail gains two modes and the labels gain three modes (plan v2 P2):
//   - RailMode.DOCKED (default): the rail is always present and the
//     content reflows around it — the P1 behaviour. RailMode.FLOATING:
//     the rail is a capsule floating OVER the content; it opens when
//     the user presses ◀ (D-pad left moves focus onto its edge handle)
//     and hides a few seconds after the user leaves it.
//   - RailLabelMode.OFF: icon-only, never shows labels. FOCUSED
//     (default): labels when the rail has focus (the P1 behaviour).
//     ALWAYS: labels always, rail always expanded.
// Note on the plan's rail-item list: plan v2 P2 lists "Home, Videos,
// Music, Folders/USB, Player, About" as the rail items, but those are
// OwnTV's sections, not PlayBox's. PlayBox's real screens are the
// five tabs (Home / Search / Explore / Library / Settings) + Player
// pinned at the bottom. P2 keeps those five real tabs — inventing
// Videos / Music / Folders / About tabs would be fake navigation to
// screens that do not exist. If the user wants those sections as
// separate tabs, that is a later patch, not P2.
// Both modes are stored in "playbox_settings" (same prefs as the
// app design) and picked in Settings > Appearance in all three
// settings screens. They apply on wide screens only (>= 700dp);
// narrow screens keep the top tab strip, unchanged.

/** Rail mode: DOCKED (always present, content reflows) or FLOATING
 * (capsule over content, opens on ◀, hides after a few seconds). */
internal enum class RailMode { DOCKED, FLOATING }

internal fun railModeFromName(name: String?): RailMode = when (name) {
    "floating" -> RailMode.FLOATING
    else -> RailMode.DOCKED
}

internal fun railMode(context: android.content.Context): RailMode =
    railModeFromName(
        context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE)
            .getString("rail_mode", null)
    )

internal fun saveRailMode(context: android.content.Context, mode: RailMode) {
    context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE).edit()
        .putString(
            "rail_mode",
            when (mode) {
                RailMode.DOCKED -> "docked"
                RailMode.FLOATING -> "floating"
            }
        )
        .apply()
}

/** Menu-label mode: OFF (icon-only), FOCUSED (labels when the rail
 * has focus), ALWAYS (labels always, rail always expanded). */
internal enum class RailLabelMode { OFF, FOCUSED, ALWAYS }

internal fun railLabelModeFromName(name: String?): RailLabelMode = when (name) {
    "off" -> RailLabelMode.OFF
    "always" -> RailLabelMode.ALWAYS
    else -> RailLabelMode.FOCUSED
}

internal fun railLabelMode(context: android.content.Context): RailLabelMode =
    railLabelModeFromName(
        context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE)
            .getString("rail_label", null)
    )

internal fun saveRailLabelMode(context: android.content.Context, mode: RailLabelMode) {
    context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE).edit()
        .putString(
            "rail_label",
            when (mode) {
                RailLabelMode.OFF -> "off"
                RailLabelMode.FOCUSED -> "focused"
                RailLabelMode.ALWAYS -> "always"
            }
        )
        .apply()
}

/** Patch 4: "Keep discoverable when closed" (Settings → Network).
 * OFF by default — the DLNA renderer stops when the app closes or
 * is swiped away, so the app leaves no background behind. ON keeps
 * the renderer running after close, for TV users who cast often. */
internal fun keepDiscoverable(context: android.content.Context): Boolean =
    context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("keep_discoverable", false)

internal fun saveKeepDiscoverable(context: android.content.Context, keep: Boolean) {
    context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE).edit()
        .putBoolean("keep_discoverable", keep)
        .apply()
}

/** How long the floating rail stays open after the user leaves it
 * (focus moves back into the content) before it hides. */
internal const val FloatingRailHideDelayMs = 5000L

// ---------- The ONE navigation rail ----------
// Shared by Nova, Qwen and Aura. Collapsible: icon-only (76dp) by
// default, expands to 132dp with 15sp sofa-sized labels when it has
// focus (D-pad) or the pointer hovers it. The logo is a play tile in
// the palette's primary + "Zeph" when expanded. Five items — Home /
// Search / Sources / Library / Settings — the same five in every
// design. The selected item is a solid primary fill with on-primary
// content; focus is a ring + a small scale-up. There is NO Player
// button at the base (v5: the player opens from a video, not the
// rail) and NO Explore section (a local player has none).
//
// P2 adds two modes on top of this one rail (see RailMode /
// RailLabelMode above): DOCKED (this rail, always present, content
// reflows) and FLOATING (a capsule over the content, opens on ◀,
// hides after a few seconds). The rail's inner content is shared
// (DesignRailContent) so both modes look and behave the same.

/** The rail's inner content: wordmark and the five tab items.
 * Shared by the docked rail and the floating rail. `expanded`
 * decides whether labels show; `onInteract` is called on every
 * rail interaction (focus, tap) so the floating rail can reset its
 * auto-hide timer; `focusRequester` is attached to the current
 * tab's item so the floating rail can move focus onto it when it
 * opens. `onPlayer` is kept for API stability — the rail no longer
 * shows a Player button (v5). */
@Composable
private fun DesignRailContent(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    palette: DesignPalette,
    expanded: Boolean,
    onInteract: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = DsSpace.xl, horizontal = DsSpace.md),
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
        // v5: Home / Search / Sources / Library / Settings. No
        // Explore — a local player has no Explore section — and no
        // Player button at the base. Sources is where content
        // arrives (folders, USB, SMB/FTP/SFTP, DLNA).
        listOf(Tab.Home, Tab.Search, Tab.Sources, Tab.Library, Tab.Settings).forEach { t ->
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
                    .then(if (t == tab) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { if (it.hasFocus) onInteract() }
                    .clickable(interactionSource = interaction, indication = null) {
                        onInteract(); onTab(t)
                    }
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
                            designLabelFor(t), fontSize = 15.sp,
                            color = if (selected) palette.onPrimary else palette.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = AppFont, maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** The docked rail (RailMode.DOCKED): always present, content
 * reflows around it. This is the P1 rail, now honouring labelMode:
 * OFF keeps it collapsed and icon-only, FOCUSED expands + labels
 * while it has focus, ALWAYS keeps it expanded with labels. */
@Composable
internal fun DesignRail(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    palette: DesignPalette,
    labelMode: RailLabelMode = RailLabelMode.FOCUSED,
    modifier: Modifier = Modifier
) {
    var railFocused by remember { mutableStateOf(false) }
    val expanded = when (labelMode) {
        RailLabelMode.OFF -> false
        RailLabelMode.FOCUSED -> railFocused
        RailLabelMode.ALWAYS -> true
    }
    val width by animateDpAsState(
        if (expanded) DesignRailExpandedWidth else DesignRailCollapsedWidth,
        spring(dampingRatio = 0.85f, stiffness = 300f), label = "designRailWidth"
    )
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    DesignRailContent(
        tab = tab, onTab = onTab, onPlayer = onPlayer, palette = palette,
        expanded = expanded, onInteract = {}, focusRequester = focusRequester,
        // No background here — the RailLayout wrapper paints the
        // floating pill around the rail, so a square background
        // would cover its rounded corners.
        modifier = modifier.width(width).fillMaxHeight()
            .onFocusChanged { railFocused = it.hasFocus }
    )
}

/** The floating rail (RailMode.FLOATING): a capsule over the
 * content. Closed, it is a slim glass edge handle on the left —
 * focusable, so pressing ◀ (D-pad left) from the content moves
 * focus onto it and it opens; tapping it opens it too. Open, it
 * is the full rail in a floating glass capsule; focus moves onto
 * the current tab's item. It hides a few seconds after the user
 * leaves it (focus moves back into the content); while the user is
 * inside it, it stays open. Transform + opacity only. */
@Composable
private fun FloatingRail(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    palette: DesignPalette,
    labelMode: RailLabelMode,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    var railFocused by remember { mutableStateOf(false) }
    var interactionNonce by remember { mutableStateOf(0) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val expanded = when (labelMode) {
        RailLabelMode.OFF -> false
        // The open floating rail always shows its labels — the user
        // opened it to navigate, so hiding the labels until an item
        // gains focus would be extra work for nothing.
        RailLabelMode.FOCUSED -> railFocused || open
        RailLabelMode.ALWAYS -> true
    }
    // Auto-hide: only while open AND the user is not inside the
    // rail. Any interaction (focus move, tap, Player) resets the
    // timer; leaving the rail starts it.
    androidx.compose.runtime.LaunchedEffect(open, railFocused, interactionNonce) {
        if (open && !railFocused) {
            kotlinx.coroutines.delay(FloatingRailHideDelayMs)
            open = false
        }
    }
    // When it opens, move focus onto the current tab's item.
    androidx.compose.runtime.LaunchedEffect(open) {
        if (open) focusRequester.requestFocus()
    }
    Box(modifier = modifier.fillMaxHeight()) {
        if (open) {
            DesignRailContent(
                tab = tab, onTab = onTab, onPlayer = onPlayer, palette = palette,
                expanded = expanded,
                onInteract = { interactionNonce++ },
                focusRequester = focusRequester,
                modifier = Modifier
                    .padding(start = DsSpace.md, top = DsSpace.lg, bottom = DsSpace.lg)
                    .width(if (expanded) DesignRailExpandedWidth else DesignRailCollapsedWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(DsRadius.sheet))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.glassRim, RoundedCornerShape(DsRadius.sheet))
                    .onFocusChanged { railFocused = it.hasFocus }
            )
        } else {
            // Slim glass edge handle: the ◀ target. Gaining focus
            // opens the rail; tapping it does too.
            Box(
                modifier = Modifier
                    .width(22.dp).fillMaxHeight()
                    .padding(vertical = 96.dp)
                    .clip(RoundedCornerShape(topEnd = DsRadius.pill, bottomEnd = DsRadius.pill))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.glassRim, RoundedCornerShape(topEnd = DsRadius.pill, bottomEnd = DsRadius.pill))
                    .focusable()
                    .onFocusChanged { if (it.hasFocus) open = true }
                    .clickable { open = true },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.width(4.dp).height(48.dp)
                        .clip(RoundedCornerShape(DsRadius.pill))
                        .background(palette.muted.copy(alpha = 0.6f))
                )
            }
        }
    }
}

/** The rail + content layout for a wide screen. DOCKED puts the
 * rail in a Row so the content reflows around it; FLOATING puts
 * the content full-bleed and overlays the floating rail on top.
 * `content` is the tab's screen. Narrow screens do not use this —
 * they keep the top tab strip, unchanged. */
@Composable
internal fun RailLayout(
    railMode: RailMode,
    labelMode: RailLabelMode,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    palette: DesignPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (railMode) {
        RailMode.DOCKED -> {
            // v5: the rail lives on the LEFT — TV navigation lives
            // on the left in every TV app, including PlayBox. The
            // 1.8.0 right-side floating pill is gone. Content sits
            // on the right; the rail is a rounded surfaceContainer
            // pill with a gap around it. D-pad: ◀ from the content
            // reaches it.
            Row(modifier = modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .padding(top = DsSpace.lg, bottom = DsSpace.lg, start = DsSpace.md, end = DsSpace.sm)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(DsRadius.sheet))
                        .background(palette.surface)
                        .border(1.dp, palette.outline, RoundedCornerShape(DsRadius.sheet))
                ) {
                    DesignRail(tab = tab, onTab = onTab, onPlayer = onPlayer, palette = palette, labelMode = labelMode)
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }
        RailMode.FLOATING -> {
            Box(modifier = modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) { content() }
                FloatingRail(tab = tab, onTab = onTab, onPlayer = onPlayer, palette = palette, labelMode = labelMode)
            }
        }
    }
}
