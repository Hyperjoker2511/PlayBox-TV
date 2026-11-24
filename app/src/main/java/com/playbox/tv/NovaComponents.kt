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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
// 1.4.3 (enhanced): the focus border is 3dp violet (a clear
// D-pad outline, from the enhanced files), and in LIQUID surface
// mode the fill is translucent with a bright top rim — a small,
// cheap glass look, not a heavy live blur.
@Composable
internal fun NovaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val liquid = remember(context) { surfaceStyle(context) == SurfaceStyle.LIQUID }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.03f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 400f), label = "novaCardScale"
    )
    val fill = if (liquid) NovaSurface.copy(alpha = 0.82f) else NovaSurface
    val borderColor = if (focused) NovaViolet else NovaOutline
    val borderWidth = if (focused) 3.dp else 1.dp
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
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
            .border(if (focused) 3.dp else 1.dp, border, RoundedCornerShape(16.dp))
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
// 1.4.4 (full copy): the file's TV row is 112px tall with a 170px
// thumbnail (90px / 112px on a phone) — sized from the real screen
// width, like the continue card.
@Composable
internal fun NovaRow(
    item: VideoItem,
    resumeFraction: Float = 0f,
    isFav: Boolean = false,
    onToggleFav: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val wide = screenW >= 700.dp
    val rowH = if (wide) 112.dp else 90.dp
    val thumbW = if (wide) 170.dp else 112.dp
    NovaCard(onClick = onOpen, modifier = modifier.fillMaxWidth().height(rowH)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(thumbW).fillMaxHeight()) {
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
// 1.4.4 (full copy): the file's TV card is 350–500px wide and
// 190px tall (310×145 on a phone). The width comes from the real
// screen width (LocalConfiguration), because a LazyRow gives this
// card an unbounded width and BoxWithConstraints cannot see it.
@Composable
internal fun NovaContinueCard(
    item: VideoItem,
    resumeFraction: Float,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val wide = screenW >= 700.dp
    val cardW = if (wide) (screenW * 0.34f).coerceIn(350.dp, 500.dp) else 310.dp
    val cardH = if (wide) 190.dp else 145.dp
    NovaCard(onClick = onOpen, modifier = modifier.width(cardW).height(cardH)) {
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

// 1.4.3 (enhanced): the feature hero — the top of Nova Home. A
// large banner for the featured video (the most recently resumed
// 1.4.4 (full copy): the feature hero — rebuilt to match the
// enhanced Nova file exactly. A large banner: the orbit art on the
// left (a rotated ellipse ring + play mark + "YOUR LIBRARY,
// REIMAGINED" tag) and the copy on the right (eyebrow, big title,
// description, Resume + Details). On a narrow phone it stacks
// (art on top, copy below), like the file's max-width:700px rule.
// The old 1.4.3 hero used the video thumbnail as a full-bleed
// background — the file's hero is the orbit art, not a thumbnail.
@Composable
internal fun NovaHero(
    item: VideoItem,
    resumeFraction: Float,
    resumeMs: Long,
    onResume: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(Color(0xFF17172B), Color(0xFF242044), Color(0xFF171B2A))
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val narrow = maxWidth < 600.dp
        val heroHeight = if (narrow) null else 330.dp
        Box(
            modifier = Modifier.fillMaxWidth()
                .then(if (heroHeight != null) Modifier.height(heroHeight) else Modifier)
                .clip(RoundedCornerShape(30.dp))
                .background(gradient)
                .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(30.dp))
                .clickable { onDetails() }
                .padding(if (narrow) 18.dp else 30.dp)
        ) {
            // Glow: a soft violet circle, top-right (the file's hero-glow).
            Box(
                modifier = Modifier.size(200.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-80).dp)
                    .background(Color(0x2B8B6CFF), CircleShape)
            )
            if (narrow) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NovaHeroArt(modifier = Modifier.fillMaxWidth().height(130.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    NovaHeroCopy(
                        item = item,
                        resumeMs = resumeMs,
                        resumeFraction = resumeFraction,
                        onResume = onResume,
                        onDetails = onDetails
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    NovaHeroArt(modifier = Modifier.weight(1f).fillMaxHeight())
                    NovaHeroCopy(
                        item = item,
                        resumeMs = resumeMs,
                        resumeFraction = resumeFraction,
                        onResume = onResume,
                        onDetails = onDetails,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }
        }
    }
}

// The orbit art: a rotated ellipse ring with a play mark in the
// middle and the "YOUR LIBRARY, REIMAGINED" tag underneath.
@Composable
private fun NovaHeroArt(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // The orbit: a circle border, rotated -22deg and squashed
        // vertically so it reads as an ellipse ring.
        Box(
            modifier = Modifier.size(200.dp)
                .graphicsLayer { rotationZ = -22f; scaleY = 0.55f }
                .border(1.dp, Color(0x80DCCEFFFF), CircleShape)
        )
        Text(
            "▶", fontSize = 64.sp, color = NovaWhite,
            modifier = Modifier.graphicsLayer { shadowElevation = 20f }
        )
        Text(
            "YOUR LIBRARY, REIMAGINED",
            fontSize = 10.sp, color = Color(0x8CFFFFFF),
            letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )
    }
}

// The hero copy: eyebrow, title, description, Resume + Details.
@Composable
private fun NovaHeroCopy(
    item: VideoItem,
    resumeMs: Long,
    resumeFraction: Float,
    onResume: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "FEATURED IN YOUR LIBRARY",
            fontSize = 12.sp, color = Color(0xFFC6B7FF),
            fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            item.name, fontSize = 32.sp, color = NovaWhite,
            fontWeight = FontWeight.Bold, lineHeight = 34.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            if (resumeMs > 0) "A little adventure is waiting. Pick up where you left off or start fresh."
            else "A little adventure is waiting. Pick up where you left off or start fresh.",
            fontSize = 16.sp, color = Color(0xFFC1C2D0), lineHeight = 24.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NovaButton(
                if (resumeMs > 0) "▶  Resume watching" else "▶  Play",
                primary = true, onClick = onResume, modifier = Modifier.height(52.dp)
            )
            NovaButton("ⓘ  Details", onClick = onDetails, modifier = Modifier.height(52.dp))
        }
        if (resumeFraction > 0f) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(5.dp).background(NovaOutline, RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(5.dp)
                        .background(NovaMint, RoundedCornerShape(3.dp))
                )
            }
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
            Text("Zeph", fontSize = 20.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
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
