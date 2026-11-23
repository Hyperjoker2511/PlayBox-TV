package com.playbox.tv

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- AURA components (2026-11-23, 1.4.5 Patch 2) ----------
// The clean Aura file's components, rebuilt in Compose. Material 3
// Expressive: lavender primary on near-black, 32dp hero / 20dp card
// / 999dp chip / 16dp button radii. Focus is the Aura signature —
// a 3dp lavender ring + scale 1.08 + a small rise, the Compose
// equivalent of the file's gliding #focus-ring. Motion is
// transform + opacity only, so it stays smooth on the 2 GB box.

// A filled or tonal button. Filled = lavender bg, dark text.
// Tonal = surface4 bg, outline border, light text. 48dp tall,
// 16dp corners. onClick is last so a trailing lambda works.
@Composable
internal fun AuraButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 400f), label = "auraBtnScale"
    )
    val bg = if (filled) AuraPrimary else AuraSurface4
    val fg = if (filled) AuraOnPrimary else AuraOnSurface
    val border = if (filled) AuraPrimary else if (focused) AuraPrimary else AuraOutlineVariant
    Box(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(if (focused) 3.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// A filter chip. 32dp tall, pill-shaped. Active = primary-container
// bg with a lavender border; idle = surface3 bg, outline border.
@Composable
internal fun AuraChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.05f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 400f), label = "auraChipScale"
    )
    val bg = if (active) AuraPrimaryContainer else AuraSurface3
    val fg = if (active) AuraOnSurface else AuraOnSurfaceVariant
    val border = if (focused || active) AuraPrimary else AuraOutlineVariant
    Box(
        modifier = modifier
            .height(32.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(999.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// A row header: a 22sp title on the left, a "See all" link on the
// right (lavender). The file's .row-header.
@Composable
internal fun AuraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 22.sp, color = AuraOnSurface, fontWeight = FontWeight.SemiBold)
        if (onSeeAll != null) {
            Text(
                "See all", fontSize = 13.sp, color = AuraPrimary,
                modifier = Modifier.clickable { onSeeAll() }
            )
        }
    }
}

// A video card. 16:9 (280x158) or poster (180x270), 20dp corners,
// a gradient cover with the title overlaid at the bottom and a
// 3px lavender progress bar when there is a resume position.
// Focus: scale 1.08 + rise 6dp + 3dp lavender ring + shadow —
// the file's .card.focusable.is-focused, in Compose.
@Composable
internal fun AuraCard(
    item: VideoItem,
    resumeFraction: Float,
    poster: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 350f), label = "auraCardScale"
    )
    val rise by animateFloatAsState(
        if (focused) -6f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 350f), label = "auraCardRise"
    )
    val w = if (poster) 180.dp else 280.dp
    val h = if (poster) 270.dp else 158.dp
    Box(
        modifier = modifier
            .size(w, h)
            .graphicsLayer { scaleX = scale; scaleY = scale; translationY = rise }
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(AuraPosterGradStart, AuraPosterGradEnd)
                )
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) AuraPrimary else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        // Real thumbnail where one exists, under the title scrim.
        VideoThumb(item, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.ui.graphics.Color(0xB3000000)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)
        ) {
            Text(
                item.name, color = AuraOnSurface, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val sub = tileSub(item)
            if (sub != null) {
                Text(sub, color = AuraOnSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (resumeFraction > 0f) {
            Box(
                modifier = Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth(resumeFraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(AuraPrimary)
            )
        }
    }
}

// The top bar: a live clock + location on the left, mic + flag
// icon buttons on the right. The file's .top-bar.
@Composable
internal fun AuraTopBar(
    modifier: Modifier = Modifier,
    onMic: () -> Unit = {},
    onFlag: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(greeting(), fontSize = 14.sp, color = AuraOnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(AuraSurface3)
                    .border(1.dp, AuraOutlineVariant, CircleShape)
                    .clickable { onMic() },
                contentAlignment = Alignment.Center
            ) { Text("🎤", fontSize = 18.sp) }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(AuraSurface3)
                    .border(1.dp, AuraOutlineVariant, CircleShape)
                    .clickable { onFlag() },
                contentAlignment = Alignment.Center
            ) { Text("⚑", fontSize = 18.sp) }
        }
    }
}

// The hero: a 480dp cinematic banner — purple gradient, a badge,
// a big title, meta, description, Play / + My List / Info actions
// and a poster card on the right, tilted 3deg, straightening +
// scaling on focus. The file's .hero, rebuilt around the real
// library (no fake movie data, no IMDb, no 4K/Dolby badges).
@Composable
internal fun AuraHero(
    item: VideoItem,
    resumeFraction: Float,
    resumeMs: Long,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val posterRot by animateFloatAsState(
        if (focused) 0f else 3f,
        spring(dampingRatio = 0.7f, stiffness = 300f), label = "auraPosterRot"
    )
    val posterScale by animateFloatAsState(
        if (focused) 1.03f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 300f), label = "auraPosterScale"
    )
    val badge = if (resumeMs > 0) "✦ FEATURED • RESUME ${TimeFormatter.format(resumeMs)}" else "✦ FEATURED IN YOUR LIBRARY"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(AuraHeroGradStart, AuraHeroGradMid, AuraHeroGradEnd)
                )
            )
            .clickable(interactionSource = interaction, indication = null) { onDetails() }
            .padding(48.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(androidx.compose.ui.graphics.Color(0x1FFFFFFF))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(badge, fontSize = 12.sp, color = AuraOnSurface)
                }
                Text(
                    item.name, fontSize = 56.sp, color = AuraOnSurface,
                    fontWeight = FontWeight.Bold, lineHeight = 54.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val sub = tileSub(item)
                if (sub != null) {
                    Text(sub, fontSize = 14.sp, color = AuraOnSurfaceVariant)
                }
                Text(
                    containerOf(item), fontSize = 16.sp, color = AuraOnSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuraButton(
                        if (resumeMs > 0) "▶ Resume" else "▶ Play",
                        filled = true, onClick = onPlay
                    )
                    AuraButton("+ My List", onClick = onDetails)
                    AuraButton("ⓘ Info", onClick = onDetails)
                }
                if (resumeFraction > 0f) {
                    Box(
                        modifier = Modifier.fillMaxWidth(0.6f).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(androidx.compose.ui.graphics.Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AuraPrimary)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
            Box(
                modifier = Modifier.weight(0.45f).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(320.dp, 420.dp)
                        .graphicsLayer { rotationZ = posterRot; scaleX = posterScale; scaleY = posterScale }
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(AuraPosterGradStart, AuraPosterGradEnd)
                            )
                        )
                        .border(1.dp, androidx.compose.ui.graphics.Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                ) {
                    VideoThumb(item, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.name, color = AuraOnSurface, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (resumeFraction > 0f) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomStart)
                                .fillMaxWidth(resumeFraction.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(AuraPrimary)
                        )
                    }
                }
            }
        }
    }
}

// The expanding left rail: 96dp icon-only, grows to 280dp with
// labels on hover/focus. The logo is the gradient "A" tile +
// "AURA TV"; the nav items are Home / Search / Explore / Library
// / Settings; the profile circle sits at the bottom. The file's
// #nav-rail, in Compose.
@Composable
internal fun AuraRail(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val width by androidx.compose.animation.core.animateDpAsState(
        if (expanded) 280.dp else 96.dp,
        spring(dampingRatio = 0.85f, stiffness = 300f), label = "auraRailWidth"
    )
    Column(
        modifier = modifier.width(width).fillMaxHeight()
            .background(AuraSurface2)
            .border(1.dp, AuraOutlineVariant)
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(AuraPrimary, AuraTertiary))),
                contentAlignment = Alignment.Center
            ) { Text("A", fontSize = 18.sp, color = AuraOnPrimary, fontWeight = FontWeight.Bold) }
            if (expanded) {
                Spacer(modifier = Modifier.width(12.dp))
                Text("AURA TV", fontSize = 18.sp, color = AuraOnSurface, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        listOf(
            Tab.Home to ("⌂" to "Home"),
            Tab.Search to ("⌕" to "Search"),
            Tab.Explore to ("◈" to "Explore"),
            Tab.Library to ("♡" to "Library"),
            Tab.Settings to ("⚙" to "Settings")
        ).forEach { (t, pair) ->
            val (icon, label) = pair
            val selected = tab == t
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val scale by animateFloatAsState(
                if (focused) 1.05f else 1f,
                spring(dampingRatio = 0.75f, stiffness = 400f), label = "auraRailScale"
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (selected) AuraPrimaryContainer else AuraSurface2)
                    .border(
                        if (focused) 2.dp else 0.dp, AuraPrimary,
                        RoundedCornerShape(28.dp)
                    )
                    .clickable(interactionSource = interaction, indication = null) { onTab(t) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(icon, fontSize = 20.sp, color = if (selected) AuraOnSurface else AuraOnSurfaceVariant)
                    if (expanded) {
                        Text(label, fontSize = 14.sp, color = AuraOnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(AuraSecondary, AuraTertiary)))
                .clickable { onPlayer() },
            contentAlignment = Alignment.Center
        ) { Text("▶", fontSize = 18.sp, color = AuraOnPrimary) }
    }
}

// The bottom nav for a narrow phone: 72dp, five items, the file's
// .bottom-nav. The rail hides under 700dp and this takes over.
@Composable
internal fun AuraBottomNav(
    tab: Tab,
    onTab: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(72.dp)
            .background(AuraSurface2)
            .border(1.dp, AuraOutlineVariant)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            Tab.Home to ("⌂" to "Home"),
            Tab.Search to ("⌕" to "Search"),
            Tab.Explore to ("◈" to "Explore"),
            Tab.Library to ("♡" to "Library"),
            Tab.Settings to ("⚙" to "Settings")
        ).forEach { (t, pair) ->
            val (icon, label) = pair
            val selected = tab == t
            Column(
                modifier = Modifier.clickable { onTab(t) }.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(icon, fontSize = 22.sp, color = if (selected) AuraPrimary else AuraOnSurfaceVariant)
                Text(label, fontSize = 10.sp, color = if (selected) AuraPrimary else AuraOnSurfaceVariant)
            }
        }
    }
}
