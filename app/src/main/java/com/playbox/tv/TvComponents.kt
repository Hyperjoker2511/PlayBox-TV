package com.playbox.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class Tab { Home, Search, Library, Settings }

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

// ---------- Batch 3 (Qwen design): glass + focus ----------

/** The one focus treatment for the whole app (Qwen): a focused item
 * scales 1.035–1.05 on a 240ms spring, gains a 2px violet ring and a
 * soft violet glow. Transform + opacity only — nothing heavy, so it
 * stays smooth on the 2 GB box. */
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
    val scale by animateFloatAsState(
        targetValue = if (focused && !reduceMotion) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f),
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
            .border(2.dp, if (focused) FocusBorder else GlassBorder, shape)
            .focusRequester(req).onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .clip(shape),
        content = content
    )
}

/** A glass pill button (tabs, toolbar actions). Active = violet fill. */
@Composable
internal fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f),
        label = "focusScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 12.dp else 0.dp, RoundedCornerShape(28.dp), ambientColor = FocusGlow, spotColor = FocusGlow)
            .then(
                if (active) Modifier.background(Primary, RoundedCornerShape(28.dp))
                else Modifier.background(
                    Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)),
                    RoundedCornerShape(28.dp)
                )
            )
            .border(2.dp, if (focused) FocusBorder else GlassBorder, RoundedCornerShape(28.dp))
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

/** Qwen's top navigation: logo + glass pills. The Player pill opens the
 * video that is playing (or the last one watched). */
@Composable
internal fun TopNav(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("▶", fontSize = 26.sp, color = Primary)
        Text("PlayBox", fontSize = 24.sp, color = White)
        Spacer(modifier = Modifier.width(28.dp))
        val pills = listOf(Tab.Home to "Home", Tab.Library to "Library", Tab.Search to "Search", Tab.Settings to "Settings")
        pills.forEach { (t, label) ->
            GlassButton(label, active = tab == t) { onTab(t) }
        }
        Spacer(modifier = Modifier.weight(1f))
        GlassButton("▶  Player") { onPlayer() }
    }
}

// ---------- Thumbnails (Batch 2, restyled Batch 3) ----------

/** Batch 2: the video's thumbnail, loaded on IO and cached (see
 * MediaLibrary.loadVideoThumbnail). Draws nothing when there is no
 * thumbnail yet — the gradient tile stays underneath. */
@Composable
internal fun VideoThumb(item: VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(item.path) { mutableStateOf(MediaLibrary.ThumbnailCache.get(item.path)) }
    LaunchedEffect(item.path) {
        if (bitmap == null && item.isVideo) {
            bitmap = withContext(Dispatchers.IO) { MediaLibrary.loadVideoThumbnail(context, item) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(24.dp))
        )
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
            Text(item.name, fontSize = 20.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
