package com.playbox.tv

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------- LUMEN player (2026-11-23) — the assistant's own design ----------
// Lumen is the default player UI: the slim floating capsule from
// Prism, the full transport row from Liquid Glass (Prev / Next
// included), the lavender-to-mint seek rail, a big mint play button,
// and the Lumen glass — layered translucency with a bright rim of
// light along the top edge. Prism and Liquid Glass stay as the two
// other switchable looks. Same player underneath.

private val Lavender = Color(0xFFC6B6FF)
private val Night = Color(0xFF101524)
private val Orange = Color(0xFFFF7A1A)
private val LightOrange = Color(0xFFFFB347)

/** The three player UIs. LUMEN is the default — it is the look 1.3.2
 * ships, so a fresh install sees the assistant's own design. */
internal enum class PlayerUiStyle { LIQUID_GLASS, PRISM, LUMEN }

/** The stored name ("liquid_glass" / "prism" / "lumen") back to a
 * style. Unknown or missing names fall back to LUMEN. (unit-tested) */
internal fun playerUiStyleFromName(name: String?): PlayerUiStyle = when (name) {
    "liquid_glass" -> PlayerUiStyle.LIQUID_GLASS
    "prism" -> PlayerUiStyle.PRISM
    else -> PlayerUiStyle.LUMEN
}

internal fun playerUiStyle(context: Context): PlayerUiStyle =
    playerUiStyleFromName(
        context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
            .getString("player_ui", null)
    )

internal fun savePlayerUiStyle(context: Context, style: PlayerUiStyle) {
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE).edit()
        .putString(
            "player_ui",
            when (style) {
                PlayerUiStyle.LIQUID_GLASS -> "liquid_glass"
                PlayerUiStyle.PRISM -> "prism"
                PlayerUiStyle.LUMEN -> "lumen"
            }
        )
        .apply()
}

/** The rows of the Video information panel (gear "Info" tab): label
 * and value. Pure — the screens supply the already-formatted size
 * and the resolution they know ("" when unknown). (unit-tested) */
internal fun videoInfoRows(
    item: VideoItem,
    sizeLabel: String,
    resolution: String,
    audioCount: Int,
    subCount: Int
): List<Pair<String, String>> = listOf(
    "File" to item.name,
    "Format" to containerOf(item),
    "Size" to sizeLabel,
    "Duration" to TimeFormatter.format(item.durationMs),
    "Resolution" to resolution.ifBlank { "—" },
    "Audio tracks" to audioCount.toString(),
    "Subtitle tracks" to subCount.toString()
)

/** The top bar from the designs. LUMEN and PRISM show the NOW
 * PLAYING label; LIQUID_GLASS shows just the title + badge. Slim,
 * slides down with the controls. */
@Composable
internal fun PlayerTopBar(
    title: String,
    badge: String,
    visible: Boolean,
    style: PlayerUiStyle = PlayerUiStyle.LUMEN,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { -it },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it },
        modifier = modifier
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = (maxWidth * 0.90f).coerceAtMost(1160.dp)
            Row(
                Modifier.width(w).align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    if (style != PlayerUiStyle.LIQUID_GLASS) {
                        Text("NOW PLAYING", color = Mint, fontSize = 11.sp, letterSpacing = 2.sp)
                    }
                    Text(
                        title, color = Color.White, fontSize = 28.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    badge,
                    Modifier.clip(CircleShape).background(Color(0xD926324A))
                        .border(1.dp, Color(0x55FFFFFF), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White, fontSize = 14.sp
                )
            }
        }
    }
}

/** A Material 3 tonal button: translucent fill, solid violet + accent
 * border + spring scale when focused. */
@Composable
internal fun M3TonalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Mint,
    fontSize: TextUnit = 16.sp,
    hPadding: Dp = 14.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.05f else 1f,
        spring(dampingRatio = 0.72f, stiffness = 420f), label = "scale"
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) Primary else Color(0x19FFFFFF))
            .border(if (focused) 2.dp else 1.dp, if (focused) accent else Color(0x35FFFFFF), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = hPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The big filled play / pause button: a filled circle with a dark
 * glyph that morphs between play and pause. Mint on PRISM, white on
 * LIQUID_GLASS. */
@Composable
internal fun M3PlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Mint
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.12f else 1f,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "scale"
    )
    Box(
        modifier = modifier
            .size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(fill)
            .border(if (focused) 2.dp else 0.dp, Primary, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.62f, stiffness = 520f), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                    (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(90)))
            },
            label = "playPause"
        ) { playing ->
            Text(if (playing) "❚❚" else "▶", color = Night, fontSize = 26.sp)
        }
    }
}

/** The seek bar from the designs: a straight gradient bar that
 * thickens on focus, a thumb that pops in, and a time bubble that
 * follows it. Left / Right seek by the skip setting. */
@Composable
private fun GlassSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedFraction: Float,
    skipMs: Long,
    onSeekBy: (Long) -> Unit,
    seekFocus: FocusRequester,
    playFocus: FocusRequester,
    gradient: List<Color>,
    thumbColor: Color,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackHeight by animateDpAsState(if (focused) 10.dp else 4.dp, spring(dampingRatio = 0.62f, stiffness = 520f), label = "track")
    val thumbRadius by animateDpAsState(if (focused) 9.dp else 0.dp, spring(dampingRatio = 0.62f, stiffness = 520f), label = "thumb")

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .focusRequester(seekFocus)
            .focusProperties { down = playFocus }
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekBy(-skipMs); true }
                    Key.DirectionRight -> { onSeekBy(skipMs); true }
                    else -> false
                }
            }
    ) {
        val widthPx = constraints.maxWidth
        val bubbleWidth = 76.dp
        Canvas(Modifier.fillMaxWidth()) {
            val h = trackHeight.toPx().coerceAtLeast(1f)
            val top = (size.height - h) / 2f
            val corner = CornerRadius(h / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = corner
            )
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(0f, top), size = Size(size.width * bufferedFraction, h), cornerRadius = corner
                )
            }
            val w = size.width * fraction
            if (w > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(gradient, endX = w.coerceAtLeast(1f)),
                    topLeft = Offset(0f, top), size = Size(w, h), cornerRadius = corner
                )
            }
            val r = thumbRadius.toPx()
            if (r > 0.5f) {
                val c = Offset(w, size.height / 2f)
                drawCircle(color = thumbColor.copy(alpha = 0.35f), radius = r * 1.9f, center = c)
                drawCircle(color = Color.White, radius = r, center = c)
            }
        }
        AnimatedVisibility(
            visible = focused,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.62f, stiffness = 520f), initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier.offset {
                val bw = bubbleWidth.roundToPx()
                val x = ((widthPx * fraction).roundToInt() - bw / 2)
                    .coerceIn(0, (widthPx - bw).coerceAtLeast(0))
                IntOffset(x, -34.dp.roundToPx())
            }
        ) {
            Box(
                Modifier.width(bubbleWidth)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(TimeFormatter.format(positionMs), color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DockTimeRow(positionMs: Long, durationMs: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(TimeFormatter.format(positionMs), fontSize = 16.sp, color = Color.White)
        Text(remainingLabel(durationMs, positionMs), fontSize = 16.sp, color = Muted)
    }
}

/** The dock: time row, gradient seek bar, then one row of Material 3
 * buttons. Up from any button enters the timeline; Down from the
 * timeline returns to Play. Slides up with the controls. LUMEN is
 * the slim floating capsule with the full transport row (Prev / Next
 * included) and the rim-light glass; PRISM is the 1.3.0 capsule;
 * LIQUID_GLASS is the full-width bottom sheet with Prev / Next. */
@Composable
internal fun PlayerDock(
    positionMs: Long,
    durationMs: Long,
    bufferedFraction: Float,
    isPlaying: Boolean,
    speed: Float,
    skipSec: Int,
    subLabel: String,
    audioLabel: String,
    aspectLabel: String,
    onSeekBy: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onAspect: () -> Unit,
    onGear: () -> Unit,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    visible: Boolean,
    status: String = "",
    style: PlayerUiStyle = PlayerUiStyle.LUMEN,
    onPlayPrev: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val skipMs = skipSec * 1_000L
    val upToSeek = Modifier.focusProperties { up = seekFocus }
    val (gradient, thumbColor, accent) = when (style) {
        PlayerUiStyle.PRISM -> Triple(listOf(Lavender, Mint), Mint, Mint)
        PlayerUiStyle.LUMEN -> Triple(listOf(Lavender, Mint), Mint, Mint)
        PlayerUiStyle.LIQUID_GLASS -> Triple(listOf(Orange, LightOrange), Orange, Orange)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(230)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(170)) { it / 2 },
        modifier = modifier
    ) {
        when (style) {
            PlayerUiStyle.LUMEN -> {
                // Lumen: the slim floating capsule, the full transport
                // row (Prev / Next included, compact buttons so it
                // fits a phone in landscape), and the Lumen glass —
                // layered translucency with a bright rim along the
                // top edge.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = (maxWidth * 0.90f).coerceAtMost(1160.dp)
                    Column(
                        Modifier.width(w).align(Alignment.Center)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)),
                                RoundedCornerShape(28.dp)
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(Color(0xCCFFFFFF), Color(0x24FFFFFF), Color(0x8A8EF3DB))
                                ),
                                RoundedCornerShape(28.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        DockTimeRow(positionMs, durationMs)
                        Spacer(Modifier.height(6.dp))
                        GlassSeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedFraction = bufferedFraction,
                            skipMs = skipMs,
                            onSeekBy = onSeekBy,
                            seekFocus = seekFocus,
                            playFocus = playFocus,
                            gradient = gradient,
                            thumbColor = thumbColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            M3TonalButton(subLabel, onClick = onSubtitles, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            M3TonalButton(audioLabel, onClick = onAudio, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            Spacer(Modifier.weight(1f))
                            if (onPlayPrev != null) M3TonalButton("⏮", onClick = onPlayPrev, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            M3TonalButton("-${skipSec}s", onClick = { onSeekBy(-skipMs) }, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            M3PlayButton(
                                isPlaying = isPlaying,
                                onClick = onPlayPause,
                                modifier = upToSeek.focusRequester(playFocus),
                                fill = Mint
                            )
                            M3TonalButton("+${skipSec}s", onClick = { onSeekBy(skipMs) }, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            if (onPlayNext != null) M3TonalButton("⏭", onClick = onPlayNext, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            Spacer(Modifier.weight(1f))
                            M3TonalButton("${speed}x", onClick = onCycleSpeed, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            M3TonalButton(aspectLabel, onClick = onAspect, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                            M3TonalButton("⚙", onClick = onGear, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        }
                        if (status.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(status, fontSize = 14.sp, color = Muted)
                        }
                    }
                }
            }
            PlayerUiStyle.PRISM -> {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = (maxWidth * 0.90f).coerceAtMost(1160.dp)
                    Column(
                        Modifier.width(w).align(Alignment.Center)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xE32C3654), Color(0xEA141C30), Color(0xF0193034))
                                )
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(Color(0x9AFFFFFF), Color(0x24FFFFFF), Color(0x757CF0DC))
                                ),
                                RoundedCornerShape(28.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        DockTimeRow(positionMs, durationMs)
                        Spacer(Modifier.height(6.dp))
                        GlassSeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedFraction = bufferedFraction,
                            skipMs = skipMs,
                            onSeekBy = onSeekBy,
                            seekFocus = seekFocus,
                            playFocus = playFocus,
                            gradient = gradient,
                            thumbColor = thumbColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            M3TonalButton("-${skipSec}s", onClick = { onSeekBy(-skipMs) }, modifier = upToSeek, accent = accent)
                            M3PlayButton(
                                isPlaying = isPlaying,
                                onClick = onPlayPause,
                                modifier = upToSeek.focusRequester(playFocus),
                                fill = Mint
                            )
                            M3TonalButton("+${skipSec}s", onClick = { onSeekBy(skipMs) }, modifier = upToSeek, accent = accent)
                            M3TonalButton("${speed}x", onClick = onCycleSpeed, modifier = upToSeek, accent = accent)
                            Spacer(Modifier.weight(1f))
                            M3TonalButton(subLabel, onClick = onSubtitles, modifier = upToSeek, accent = accent)
                            M3TonalButton(audioLabel, onClick = onAudio, modifier = upToSeek, accent = accent)
                            M3TonalButton(aspectLabel, onClick = onAspect, modifier = upToSeek, accent = accent)
                            M3TonalButton("⚙", onClick = onGear, modifier = upToSeek, accent = accent)
                        }
                        if (status.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(status, fontSize = 14.sp, color = Muted)
                        }
                    }
                }
            }
            PlayerUiStyle.LIQUID_GLASS -> {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)),
                            RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                        .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    DockTimeRow(positionMs, durationMs)
                    Spacer(Modifier.height(6.dp))
                    GlassSeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        bufferedFraction = bufferedFraction,
                        skipMs = skipMs,
                        onSeekBy = onSeekBy,
                        seekFocus = seekFocus,
                        playFocus = playFocus,
                        gradient = gradient,
                        thumbColor = thumbColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        M3TonalButton(subLabel, onClick = onSubtitles, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        M3TonalButton(audioLabel, onClick = onAudio, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        Spacer(Modifier.weight(1f))
                        if (onPlayPrev != null) M3TonalButton("⏮", onClick = onPlayPrev, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        M3TonalButton("-${skipSec}s", onClick = { onSeekBy(-skipMs) }, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        M3PlayButton(
                            isPlaying = isPlaying,
                            onClick = onPlayPause,
                            modifier = upToSeek.focusRequester(playFocus),
                            fill = Color.White
                        )
                        M3TonalButton("+${skipSec}s", onClick = { onSeekBy(skipMs) }, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        if (onPlayNext != null) M3TonalButton("⏭", onClick = onPlayNext, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        Spacer(Modifier.weight(1f))
                        M3TonalButton("${speed}x", onClick = onCycleSpeed, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                        M3TonalButton("⚙", onClick = onGear, modifier = upToSeek, accent = accent, fontSize = 14.sp, hPadding = 12.dp)
                    }
                    if (status.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(status, fontSize = 14.sp, color = Muted)
                    }
                }
            }
        }
    }
}
