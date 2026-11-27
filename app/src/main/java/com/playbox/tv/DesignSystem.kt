package com.playbox.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
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
//   - One font across the app: Roboto Flex (OFL, bundled in
//     res/font/roboto_flex.ttf), applied app-wide through
//     LocalTextStyle in MainActivity.
//   - One line-icon set: Material Outlined icons (designIconFor).
//   - The three designs are now PALETTES (colour + accent) on top of
//     this one system — they stop being three separate UIs. A design
//     switch changes the palette, not the structure.
// The per-design screens (NovaHomeScreen, AuraHomeScreen, HomeScreen)
// keep their own layouts for now — that structure is what the later
// patches (P2–P8) rebuild onto this system, one patch at a time.
// Motion stays transform + opacity only, 60fps, no live blur.

/** One font across the app: Roboto Flex (variable font, OFL —
 * the 2.0.0 design's typeface). The FontWeight entries pick the
 * matching weight instance from the variable file. */
internal val AppFont = FontFamily(
    Font(R.font.roboto_flex, FontWeight.Normal),
    Font(R.font.roboto_flex, FontWeight.Medium),
    Font(R.font.roboto_flex, FontWeight.SemiBold),
    Font(R.font.roboto_flex, FontWeight.Bold)
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

/** Shared corner radii — the design's scale (index.css): xs 4,
 * sm 8, md 12, lg 16, xl 28, 2xl 36. Cards are lg (16dp), sheets
 * and dialogs are xl (28dp), buttons and pills are full (28dp
 * here is effectively a pill for a 40dp button). */
internal object DsRadius {
    val card = 16.dp
    val button = 28.dp
    val sheet = 28.dp
    val pill = 28.dp
}

/** The rail (2.0.0 design): one fixed width, 88dp, compact — the
 * design's rail is a flat surface strip with a 56×32 pill + a 12sp
 * label per item. Labels are always on; there is no collapsed mode. */
internal val DesignRailWidth = 88.dp

/** Shared focus (2.0.2): scale 1.10 on focus + a 5dp ring
 * in the M3 on-surface colour (near-white in dark, near-black in
 * light — maximally visible on any background) + a state-layer
 * fill + a black lift shadow. Transform + opacity only. */
internal const val DesignFocusScale = 1.10f

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
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
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
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    focusBorder = Color(0xFFD0BCFF),
    focusGlow = Color(0xFFD0BCFF).copy(alpha = 0.25f),
    glass = Color(0xFF211F26).copy(alpha = 0.88f),
    glassStrong = Color(0xFF211F26).copy(alpha = 0.96f),
    glassEdge = Color(0x14FFFFFF),
    glassRim = Color(0x40FFFFFF)
)


/** One icon set for the navigation — the design's rail glyphs
 * (chrome.tsx ITEMS): Home / Video (movie) / Audio (music_note) /
 * Browse (folder) / Playlists (playlist_play) / Settings, plus
 * Search as a button under the logo. The icon is FILLED when the
 * item is selected, outlined when it is not (the design's Icon
 * fill={sel}). */
internal fun designIconFor(tab: Tab, selected: Boolean = false): ImageVector = when (tab) {
    Tab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Tab.Video -> if (selected) Icons.Filled.Movie else Icons.Outlined.Movie
    Tab.Audio -> if (selected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote
    Tab.Browse -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    Tab.Playlists -> if (selected) Icons.Filled.PlaylistPlay else Icons.Outlined.PlaylistPlay
    Tab.Settings -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    Tab.Search -> Icons.Outlined.Search
}

internal fun designLabelFor(tab: Tab): String = when (tab) {
    Tab.Home -> "Home"
    Tab.Video -> "Video"
    Tab.Audio -> "Audio"
    Tab.Browse -> "Browse"
    Tab.Playlists -> "Playlists"
    Tab.Settings -> "Settings"
    Tab.Search -> "Search"
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
 * has focus), ALWAYS (labels always, rail always expanded). v7: the
 * design always draws the rail expanded, so the default is ALWAYS. */
internal enum class RailLabelMode { OFF, FOCUSED, ALWAYS }

internal fun railLabelModeFromName(name: String?): RailLabelMode = when (name) {
    "off" -> RailLabelMode.OFF
    "focused" -> RailLabelMode.FOCUSED
    else -> RailLabelMode.ALWAYS
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

// M25 (2.0.1): the cast receiver on/off. Default ON (the old
// behaviour — the receiver always ran while the app was open).
// Off stops the receiver entirely, so no phone can cast to
// this TV until it is switched back on.
internal fun castReceiverOn(context: android.content.Context): Boolean =
    context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("cast_receiver_on", true)

internal fun saveCastReceiverOn(context: android.content.Context, on: Boolean) {
    context.getSharedPreferences("playbox_settings", android.content.Context.MODE_PRIVATE).edit()
        .putBoolean("cast_receiver_on", on)
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

/** The rail's inner content (2.0.0 design, chrome.tsx NavRail):
 * the Zeph logo tile at the top (30dp, 9dp radius), a Search
 * button under it (never selected, on-surface icon), then the six
 * rail items centred (12dp apart), then the wifi icon at the base
 * — always shown, 18dp, on-surface-variant. The colours come from
 * the current theme (MaterialTheme), NOT a fixed palette, so the
 * rail follows the theme when it changes. Labels are always on.
 * The items area scrolls, so every item stays reachable on a
 * short phone-landscape screen. */
@Composable
private fun DesignRailContent(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    // M23 (2.0.1): a requester for the selected tab item, so
    // MainActivity can return focus to the rail when an overlay
    // (Details) closes and the focused element unmounts.
    selectedTabFocus: FocusRequester? = null
) {
    Column(
        modifier = modifier.padding(vertical = DsSpace.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Zeph logo: the design's ZephLogo at size 36 — its SVG is
        // a 48×48 viewBox rendered at 36, so the tile is 30dp with
        // a 9dp radius and a ~10×13dp triangle.
        Box(
            modifier = Modifier.size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = "Zeph Player",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Search lives under the logo, NOT in the item list — the
        // design puts it there, and on the Search route the rail's
        // active item is Home.
        RailCircleButton(
            icon = Icons.Outlined.Search,
            label = "Search",
            onClick = onSearch
        )
        Spacer(modifier = Modifier.height(8.dp))
        // The six items, vertically centred; the area scrolls when
        // the screen is too short for all of them.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(Tab.Home, Tab.Video, Tab.Audio, Tab.Browse, Tab.Playlists, Tab.Settings).forEach { t ->
                    RailPillItem(
                        tab = t,
                        // On the Search route the rail's active item is Home.
                        selected = if (tab == Tab.Search) t == Tab.Home else tab == t,
                        onClick = { onTab(t) },
                        // M23: the selected tab carries the requester.
                        focusRequester = if ((if (tab == Tab.Search) t == Tab.Home else tab == t)) selectedTabFocus else null
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            Icons.Outlined.Wifi, contentDescription = "Wi-Fi",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** One rail item (2.0.0 design, chrome.tsx NavRail): a 72dp-wide
 * button (rounded-lg = 16dp, 2dp vertical padding) with a 56×32
 * pill + a label-m label below it. The selected pill is
 * secondaryContainer and sweeps in over 450ms emphasized —
 * scaleX 0→1, scaleY 0.75→1 — and the icon is filled when
 * selected, outlined when not. The label is label-m (12sp,
 * 0.5 tracking), Bold + on-surface when selected, on-surface-
 * variant when not. Focus is the design's [data-f] WITHOUT lift:
 * the rail items carry no lift class — a 3dp secondary outline
 * (offset 3dp) around the whole item + a 0.12 on-surface state
 * layer on the pill, no scale, no shadow. */
@Composable
private fun RailPillItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    // The selected pill sweeps in (scaleX 0→1, scaleY 0.75→1).
    val pillScaleX by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(durationMillis = 450, easing = Motion.Emphasized), label = "railPillScaleX"
    )
    val pillScaleY by animateFloatAsState(
        if (selected) 1f else 0.75f,
        tween(durationMillis = 450, easing = Motion.Emphasized), label = "railPillScaleY"
    )
    Column(
        modifier = Modifier.width(72.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .zephFocus(interaction, focusScale = 1f, cornerRadius = 16.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp, 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = pillScaleX; scaleY = pillScaleY }
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent
                    )
            )
            // State layer: on-surface at 0.12 when focused or
            // pressed (the design's .sl::after on the pill).
            if (focused || pressed) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
            }
            Icon(
                designIconFor(tab, selected), contentDescription = designLabelFor(tab),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            designLabelFor(tab),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** The Search button under the logo (2.0.0 design, chrome.tsx):
 * a 40dp circle with an on-surface icon. It is NEVER selected —
 * the design's Search IconButton carries no selected state.
 * Focus is the [data-f] outline only (no lift, no shadow). */
@Composable
private fun RailCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(40.dp)
            .zephFocus(interaction, focusScale = 1f, cornerRadius = 20.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** The docked rail (2.0.0 design): a flat 88dp surface strip, left,
 * labels always on. `labelMode` is kept for API stability — the
 * design always draws labels, so the mode no longer changes the
 * rail. (The setting itself is removed in Patch 8.) */
@Composable
internal fun DesignRail(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    labelMode: RailLabelMode = RailLabelMode.ALWAYS,
    modifier: Modifier = Modifier,
    selectedTabFocus: FocusRequester? = null
) {
    DesignRailContent(
        tab = tab, onTab = onTab, onSearch = onSearch,
        modifier = modifier.width(DesignRailWidth).fillMaxHeight(),
        selectedTabFocus = selectedTabFocus
    )
}

/** The rail + content layout (2.0.0 design): the rail is a flat
 * 88dp surface strip on the left — no pill, no border — and the
 * content fills the rest. The same rail on every screen size: the
 * phone gets this rail too (the §11 correction), scrolling when
 * the screen is short. `content` is the tab's screen.
 *
 * 2.0.1 (Bug 3): overscan-safe inset. The rail sat flush at x=0
 * with no margin, and budget TVs (the user's Koryo) commonly
 * crop 3–5% of the edges on HDMI — cutting the rail off-screen
 * entirely, so the user couldn't see it or its focus ring and
 * couldn't reach the tabs. The whole layout is now inset 24dp
 * on both sides, so the rail starts inside the visible area.
 *
 * 2.0.3: explicit rail bridge. The content is a LazyColumn, and
 * Compose's focus search from inside a lazy list cannot find
 * the rail to the left — so Left from the content did nothing
 * and the user could never reach Settings / Search / the other
 * tabs (2.0.2 only made the ring brighter, which is useless if
 * focus can't get there). This intercepts DPAD_LEFT while focus
 * is in the content: it first tries the normal move (so Left
 * still steps between cards in a horizontal row), and only when
 * there is nothing to the left does it jump to the rail's
 * selected item. Right is left to the default search — the
 * content is a big target to the right, so it already works. */
@Composable
internal fun RailLayout(
    labelMode: RailLabelMode,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTabFocus: FocusRequester? = null,
    content: @Composable () -> Unit
) {
    // Which panel currently holds focus — tracked so Left is only
    // bridged out of the content, never out of the rail itself
    // (Left on the rail is already the left edge: do nothing).
    var contentHasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionLeft &&
                    event.type == KeyEventType.KeyDown && contentHasFocus
                ) {
                    // Normal move first (previous card in a row).
                    val moved = runCatching {
                        focusManager.moveFocus(FocusDirection.Left)
                    }.getOrDefault(false)
                    if (!moved) {
                        // Nothing to the left in the content — land
                        // on the rail's selected tab.
                        runCatching { selectedTabFocus?.requestFocus() }
                    }
                    true
                } else {
                    false
                }
            }
    ) {
        Box(
            modifier = Modifier.width(DesignRailWidth).fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            DesignRail(tab = tab, onTab = onTab, onSearch = onSearch, labelMode = labelMode, selectedTabFocus = selectedTabFocus)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .onFocusChanged { contentHasFocus = it.hasFocus }
        ) { content() }
    }
}
