package com.playbox.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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

// ---------- ZEPH Player dock + HUD (M3 redesign, from scratch) ----------
// ONE bottom dock only — no top bar. The dock is a single M3 style
// (violet accent, surface container), not the five PlayerUiStyles.
// The HUD at the top carries what the old top bar did (title +
// stream info) plus the live clock (plan v2 P8). All transform +
// opacity only, so it stays cheap on the 2 GB box.

/** The HUD: title + stream info on the left, live clock on the
 * right. Shown with the controls, hidden with them. */
@Composable
internal fun M3PlayerHud(
    title: String,
    badge: String,
    streamInfo: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.soft()) + slideInVertically(Motion.soft()) { -it },
        exit = fadeOut(Motion.soft()) + slideOutVertically(Motion.soft()) { -it },
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
                    Text(
                        "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (streamInfo.isNotBlank()) {
                        Text(
                            streamInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD6DAE2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                HomeClock(color = Color.White)
            }
        }
    }
}

/** The M3 seek bar: a straight bar that thickens on focus, a thumb
 * that pops in, and a time bubble that follows it. Left / Right
 * seek by the skip setting. */
@Composable
private fun M3SeekBar(
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
    val trackHeight by animateDpAsState(
        if (focused) 10.dp else 4.dp,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "track"
    )
    val thumbRadius by animateDpAsState(
        if (focused) 9.dp else 0.dp,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "thumb"
    )
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface

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
                color = onSurface.copy(alpha = 0.18f),
                topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = corner
            )
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = onSurface.copy(alpha = 0.28f),
                    topLeft = Offset(0f, top), size = Size(size.width * bufferedFraction, h), cornerRadius = corner
                )
            }
            val w = size.width * fraction
            if (w > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(primaryContainer, primary), endX = w.coerceAtLeast(1f)
                    ),
                    topLeft = Offset(0f, top), size = Size(w, h), cornerRadius = corner
                )
            }
            val r = thumbRadius.toPx()
            if (r > 0.5f) {
                val c = Offset(w, size.height / 2f)
                drawCircle(color = primary.copy(alpha = 0.35f), radius = r * 1.9f, center = c)
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
                Text(TimeFormatter.format(positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** An M3 tonal dock button: translucent fill, primary border when
 * focused, spring scale. */
@Composable
private fun M3DockButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else if (focused) 1.05f else 1f,
        spring(dampingRatio = 0.72f, stiffness = 420f), label = "scale"
    )
    Box(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The big filled play / pause button: a filled primary circle
 * with a white glyph that morphs between play and pause. */
@Composable
private fun M3DockPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else if (focused) 1.12f else 1f,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "scale"
    )
    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(
                if (focused) 2.dp else 0.dp,
                MaterialTheme.colorScheme.onPrimary, CircleShape
            )
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
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** A Prev / Next button. When there is no previous or next item,
 * it is replaced by an invisible twin of the SAME size, so the
 * play button stays dead centre. */
@Composable
private fun M3PrevNextButton(
    onClick: (() -> Unit)?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (onClick != null) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(
                icon, contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        Box(
            modifier = modifier.size(48.dp)
                .graphicsLayer { alpha = 0f },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = Color.Transparent,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** The M3 dock: time row, seek bar, then one row of buttons. Up
 * from any button enters the timeline; Down from the timeline
 * returns to Play. Slides up with the controls. One style only —
 * the M3 surface container capsule with the violet accent. */
@Composable
internal fun M3PlayerDock(
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
    onPlayPrev: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val skipMs = skipSec * 1_000L
    val upToSeek = Modifier.focusProperties { up = seekFocus }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.soft()) + slideInVertically(Motion.soft()) { it / 2 },
        exit = fadeOut(Motion.soft()) + slideOutVertically(Motion.soft()) { it / 2 },
        modifier = modifier
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = (maxWidth * 0.90f).coerceAtMost(1160.dp)
            Column(
                Modifier.width(w).align(Alignment.Center)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(28.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        TimeFormatter.format(positionMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        remainingLabel(durationMs, positionMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                M3SeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedFraction = bufferedFraction,
                    skipMs = skipMs,
                    onSeekBy = onSeekBy,
                    seekFocus = seekFocus,
                    playFocus = playFocus,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left and right groups take the SAME width, so
                    // the transport in the middle is exactly centred.
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        M3DockButton(subLabel, onClick = onSubtitles, modifier = upToSeek)
                        M3DockButton(audioLabel, onClick = onAudio, modifier = upToSeek)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        M3PrevNextButton(onPlayPrev, Icons.Filled.SkipPrevious, "Previous", modifier = upToSeek)
                        M3DockButton("-${skipSec}s", onClick = { onSeekBy(-skipMs) }, modifier = upToSeek)
                        M3DockPlayButton(
                            isPlaying = isPlaying,
                            onClick = onPlayPause,
                            modifier = upToSeek.focusRequester(playFocus)
                        )
                        M3DockButton("+${skipSec}s", onClick = { onSeekBy(skipMs) }, modifier = upToSeek)
                        M3PrevNextButton(onPlayNext, Icons.Filled.SkipNext, "Next", modifier = upToSeek)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                        M3DockButton("${speed}x", onClick = onCycleSpeed, modifier = upToSeek)
                        M3DockButton(aspectLabel, onClick = onAspect, modifier = upToSeek)
                        IconButton(onClick = onGear, modifier = upToSeek) {
                            Icon(
                                Icons.Filled.Settings, contentDescription = "Player settings",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
