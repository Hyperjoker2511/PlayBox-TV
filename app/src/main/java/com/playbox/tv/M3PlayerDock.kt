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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- ZEPH Player dock + HUD (M3 Expressive, v5 / 1.9.0) ----------
// ONE bottom dock only. The dock is a single M3 container
// (surfaceContainerHigh, 32dp) with clear groups inside it:
//   - the WAVY scrubber (the M3 Expressive signature — it moves
//     while the video plays),
//   - the time row (current / total, engine),
//   - the transport (skip back, a 72dp hero Play/Pause, skip
//     forward, Prev/Next at the ends),
//   - the ENGINE row (Auto / Media3 / VLC — the same door as
//     Details, Auto first),
//   - the options chips (speed, audio, subtitles, Up Next, aspect,
//     gear),
//   - the UP NEXT carousel (a horizontal row of cards, not a grid),
//   - the remote hints.
// The HUD at the top carries the back button, title, stream info,
// badge and live clock (plan v2 P8). No Resume button here —
// Resume lives on Home and Details. All transform + opacity only.

/** The HUD: back + title + stream info on the left, badge + live
 * clock on the right. Shown with the controls, hidden with them. */
@Composable
internal fun M3PlayerHud(
    title: String,
    badge: String,
    streamInfo: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
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
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack, contentDescription = "Back",
                            tint = Color.White, modifier = Modifier.size(28.dp)
                        )
                    }
                }
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
                            color = Color.White.copy(alpha = 0.85f),
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

/** The M3 Expressive wavy seek bar: the track is a wave, not a
 * straight line, and the wave MOVES while the video plays (the
 * phase advances on a timer, only while playing). The wave grows
 * on focus, a thumb pops in, and a time bubble follows it.
 * Left / Right seek by the skip setting. */
@Composable
private fun M3WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedFraction: Float,
    skipMs: Long,
    isPlaying: Boolean,
    onSeekBy: (Long) -> Unit,
    seekFocus: FocusRequester,
    playFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    // The wave phase advances while playing (20fps, cheap) and
    // freezes when paused.
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(50)
            phase = (phase + 0.16f) % (2f * PI.toFloat())
        }
    }
    val amplitude by animateDpAsState(
        if (focused) 6.dp else 3.5.dp,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "waveAmp"
    )
    val strokeWidth by animateDpAsState(
        if (focused) 5.dp else 4.dp,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "waveStroke"
    )
    val thumbRadius by animateDpAsState(
        if (focused) 9.dp else 0.dp,
        spring(dampingRatio = 0.62f, stiffness = 520f), label = "thumb"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
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
            val ampPx = amplitude.toPx()
            val strokePx = strokeWidth.toPx()
            val wavelengthPx = 48.dp.toPx()
            val centerY = size.height / 2f
            fun wavePath(upToX: Float): Path {
                val path = Path()
                path.moveTo(0f, centerY + ampPx * sin(phase))
                var x = 4f
                while (x <= upToX) {
                    val y = centerY + ampPx * sin(2f * PI.toFloat() * x / wavelengthPx + phase)
                    path.lineTo(x, y)
                    x += 4f
                }
                return path
            }
            val style = Stroke(width = strokePx, cap = StrokeCap.Round)
            drawPath(
                path = wavePath(size.width),
                color = onSurface.copy(alpha = 0.18f),
                style = style
            )
            if (bufferedFraction > 0f) {
                drawPath(
                    path = wavePath(size.width * bufferedFraction),
                    color = onSurface.copy(alpha = 0.28f),
                    style = style
                )
            }
            val w = size.width * fraction
            if (w > 0f) {
                drawPath(
                    path = wavePath(w),
                    color = primary,
                    style = style
                )
            }
            val r = thumbRadius.toPx()
            if (r > 0.5f) {
                val thumbY = centerY + ampPx * sin(2f * PI.toFloat() * w / wavelengthPx + phase)
                val c = Offset(w, thumbY)
                drawCircle(color = primary.copy(alpha = 0.35f), radius = r * 1.9f, center = c)
                drawCircle(color = Color.White, radius = r, center = c)
            }
        }
        AnimatedVisibility(
            visible = focused,
            enter = fadeIn() + scaleIn(Motion.spatial(), initialScale = 0.6f),
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
        Motion.spatial(), label = "scale"
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

/** The big filled play / pause button — the hero of the dock:
 * a 72dp filled primary circle with a glyph that morphs between
 * play and pause. */
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
        Motion.spatial(), label = "scale"
    )
    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(
                if (focused) 3.dp else 0.dp,
                MaterialTheme.colorScheme.onPrimary, CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(Motion.spatial(), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                    (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(90)))
            },
            label = "playPause"
        ) { playing ->
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/** A tonal transport circle (skip back / forward): secondary
 * container fill, 56dp, with a text label ("-10s") that shows the
 * real skip value. */
@Composable
private fun M3TransportButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else if (focused) 1.08f else 1f,
        Motion.spatial(), label = "scale"
    )
    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (focused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
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

/** One Up Next card in the dock's carousel: 16:9 cover, title,
 * duration. */
@Composable
private fun M3UpNextCard(
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZephCard(modifier = modifier.width(200.dp), onClick = onClick) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            ) {
                if (item.isVideo) {
                    VideoThumb(item, Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (item.isVideo && item.durationMs > 0L) {
                    Text(
                        TimeFormatter.format(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Color(0xB3000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    listOf(
                        if (item.isVideo) TimeFormatter.format(item.durationMs) else "Audio",
                        formatSize(item.sizeBytes)
                    ).filter { it.isNotBlank() }.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The M3 dock (v5): one large container with clear groups — wavy
 * scrubber + time row, transport with a hero Play, the engine row,
 * the options chips, the Up Next carousel, the remote hints. Up
 * from any button enters the timeline; Down from the timeline
 * returns to Play. Slides up with the controls. */
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
    // v5: the engine door (same control as Details) + Up Next.
    activeEngine: PlayerEngine = PlayerEngine.MEDIA3,
    engineChoice: PlayerEngine? = null,
    onEngineChange: ((PlayerEngine?) -> Unit)? = null,
    upNext: List<VideoItem> = emptyList(),
    onPlayUpNext: (VideoItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val skipMs = skipSec * 1_000L
    val upToSeek = Modifier.focusProperties { up = seekFocus }
    var showUpNext by remember { mutableStateOf(true) }
    val engineText = (if (activeEngine == PlayerEngine.MEDIA3) "Media3" else "VLC") +
        if (engineChoice == null) " · Auto" else ""
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
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(32.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Wavy scrubber + time row.
                M3WavySeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedFraction = bufferedFraction,
                    skipMs = skipMs,
                    isPlaying = isPlaying,
                    onSeekBy = onSeekBy,
                    seekFocus = seekFocus,
                    playFocus = playFocus,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${TimeFormatter.format(positionMs)}  /  ${TimeFormatter.format(durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        engineText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Transport — centred on the hero Play.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3PrevNextButton(onPlayPrev, Icons.Filled.SkipPrevious, "Previous", modifier = upToSeek)
                    Spacer(modifier = Modifier.width(20.dp))
                    M3TransportButton("-${skipSec}s", onClick = { onSeekBy(-skipMs) }, modifier = upToSeek)
                    Spacer(modifier = Modifier.width(20.dp))
                    M3DockPlayButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPause,
                        modifier = upToSeek.focusRequester(playFocus)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    M3TransportButton("+${skipSec}s", onClick = { onSeekBy(skipMs) }, modifier = upToSeek)
                    Spacer(modifier = Modifier.width(20.dp))
                    M3PrevNextButton(onPlayNext, Icons.Filled.SkipNext, "Next", modifier = upToSeek)
                }
                // Engine row — the door, Auto first.
                if (onEngineChange != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Engine",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        M3EngineDoor(
                            choice = engineChoice,
                            onChoice = onEngineChange,
                            modifier = upToSeek
                        )
                    }
                }
                // Options chips.
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3DockButton("${speed}x", onClick = onCycleSpeed, modifier = upToSeek)
                    M3DockButton(audioLabel, onClick = onAudio, modifier = upToSeek)
                    M3DockButton(subLabel, onClick = onSubtitles, modifier = upToSeek)
                    if (upNext.isNotEmpty()) {
                        M3DockButton("Up Next", onClick = { showUpNext = !showUpNext }, modifier = upToSeek)
                    }
                    M3DockButton(aspectLabel, onClick = onAspect, modifier = upToSeek)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onGear, modifier = upToSeek) {
                        Icon(
                            Icons.Filled.Settings, contentDescription = "Player settings",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                // Up Next carousel.
                if (showUpNext && upNext.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Up next",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${upNext.size} queued",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(upNext, key = { it.path }) { next ->
                            M3UpNextCard(item = next, onClick = { onPlayUpNext(next) })
                        }
                    }
                }
                // Remote hints + status.
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "OK: play / pause  ·  Left / Right: seek ${skipSec}s  ·  Up / Down: volume  ·  Back: exit player",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
