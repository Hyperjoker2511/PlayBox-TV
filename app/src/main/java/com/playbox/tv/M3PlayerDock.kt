package com.playbox.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import java.util.Locale

// ---------- ZEPH Player dock + HUD (M3 Expressive, v5 / 1.9.0) ----------
// ONE bottom dock only. The dock is a single M3 container
// (surfaceContainerHigh, 32dp) with clear groups inside it:
//   - the WAVY scrubber (the M3 Expressive signature — it moves
//     while the video plays),
//   - the time row (current / total, engine),
//   - the transport (Replay10, a 74dp hero Play/Pause,
//     Forward10 — no Prev/Next, no text),
//   - the ENGINE row (Auto / Media3 / VLC — the same door as
//     Details, Auto first),
//   - the options chips (speed, audio, subtitles, Up Next,
//     volume — AssistChips with icons),
//   - the UP NEXT carousel (a horizontal row of cards, not a grid),
//   - the remote hints.
// The HUD at the top carries the back button, title, stream info,
// clock pill, gear and the status pill (plan v2 P8). No Resume
// button here — Resume lives on Home and Details. All transform
// + opacity only.

/** The HUD (P1–P4): back + title + stream info on the left, the
 * clock pill and the gear on the right. No "NOW PLAYING" label,
 * no badge pill. The title is 20sp semibold; the stream info
 * carries codecs ("1080p · H.264 · AAC stereo"). The status (a
 * transient note — "Resumed at…", a track change, an error) shows
 * as a small pill under the row while it lasts. */
@Composable
internal fun M3PlayerHud(
    title: String,
    streamInfo: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onGear: (() -> Unit)? = null,
    status: String = ""
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.soft()) + slideInVertically(Motion.soft()) { -it },
        exit = fadeOut(Motion.soft()) + slideOutVertically(Motion.soft()) { -it },
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
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
                        title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (streamInfo.isNotBlank()) {
                        Text(
                            streamInfo,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ClockPill(dark = true)
                if (onGear != null) {
                    IconButton(onClick = onGear) {
                        Icon(
                            Icons.Filled.Settings, contentDescription = "Player settings",
                            tint = Color.White, modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            // P14: the status lives here now, not in the dock — a
            // small pill under the HUD while the note lasts.
            if (status.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The M3 Expressive wavy seek bar: the track is a wave, not a
 * straight line, and the wave MOVES while the video plays (the
 * phase advances on a timer, only while playing). The wave grows
 * on focus. The stroke is 5dp always, the thumb is 8dp and ALWAYS
 * visible (P6), the track is surface-highest, and there is no time
 * bubble (P7). Left / Right seek by the skip setting. */
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
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
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
        Motion.spatial(), label = "waveAmp"
    )
    // P6: the stroke is 5dp always and the thumb is 8dp, always
    // visible — the design's scrubber never hides its thumb.
    val strokeWidth = 5.dp
    val thumbRadius = 8.dp

    Box(
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
                color = trackColor,
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
    }
}

/** An options chip for the dock row (P10): a real AssistChip
 * with an icon and a label — "Speed · 1.0×", "Audio · Stereo",
 * "Subtitles · English", "Up Next", "Volume · 80%". */
@Composable
private fun M3DockChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        modifier = modifier
            .height(48.dp)
            .zephFocus(interaction),
        shape = RoundedCornerShape(12.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            labelColor = Color.White,
            leadingIconContentColor = Color.White
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = Color.White.copy(alpha = 0.4f)
        ),
        interactionSource = interaction
    )
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
    // The morph spec is read here (in composition) because a
    // transitionSpec lambda is not a @Composable context — it can
    // only USE a spec, not read the theme's scheme.
    val morphSpec = Motion.spatial<Float>()
    Box(
        modifier = modifier
            .size(74.dp)
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
                (scaleIn(morphSpec, initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
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
 * container fill, 56dp, with an icon (Replay10 / Forward10) —
 * the design's transport has icons, not "-10s" text (P8). */
@Composable
private fun M3TransportButton(
    icon: ImageVector,
    contentDescription: String,
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
        Icon(
            icon, contentDescription = contentDescription,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** The centre play/pause indicator (P5): a 76dp translucent
 * circle with the glyph that flashes over the middle of the
 * video for ~700ms each time playback toggles. It does not show
 * on the first composition — only on a real toggle. */
@Composable
internal fun M3CentreIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    var shownPlaying by remember { mutableStateOf(isPlaying) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(isPlaying) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        shownPlaying = isPlaying
        visible = true
        delay(700)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.soft()) + scaleIn(Motion.soft(), initialScale = 0.8f),
        exit = fadeOut(Motion.soft()),
        modifier = modifier
    ) {
        Box(
            Modifier.size(76.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (shownPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
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
    ZephCard(modifier = modifier.width(220.dp), onClick = onClick) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(96.dp)
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

/** The M3 dock (v5 + 1.9.1 design pass): one large container
 * with clear groups — wavy scrubber + time row, transport with
 * a hero Play, the engine row, the options chips, the Up Next
 * carousel, the remote hints. Up from any button enters the
 * timeline; Down from the timeline returns to Play. Slides up
 * with the controls. The dock is full content width (P13); the
 * status line lives in the HUD now (P14); Prev / Next left the
 * dock (P8) — they stay on the queue and the media keys. */
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
    volumePct: Int,
    onSeekBy: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onVolume: () -> Unit,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    visible: Boolean,
    // v5: the engine door (same control as Details) + Up Next.
    activeEngine: PlayerEngine = PlayerEngine.MEDIA3,
    engineChoice: PlayerEngine? = null,
    onEngineChange: ((PlayerEngine?) -> Unit)? = null,
    upNext: List<VideoItem> = emptyList(),
    onPlayUpNext: (VideoItem) -> Unit = {},
    onSeeAllUpNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val skipMs = skipSec * 1_000L
    val upToSeek = Modifier.focusProperties { up = seekFocus }
    val firstUpNextFocus = remember { FocusRequester() }
    val engineText = (if (activeEngine == PlayerEngine.MEDIA3) "Media3" else "VLC") +
        if (engineChoice == null) " · Auto" else ""
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.soft()) + slideInVertically(Motion.soft()) { it / 2 },
        exit = fadeOut(Motion.soft()) + slideOutVertically(Motion.soft()) { it / 2 },
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxWidth()
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
                // Transport — centred on the hero Play (P8):
                // Replay10 / Play 74dp / Forward10, 20dp gaps.
                // Prev / Next left the dock — queue + media keys.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3TransportButton(
                        Icons.Filled.Replay10, "Back ${skipSec} seconds",
                        onClick = { onSeekBy(-skipMs) }, modifier = upToSeek
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    M3DockPlayButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPause,
                        modifier = upToSeek.focusRequester(playFocus)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    M3TransportButton(
                        Icons.Filled.Forward10, "Forward ${skipSec} seconds",
                        onClick = { onSeekBy(skipMs) }, modifier = upToSeek
                    )
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
                // Options chips (P10): AssistChips with icons and
                // labels. The aspect chip is gone — aspect lives in
                // the gear sheet; the gear button moved to the HUD.
                // The "Up Next" chip moves focus to the carousel.
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3DockChip(
                        "Speed · ${"%.1f".format(Locale.US, speed)}×",
                        Icons.Filled.Speed, onClick = onCycleSpeed, modifier = upToSeek
                    )
                    M3DockChip(audioLabel, Icons.Filled.MusicNote, onClick = onAudio, modifier = upToSeek)
                    M3DockChip(subLabel, Icons.Filled.ClosedCaption, onClick = onSubtitles, modifier = upToSeek)
                    if (upNext.isNotEmpty()) {
                        M3DockChip(
                            "Up Next", Icons.Filled.QueueMusic,
                            onClick = { firstUpNextFocus.requestFocus() },
                            modifier = upToSeek
                        )
                    }
                    M3DockChip(
                        if (volumePct == 0) "Volume · Off" else "Volume · $volumePct%",
                        if (volumePct == 0) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        onClick = onVolume, modifier = upToSeek
                    )
                }
                // Up Next carousel (P11): always shown while the
                // queue is non-empty — no toggle. Cards are 220dp
                // wide with a 96dp cover.
                if (upNext.isNotEmpty()) {
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
                        ZephTextButton("See all", onClick = onSeeAllUpNext)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(upNext, key = { _, it -> it.path }) { index, next ->
                            M3UpNextCard(
                                item = next,
                                onClick = { onPlayUpNext(next) },
                                modifier = if (index == 0) Modifier.focusRequester(firstUpNextFocus) else Modifier
                            )
                        }
                    }
                }
                // Remote hints (P12): centred, with glyphs.
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "OK — play / pause  ·  ◀ ▶ — seek ${skipSec} s  ·  ▲ ▼ — volume  ·  Back — exit player",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }