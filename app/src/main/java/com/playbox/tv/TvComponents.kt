package com.playbox.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class Tab { Home, Video, Audio, Browse, Playlists, Settings, Search }

/** Tile subtitle: duration plus a "May not play" badge for formats
 * Media3 cannot play yet (Batch 1.4 polish). Null when there is nothing
 * to show, so the tile stays clean. */
internal fun tileSub(item: VideoItem): String? {
    val parts = listOfNotNull(
        if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
        if (item.likelyUnsupported) "May not play" else null
    )
    return parts.joinToString("  ·  ").ifBlank { null }
}

// ---------- LUMEN (2026-11-23): glass + focus ----------

/** The one focus treatment for the whole app (Lumen): a focused item
 * scales 1.04 on a spring, gains a 3px violet ring and a soft violet
 * glow, and every glass surface carries a bright rim of light along
 * its top edge — the detail that makes glass read as glass without
 * any live blur. Transform + opacity only, so it stays smooth on the
 * 2 GB box and the phone. v7: same numbers as zephFocus (S6) — one
 * treatment, not two. */
@Composable
internal fun GlassSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    // Patch 6: reduce motion skips the focus zoom.
    val surfaceContext = LocalContext.current
    val reduceMotion = remember(surfaceContext) { isReduceMotion(surfaceContext) }
    // v7: the focus growth is 1.04, the same as zephFocus — fast
    // D-pad travel makes the growth chase the focus from card to
    // card instead of cutting.
    val scale by animateFloatAsState(
        targetValue = if (focused && !reduceMotion) 1.04f else 1f,
        animationSpec = Motion.bouncy(),
        label = "focusScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 16.dp else 0.dp, shape, ambientColor = FocusGlow, spotColor = FocusGlow)
            .background(
                Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)),
                shape
            )
            .border(if (focused) 3.dp else 2.dp, if (focused) FocusBorder else GlassBorder, shape)
            .focusRequester(req).onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .clip(shape),
    ) {
        // Lumen rim light: a bright catch along the top edge.
        Box(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, GlassRim, Color.Transparent)
                    )
                )
        )
        content()
    }
}

/** A glass pill button (tabs, toolbar actions). Active = violet fill.
 * Primary = the "go" button (Play, Resume) — 1.4.0: violet fill with
 * white text, the app's one accent; it reads as primary by its fill
 * and its glow, not by a second colour.
 * Focus scales 1.08 on the Bouncy spring with a violet ring. */
@Composable
internal fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    val buttonContext = LocalContext.current
    val reduceMotion = remember(buttonContext) { isReduceMotion(buttonContext) }
    val scale by animateFloatAsState(
        targetValue = if (focused && !reduceMotion) 1.04f else 1f,
        animationSpec = Motion.bouncy(),
        label = "focusScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 12.dp else 0.dp, RoundedCornerShape(28.dp), ambientColor = FocusGlow, spotColor = FocusGlow)
            .then(
                when {
                    primary -> Modifier.background(Primary, RoundedCornerShape(28.dp))
                    active -> Modifier.background(Primary, RoundedCornerShape(28.dp))
                    else -> Modifier.background(
                        Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)),
                        RoundedCornerShape(28.dp)
                    )
                }
            )
            .border(3.dp, if (focused) FocusBorder else GlassBorder, RoundedCornerShape(28.dp))
            .focusRequester(req).onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 20.sp, color = White, maxLines = 1)
    }
}

/** A big dashboard tile: glyph, title, subtitle. (Glyphs are plain text
 * symbols, not emoji — the app has no icon font.) */
@Composable
internal fun GlassTile(
    glyph: String,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassSurface(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(glyph, fontSize = 32.sp, color = Periwinkle)
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 22.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(sub, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Lumen quick chip: one compact glass pill (glyph + label) for the
 * Home quick-actions row. The row scrolls horizontally, so on a phone
 * in landscape nothing is ever clipped or pushed off the edge. */
@Composable
internal fun QuickChip(
    glyph: String,
    label: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(glyph, fontSize = 26.sp, color = Primary)
            Column {
                Text(label, fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 15.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The remote hints bar at the bottom of browse screens (Qwen). */
@Composable
internal fun RemoteHints(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Text("← →  Navigate", fontSize = 16.sp, color = Muted)
        Text("OK  Select", fontSize = 16.sp, color = Muted)
        Text("Back  Exit", fontSize = 16.sp, color = Muted)
        Text("▶  Play / Pause", fontSize = 16.sp, color = Muted)
    }
}

// ---------- Thumbnails (Batch 2, restyled Batch 3) ----------

/** Batch 2: the video's thumbnail, loaded on IO and cached (see
 * MediaLibrary.loadVideoThumbnail). Draws nothing when there is no
 * thumbnail yet — the gradient tile stays underneath. */
/** 2.0.3: a beautiful backdrop when a video has no thumbnail.
 * VideoThumb used to draw nothing when the thumbnail failed,
 * which left the hero and the cards as bare dark boxes. This
 * gradient is never bare — it always gives the poster a
 * finished, premium look, with the title on top. */
@Composable
internal fun PosterBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )
    ) {
        // A soft light bloom, top-left — gives the flat gradient
        // depth, like a poster backdrop.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(0.2f, 0.15f),
                    radius = 900f
                )
            )
        )
    }
}

@Composable
internal fun VideoThumb(item: VideoItem, modifier: Modifier = Modifier, width: Int = 320, height: Int = 180) {
    val context = LocalContext.current
    // 2.0.3: the cache key carries the size (see
    // loadVideoThumbnail) so a small card thumb is never served
    // to the big hero poster.
    val cacheKey = "${item.path}:${width}x$height"
    var bitmap by remember(cacheKey) { mutableStateOf(MediaLibrary.ThumbnailCache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (bitmap == null && item.isVideo) {
            bitmap = withContext(Dispatchers.IO) { MediaLibrary.loadVideoThumbnail(context, item, width, height) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(24.dp))
        )
    } else {
        // 2.0.3: never a bare box — the gradient backdrop while
        // the thumbnail loads, and forever if it can't load.
        PosterBackdrop(modifier.clip(RoundedCornerShape(24.dp)))
    }
}

/** A video tile: thumbnail, duration badge, title + meta, and a thin
 * resume progress bar when the video was partly watched. */
@Composable
internal fun ThumbTile(
    item: VideoItem,
    sub: String? = null,
    resumeFraction: Float = 0f,
    onClick: () -> Unit
) {
    GlassSurface(onClick = onClick, modifier = Modifier.width(220.dp).height(180.dp)) {
        if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
        // Dark scrim at the bottom so the title stays readable on a photo.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, Color(0xCC000000))
                )
            )
        )
        if (item.durationMs > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .background(GlassStrong, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(TimeFormatter.format(item.durationMs), fontSize = 14.sp, color = White)
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            // 2.0.3: clean display title, not the raw file name.
            Text(item.displayTitle, fontSize = 20.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(sub, fontSize = 15.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (resumeFraction > 0f) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                    .background(Color(0x44FFFFFF))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(4.dp)
                        .background(Primary)
                )
            }
        }
    }
}

/** 1.4.0 (Nocturne TV): the settings list-door row — a full-width
 * glass card with a coloured icon tile, a bold title, a one-line
 * grey description and the current value at the right edge. The
 * shape the owner picked for settings lists: every door labelled,
 * nothing hidden behind a bare toggle. */
@Composable
internal fun SettingsRow(
    glyph: String,
    title: String,
    sub: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier.background(SecondaryContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, fontSize = 24.sp, color = Periwinkle)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 22.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(3.dp))
                Text(sub, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) {
                Text(value, fontSize = 20.sp, color = Primary, maxLines = 1)
            }
        }
    }
}

// ---------- Backwards-compatible names (used by screens not yet rebuilt) ----------

@Composable
internal fun TvTile(text: String, sub: String? = null, onClick: () -> Unit) {
    GlassSurface(onClick = onClick, modifier = Modifier.width(220.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text, fontSize = 22.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(sub, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun TvButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassButton(text, modifier = modifier) { onClick() }
}
