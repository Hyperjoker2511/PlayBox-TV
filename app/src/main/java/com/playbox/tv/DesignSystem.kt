package com.playbox.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

/** Shared corner radii. */
internal object DsRadius {
    val card = 20.dp
    val button = 14.dp
    val sheet = 24.dp
    val pill = 28.dp
}

/** The rail (2.0.0 design): one fixed width, 88dp, compact — the
 * design's rail is a flat surface strip with a 56×32 pill + a 12sp
 * label per item. Labels are always on; there is no collapsed mode. */
internal val DesignRailWidth = 88.dp

/** Shared focus (2.0.0 design): scale 1.06 on focus + a 3dp ring
 * in the M3 secondary colour + a black lift shadow. Transform +
 * opacity only. */
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


/** One line-icon set (Material Outlined) for the navigation —
 * 2.0.0 rail: Home / Video / Audio / Browse / Playlists /
 * Settings, plus Search as a button under the logo. */
internal fun designIconFor(tab: Tab): ImageVector = when (tab) {
    Tab.Home -> Icons.Outlined.Home
    Tab.Video -> Icons.Outlined.Movie
    Tab.Audio -> Icons.Outlined.MusicNote
    Tab.Browse -> Icons.Outlined.FolderShared
    Tab.Playlists -> Icons.Outlined.QueueMusic
    Tab.Settings -> Icons.Outlined.Settings
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

/** 2.0.0: the design shows a wifi icon at the rail's base. It is
 * real — shown only when the device is actually on wifi
 * (ConnectivityManager), never a decoration. */
internal fun isWifiConnected(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
        as? android.net.ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
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

/** The rail's inner content (2.0.0 design): the Zeph logo tile at
 * the top, a Search button under it, then the six rail items
 * centred, then the wifi icon at the base (only when on wifi).
 * Each item is a 56×32 pill (secondaryContainer when selected,
 * scaling in over 450ms emphasized) with a 12sp label below it.
 * Labels are always on. The items area scrolls, so every item
 * stays reachable on a short phone-landscape screen. */
@Composable
private fun DesignRailContent(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    wifiConnected: Boolean,
    palette: DesignPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = DsSpace.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Zeph logo: a 36dp primaryContainer tile with a play
        // triangle in onPrimaryContainer (the design's ZephLogo).
        Box(
            modifier = Modifier.size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = "Zeph Player",
                tint = palette.onSecondaryContainer, modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Search lives under the logo, NOT in the item list — the
        // design puts it there, and on the Search route the rail's
        // active item is Home.
        RailCircleButton(
            icon = Icons.Outlined.Search,
            label = "Search",
            selected = tab == Tab.Search,
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(Tab.Home, Tab.Video, Tab.Audio, Tab.Browse, Tab.Playlists, Tab.Settings).forEach { t ->
                    RailPillItem(
                        tab = t,
                        // On the Search route the rail's active item is Home.
                        selected = if (tab == Tab.Search) t == Tab.Home else tab == t,
                        palette = palette,
                        onClick = { onTab(t) }
                    )
                }
            }
        }
        if (wifiConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            Icon(
                Icons.Outlined.Wifi, contentDescription = "Wi-Fi connected",
                tint = palette.muted, modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** One rail item (2.0.0 design): a 56×32 pill + a 12sp label
 * below it. The selected pill is secondaryContainer and scales in
 * over 450ms emphasized; the icon is filled when selected. Focus
 * scales the whole item 1.06 with a 3dp secondary ring + shadow. */
@Composable
private fun RailPillItem(
    tab: Tab,
    selected: Boolean,
    palette: DesignPalette,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) DesignFocusScale else 1f,
        Motion.spatial(), label = "railItemScale"
    )
    // The selected pill scales in behind the icon (450ms emphasized).
    val pillScale by animateFloatAsState(
        if (selected) 1f else 0.85f,
        tween(durationMillis = 450, easing = Motion.Emphasized), label = "railPillScale"
    )
    val focusRing = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp, 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                    .shadow(
                        if (focused) 12.dp else 0.dp,
                        RoundedCornerShape(DsRadius.pill),
                        spotColor = Color.Black.copy(alpha = 0.6f),
                        ambientColor = Color.Black.copy(alpha = 0.6f)
                    )
                    .clip(RoundedCornerShape(DsRadius.pill))
                    .background(if (selected) palette.secondaryContainer else Color.Transparent)
                    .border(
                        if (focused) 3.dp else 0.dp, focusRing,
                        RoundedCornerShape(DsRadius.pill)
                    )
            )
            Icon(
                designIconFor(tab), contentDescription = designLabelFor(tab),
                tint = if (selected) palette.onSecondaryContainer else palette.muted,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            designLabelFor(tab), fontSize = 12.sp,
            color = if (selected) palette.onSurface else palette.muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = AppFont, maxLines = 1
        )
    }
}

/** The Search button under the logo (2.0.0 design): a 40dp circle,
 * onSurfaceVariant icon, same focus treatment as the rail items. */
@Composable
private fun RailCircleButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) DesignFocusScale else 1f,
        Motion.spatial(), label = "railCircleScale"
    )
    val focusRing = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier.size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                if (focused) 12.dp else 0.dp, CircleShape,
                spotColor = Color.Black.copy(alpha = 0.6f),
                ambientColor = Color.Black.copy(alpha = 0.6f)
            )
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .border(if (focused) 3.dp else 0.dp, focusRing, CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
    wifiConnected: Boolean,
    palette: DesignPalette,
    labelMode: RailLabelMode = RailLabelMode.ALWAYS,
    modifier: Modifier = Modifier
) {
    DesignRailContent(
        tab = tab, onTab = onTab, onSearch = onSearch, wifiConnected = wifiConnected,
        palette = palette,
        modifier = modifier.width(DesignRailWidth).fillMaxHeight()
    )
}

/** The rail + content layout (2.0.0 design): the rail is a flat
 * 88dp surface strip on the left — no pill, no border — and the
 * content fills the rest. The same rail on every screen size: the
 * phone gets this rail too (the §11 correction), scrolling when
 * the screen is short. `content` is the tab's screen. */
@Composable
internal fun RailLayout(
    labelMode: RailLabelMode,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    wifiConnected: Boolean,
    palette: DesignPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.width(DesignRailWidth).fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            DesignRail(tab = tab, onTab = onTab, onSearch = onSearch, wifiConnected = wifiConnected, palette = palette, labelMode = labelMode)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}
