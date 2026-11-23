package com.playbox.tv

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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------- Patch 1.3.0 (player restyle) ----------
// The player look from the two pasted designs (liquid-glass tvplayer
// + Prism TV), applied to PlayBox's own player — the system is unchanged
// (Media3 + LibVLC, gear, FTP, PiP). The old panel was a full-width
// sheet about half the phone screen tall; the new dock is a SLIM
// floating capsule (90% wide, max 1160dp) with a gradient seek bar and
// real Material 3 buttons: a big filled play/pause in the middle and
// tonal buttons for everything else (Subtitles, Audio, speed, aspect,
// gear). The focused button is obvious from across the room: it turns
// violet, scales up with a spring and gets a mint border. Transform /
// opacity only, so the 60fps rule still holds — the video surface is
// never animated.

private val Mint = Color(0xFF8EF3DB)
private val Lavender = Color(0xFFC6B6FF)
private val Night = Color(0xFF101524)

/** The top bar from the designs: NOW PLAYING + title + a quality
 * badge. Slim, slides down with the controls. */
@Composable
internal fun PlayerTopBar(title: String, badge: String, visible: Boolean, modifier: Modifier = Modifier) {
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
                    Text("NOW PLAYING", color = Mint, fontSize = 11.sp, letterSpacing = 2.sp)
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

/** A Material 3 tonal button: translucent fill, solid violet + mint
 * border + spring scale when focused. */
@Composable
internal fun M3TonalButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            .border(if (focused) 2.dp else 1.dp, if (focused) Mint else Color(0x35FFFFFF), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The big filled play / pause button: a mint circle with a dark
 * glyph that morphs between play and pause. */
@Composable
internal fun M3PlayButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            .background(Mint)
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

/** The seek bar from the designs: a straight gradient bar (violet to
 * mint) that thickens on focus, a thumb that pops in, and a time
 * bubble that follows it. Left / Right seek by the skip setting. */
@Composable
private fun GlassSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedFraction: Float,
    skipMs: Long,
    onSeekBy: (Long) -> Unit,
    seekFocus: FocusRequester,
    playFocus: FocusRequester,
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
                    brush = Brush.horizontalGradient(listOf(Lavender, Mint), endX = w.coerceAtLeast(1f)),
                    topLeft = Offset(0f, top), size = Size(w, h), cornerRadius = corner
                )
            }
            val r = thumbRadius.toPx()
            if (r > 0.5f) {
                val c = Offset(w, size.height / 2f)
                drawCircle(color = Mint.copy(alpha = 0.35f), radius = r * 1.9f, center = c)
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

/** The slim floating dock: time row, gradient seek bar, then one row
 * of Material 3 buttons. Up from any button enters the timeline;
 * Down from the timeline returns to Play. Slides up with the controls. */
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
    modifier: Modifier = Modifier
) {
    val skipMs = skipSec * 1_000L
    val upToSeek = Modifier.focusProperties { up = seekFocus }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(230)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(170)) { it / 2 },
        modifier = modifier
    ) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(TimeFormatter.format(positionMs), fontSize = 16.sp, color = Color.White)
                    Text(remainingLabel(durationMs, positionMs), fontSize = 16.sp, color = Muted)
                }
                Spacer(Modifier.height(6.dp))
                GlassSeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedFraction = bufferedFraction,
                    skipMs = skipMs,
                    onSeekBy = onSeekBy,
                    seekFocus = seekFocus,
                    playFocus = playFocus,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3TonalButton("-${skipSec}s", onClick = { onSeekBy(-skipMs) }, modifier = upToSeek)
                    M3PlayButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPause,
                        modifier = upToSeek.focusRequester(playFocus)
                    )
                    M3TonalButton("+${skipSec}s", onClick = { onSeekBy(skipMs) }, modifier = upToSeek)
                    M3TonalButton("${speed}x", onClick = onCycleSpeed, modifier = upToSeek)
                    Spacer(Modifier.weight(1f))
                    M3TonalButton(subLabel, onClick = onSubtitles, modifier = upToSeek)
                    M3TonalButton(audioLabel, onClick = onAudio, modifier = upToSeek)
                    M3TonalButton(aspectLabel, onClick = onAspect, modifier = upToSeek)
                    M3TonalButton("⚙", onClick = onGear, modifier = upToSeek)
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, fontSize = 14.sp, color = Muted)
                }
            }
        }
    }
}
