package com.playbox.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------- P3 — FULL-BLEED HOME HERO + CONTINUE PILL + CLOCK ----------
// (2026-11-24, plan v2 Patch 3)
// Home gets one shared full-bleed hero (StageHero) across Nova, Qwen
// and Aura, replacing the three per-design heroes (NovaHero's
// gradient + orbit art, AuraHero's gradient + poster, Qwen's
// HeroBanner). The video's cover fills the hero edge-to-edge behind
// its details (title, duration, size, Play / Details); when there
// is no thumbnail it falls back to a palette gradient, never a fake
// poster. Top-right: a Continue pill (Resume / Play) and a large
// live clock. The hero folds to one line when the user moves into
// the rows (HeroFoldedBar, sticky) and ▲ from the first row unfolds
// it (scrolling back shows the full hero again). The palette keeps
// each design's own accent, so the hero is shared but not identical.
// Transform + opacity only; thumbnails load off the main thread
// (the same cache/loader VideoThumb uses).

/** The video's cover, full-bleed. Thumbnail when there is one,
 * palette gradient when there is not. No rounded corners here —
 * the hero container supplies its own clip. */
@Composable
private fun HeroBackdrop(
    item: VideoItem,
    palette: DesignPalette,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(item.path) { mutableStateOf(MediaLibrary.ThumbnailCache.get(item.path)) }
    LaunchedEffect(item.path) {
        if (bitmap == null && item.isVideo) {
            bitmap = withContext(Dispatchers.IO) { MediaLibrary.loadVideoThumbnail(context, item) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bmp.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    listOf(palette.primaryContainer, palette.primary, palette.bg)
                )
            )
        )
    }
}

/** A large live clock (HH:mm). The state lives inside this
 * composable so only it recomposes, once a second. */
@Composable
internal fun HomeClock(
    color: Color,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = java.time.LocalTime.now()
        }
    }
    Text(
        now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
        fontSize = 30.sp, color = color, fontWeight = FontWeight.Bold,
        fontFamily = AppFont, modifier = modifier
    )
}

/** The Continue pill: Resume (with where you left off) or Play.
 * Tapping it plays the hero video. */
@Composable
private fun ContinuePill(
    resumeMs: Long,
    palette: DesignPalette,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DsRadius.pill))
            .background(palette.primary)
            .clickable { onPlay() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null,
                tint = palette.onPrimary, modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (resumeMs > 0) "Resume ${TimeFormatter.format(resumeMs)}" else "Play",
                fontSize = 16.sp, color = palette.onPrimary,
                fontWeight = FontWeight.SemiBold, fontFamily = AppFont, maxLines = 1
            )
        }
    }
}

/** The full-bleed hero: the focused video's cover fills the hero
 * behind its details. Top-right: Continue pill + large clock.
 * Bottom-left: title, meta (duration • size • container), Play /
 * Resume + Details. Tapping the hero opens Details. */
@Composable
internal fun StageHero(
    item: VideoItem,
    resumeFraction: Float,
    resumeMs: Long,
    palette: DesignPalette,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= 700.dp
        val heroHeight = if (wide) 440.dp else 320.dp
        Box(
            modifier = Modifier.fillMaxWidth().height(heroHeight)
                .clip(RoundedCornerShape(DsRadius.sheet))
                .clickable { onDetails() }
        ) {
            HeroBackdrop(item = item, palette = palette, modifier = Modifier.fillMaxSize())
            // Dark scrims so the title (bottom) and the pill + clock
            // (top) stay readable on any cover.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x9905070C), Color.Transparent, Color(0x9905070C), Color(0xE605070C))
                    )
                )
            )
            // Top-right cluster: Continue pill + large clock.
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContinuePill(resumeMs = resumeMs, palette = palette, onPlay = onPlay)
                HomeClock(color = Color.White)
            }
            // Bottom-left: title, meta, actions.
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (resumeMs > 0) "CONTINUE WATCHING" else "FEATURED IN YOUR LIBRARY",
                    fontSize = 14.sp, color = palette.primary,
                    fontWeight = FontWeight.Bold, fontFamily = AppFont
                )
                Text(
                    item.name, fontSize = if (wide) 40.sp else 30.sp,
                    color = Color.White, fontWeight = FontWeight.Bold, fontFamily = AppFont,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                    containerOf(item).ifBlank { null },
                    formatSize(item.sizeBytes).ifBlank { null }
                ).joinToString("  •  ")
                if (meta.isNotEmpty()) {
                    Text(meta, fontSize = 16.sp, color = Color(0xD2FFFFFF), fontFamily = AppFont)
                }
                if (resumeMs > 0 && resumeFraction > 0f) {
                    Box(
                        modifier = Modifier.width(220.dp).height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x55FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(resumeFraction.coerceIn(0f, 1f))
                                .height(5.dp)
                                .background(palette.primary)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(DsRadius.button))
                            .background(palette.primary)
                            .clickable { onPlay() }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = palette.onPrimary, modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (resumeMs > 0) "Resume" else "Play",
                                fontSize = 17.sp, color = palette.onPrimary,
                                fontWeight = FontWeight.SemiBold, fontFamily = AppFont
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(DsRadius.button))
                            .background(Color(0x33FFFFFF))
                            .clickable { onDetails() }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Details", fontSize = 17.sp, color = Color.White,
                            fontWeight = FontWeight.SemiBold, fontFamily = AppFont
                        )
                    }
                }
            }
        }
    }
}

/** The folded hero: one line (play mark + title + resume time).
 * Shown sticky at the top once the full hero has scrolled off.
 * It is not focusable — ▲ from the first row scrolls back to the
 * full hero and unfolds it, which is the plan's behaviour. */
@Composable
internal fun HeroFoldedBar(
    item: VideoItem,
    resumeMs: Long,
    palette: DesignPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DsRadius.button))
            .background(palette.glassStrong)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(palette.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null,
                tint = palette.onPrimary, modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            item.name, fontSize = 17.sp, color = palette.onSurface,
            fontWeight = FontWeight.SemiBold, fontFamily = AppFont,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (resumeMs > 0) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Resume ${TimeFormatter.format(resumeMs)}",
                fontSize = 15.sp, color = palette.muted, fontFamily = AppFont, maxLines = 1
            )
        }
    }
}
