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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- NOVA components (2026-11-23) ----------
// Solid, flat, editorial. No glass, no blur, no rim light — a solid
// fill, a 1dp outline, and a solid violet focus ring. The focus
// scale is small (1.03) and the motion is transform + opacity only.

// A solid card: NovaSurface fill, 1dp NovaOutline border, 20dp
// corners. The Nova replacement for GlassSurface.
@Composable
internal fun NovaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.03f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 400f), label = "novaCardScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(NovaSurface)
            .border(if (focused) 2.dp else 1.dp, if (focused) NovaViolet else NovaOutline, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// A solid button: violet fill (primary) or an outline (secondary).
// 16dp corners, 18sp, white text. The Nova replacement for
// GlassButton / TvButton. onClick is last so a trailing lambda
// works, like GlassButton.
@Composable
internal fun NovaButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.04f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 400f), label = "novaBtnScale"
    )
    val bg = when {
        primary || active -> NovaViolet
        else -> NovaSurface
    }
    val border = if (focused) NovaViolet else if (primary || active) NovaViolet else NovaOutline
    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = NovaWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// A small pill for filters and modes. onClick is last so a
// trailing lambda works.
@Composable
internal fun NovaChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) NovaViolet else NovaSurface)
            .border(if (focused) 2.dp else 1.dp, if (focused) NovaViolet else NovaOutline, RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = NovaWhite, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

// A big bold section header with an optional count and action.
@Composable
internal fun NovaSectionHeader(
    title: String,
    count: String? = null,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 26.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(count, fontSize = 16.sp, color = NovaMuted)
        }
        Spacer(modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// A video list row: 16:9 thumbnail on the left, title + meta in the
// middle, duration + a play chevron on the right, and a thin mint
// resume bar along the bottom. The Nova replacement for ThumbTile
// (grid) and ListRow (glass) — Nova lists, it does not grid.
@Composable
internal fun NovaRow(
    item: VideoItem,
    resumeFraction: Float = 0f,
    isFav: Boolean = false,
    onToggleFav: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    NovaCard(onClick = onOpen, modifier = modifier.fillMaxWidth().height(96.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(140.dp).fillMaxHeight()) {
                    if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
                    else Box(
                        Modifier.fillMaxSize().background(NovaSurfaceHigh, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("♫", fontSize = 28.sp, color = NovaMint) }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(item.name, fontSize = 20.sp, color = NovaWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(tileSub(item) ?: "", fontSize = 15.sp, color = NovaMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onToggleFav != null) {
                    NovaButton(if (isFav) "★" else "☆", onClick = onToggleFav, modifier = Modifier.height(44.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("▶", fontSize = 20.sp, color = NovaMint)
            }
            if (resumeFraction > 0f) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                        .background(NovaOutline)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(4.dp)
                            .background(NovaMint)
                    )
                }
            }
        }
    }
}

// A wide continue-watching card: cover on the left, title + progress
// + duration on the right, and a circular violet play button. Used
// in the Home carousel — one card per in-progress video.
@Composable
internal fun NovaContinueCard(
    item: VideoItem,
    resumeFraction: Float,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    NovaCard(onClick = onOpen, modifier = modifier.width(340.dp).height(150.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(150.dp).fillMaxHeight()) {
                if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text("CONTINUE", fontSize = 12.sp, color = NovaMint, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(item.name, fontSize = 18.sp, color = NovaWhite, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                        if (resumeFraction > 0f) "${(resumeFraction * 100).toInt()}% watched" else null
                    ).joinToString("  •  "),
                    fontSize = 14.sp, color = NovaMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp).background(NovaOutline, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(4.dp)
                            .background(NovaMint, RoundedCornerShape(2.dp))
                    )
                }
            }
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(NovaViolet)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) { Text("▶", fontSize = 22.sp, color = NovaWhite) }
        }
    }
}

// A folder / file row: an icon, the name, and a chevron. The Nova
// replacement for FolderRow. onClick is last so a trailing lambda
// works.
@Composable
internal fun NovaFolderRow(
    text: String,
    icon: String = "▤",
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NovaCard(onClick = onClick, modifier = modifier.fillMaxWidth().height(64.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp, color = NovaMint)
            Spacer(modifier = Modifier.width(14.dp))
            Text(text, fontSize = 18.sp, color = NovaWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("›", fontSize = 22.sp, color = NovaMuted)
        }
    }
}

// ---------- Nova navigation ----------
// The left rail (wide screens): the logo at the top, four nav items
// stacked vertically with a big icon + label, and the Player button
// pinned at the bottom. The selected item is a solid violet fill.
// This is the Nova nav — Qwen uses a top pill bar instead.
@Composable
internal fun NovaRail(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(208.dp).fillMaxHeight()
            .background(NovaSurface)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("▶", fontSize = 24.sp, color = NovaMint)
            Spacer(modifier = Modifier.width(10.dp))
            Text("PlayBox", fontSize = 22.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
        listOf(
            Tab.Home to ("⌂" to "Home"),
            Tab.Search to ("⌕" to "Search"),
            Tab.Library to ("▣" to "Library"),
            Tab.Settings to ("⚙" to "Settings")
        ).forEach { (t, pair) ->
            val (icon, label) = pair
            val selected = tab == t
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) NovaViolet else NovaSurface)
                    .border(1.dp, if (selected) NovaViolet else NovaOutline, RoundedCornerShape(14.dp))
                    .clickable { onTab(t) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 20.sp, color = NovaWhite)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, fontSize = 18.sp, color = NovaWhite, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        NovaButton("▶  Player", onClick = onPlayer, primary = true, modifier = Modifier.fillMaxWidth())
    }
}

// The top tab strip (narrow screens): four equal tabs with a solid
// violet underline under the selected one, and a play button at the
// end. Same four tabs as the rail, laid out horizontally.
@Composable
internal fun NovaTabStrip(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(NovaSurface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▶", fontSize = 22.sp, color = NovaMint)
            Spacer(modifier = Modifier.width(8.dp))
            Text("PlayBox", fontSize = 20.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(NovaViolet)
                    .clickable { onPlayer() },
                contentAlignment = Alignment.Center
            ) { Text("▶", fontSize = 18.sp, color = NovaWhite) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf(
                Tab.Home to "Home",
                Tab.Search to "Search",
                Tab.Library to "Library",
                Tab.Settings to "Settings"
            ).forEach { (t, label) ->
                val selected = tab == t
                Column(
                    modifier = Modifier.weight(1f).clickable { onTab(t) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, fontSize = 16.sp, color = if (selected) NovaWhite else NovaMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.width(48.dp).height(3.dp)
                            .background(if (selected) NovaViolet else NovaOutline, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
