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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- ZEPH VideoPlayer (2.0.0 rebuild, Patch 7) ----------
// The design's full-screen VideoPlayer, replacing the old bottom
// dock player. The layout is the design's, verbatim:
//   - top bar: back + title + meta + res / codec / HW badges
//     (no clock pill, no gear — the gear is a pill in the bottom
//     row now),
//   - centre transport: prev, replay_10, the 96dp Fab, forward_10,
//     next — the Fab morphs round (paused) ↔ rounded square
//     (playing), M3 Expressive shape-morphing,
//   - bottom: time row (elapsed, speed / A–B badges, "Ends at
//     HH:MM", remaining), the M3 slider (buffered, value bubble on
//     focus), the pill row (Audio, Subtitles, Speed, Aspect, A–B,
//     Engine, spacer, Info, Pop-up, gear),
//   - side sheets (Audio / Subs / Speed / Aspect / Engine / Info)
//     slide in from the right, 420ms emphasized-decelerate,
//   - seek-flash circles (700ms seek-pop) and the paused badge.
// Controls auto-hide after 4.2s (the design's number — the old
// dock used 5s). Everything is transform + opacity only.
// The two player screens (Media3 + VLC) share this chrome; the
// engine-specific sheets (the gear — full VLC settings) stay in
// PlayerGear.kt / VlcGear.kt. Up Next left the player — it lives
// in Details and in the ended card.

/** One row in a player side sheet (radio list). */
internal data class M3PlayerSheetOption(
    val id: String,
    val label: String,
    val sub: String = "",
    val selected: Boolean = false
)

/** "Ends at 9:41 PM" for the bottom time row — the remaining time
 * at the current speed, added to now. (unit-tested via
 * endsAtLabel in Patch3Test? No — pure helper, tested here.) */
internal fun endsAtLabel(durationMs: Long, positionMs: Long, speed: Float): String {
    val remainingSec = if (durationMs > 0 && speed > 0) {
        ((durationMs - positionMs).coerceAtLeast(0) / 1000f / speed).toLong()
    } else 0L
    if (remainingSec <= 0L) return ""
    val at = Date(System.currentTimeMillis() + remainingSec * 1000L)
    return "Ends at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(at)
}

/** The design's pill button (VideoPlayer.tsx PillButton): h-10,
 * white/15 fill, label-l, 18dp icon, rounded-full, label truncated
 * to ~120dp. Active = primary fill. */
@Composable
internal fun M3PlayerPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else if (focused) 1.06f else 1f,
        Motion.spatial(), label = "pillScale"
    )
    Box(
        modifier = modifier
            .height(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                when {
                    active -> MaterialTheme.colorScheme.primary
                    focused -> Color.White.copy(alpha = 0.28f)
                    else -> Color.White.copy(alpha = 0.15f)
                }
            )
            .border(
                if (focused) 3.dp else 0.dp,
                if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.secondary, CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
    }
}

/** The design's top bar: a tonal 48dp back button, title + meta,
 * then the res / codec / HW badges. The status (a transient note
 * — "Resumed at…", a track change, an error) shows as a small pill
 * under the row while it lasts. */
@Composable
internal fun M3PlayerHud(
    title: String,
    meta: String,
    resBadge: String,
    codecBadge: String,
    showHwBadge: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    val interaction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .zephFocus(interaction, cornerRadius = 24.dp),
                        interactionSource = interaction
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack, contentDescription = "Back",
                            tint = Color.White, modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (resBadge.isNotBlank()) {
                    M3PlayerBadge(resBadge)
                }
                if (codecBadge.isNotBlank()) {
                    M3PlayerBadge(codecBadge)
                }
                if (showHwBadge) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "HW",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
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

/** A small white/15 badge for the top bar (res / codec). */
@Composable
private fun M3PlayerBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** The design's centre transport: prev (56dp), replay_10 (64dp),
 * the 96dp Fab (round when paused, rounded-square when playing —
 * M3 Expressive shape-morphing), forward_10 (64dp), next (56dp).
 * Focus lands on the Fab when the controls show. */
@Composable
internal fun M3VideoPlayerTransport(
    isPlaying: Boolean,
    ended: Boolean,
    onPrev: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onNext: () -> Unit,
    playFocus: FocusRequester,
    // M10 (2.0.2): Down from the play Fab goes to the dock's seek
    // slider — the main Up/Down path in the HUD.
    seekFocus: FocusRequester,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.soft()) + scaleIn(Motion.soft(), initialScale = 0.9f),
        exit = fadeOut(Motion.soft()) + scaleOut(Motion.soft(), targetScale = 0.9f),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3TransportCircle(
                Icons.Filled.SkipPrevious, "Previous video", 56.dp, 28,
                onClick = onPrev
            )
            M3TransportCircle(
                Icons.Filled.Replay10, "Back 10 seconds", 64.dp, 34,
                onClick = onSeekBack
            )
            M3PlayerFab(
                isPlaying = isPlaying,
                ended = ended,
                onClick = onPlayPause,
                // M10 (2.0.2): Down from play goes to the seek slider.
                modifier = Modifier.focusRequester(playFocus)
                    .focusProperties { down = seekFocus }
            )
            M3TransportCircle(
                Icons.Filled.Forward10, "Forward 10 seconds", 64.dp, 34,
                onClick = onSeekForward
            )
            M3TransportCircle(
                Icons.Filled.SkipNext, "Next video", 56.dp, 28,
                onClick = onNext
            )
        }
    }
}

/** A tonal transport circle (skip / prev / next): secondary
 * container fill, with an icon. */
@Composable
private fun M3TransportCircle(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: Int,
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
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            )
            // 2.0.10: the transport circles had no zephFocus ring —
            // only a background-colour change — so on the TV the user
            // could not tell which control was focused ("don't know
            // what I'm tapping"). The bright ring makes the focused
            // control unmistakable. focusScale 1f: the circle already
            // scales itself, so the ring adds no extra scale.
            .zephFocus(interaction, focusScale = 1f, cornerRadius = (size / 2))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = contentDescription,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/** The design's 96dp Fab: primary-container fill, the glyph
 * morphs play ↔ pause, and the shape morphs round (paused) ↔
 * rounded square (playing) — M3 Expressive shape-morphing. */
@Composable
private fun M3PlayerFab(
    isPlaying: Boolean,
    ended: Boolean,
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
    // Round when paused (or ended — restart), rounded square
    // when playing: the design's morph.
    val corner by animateDpAsState(
        if (isPlaying && !ended) 28.dp else 48.dp,
        Motion.bouncy(), label = "fabCorner"
    )
    val shape = RoundedCornerShape(corner)
    val morphSpec = Motion.spatial<Float>()
    Box(
        modifier = modifier
            .size(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                if (focused) 3.dp else 0.dp,
                MaterialTheme.colorScheme.onPrimaryContainer, shape
            )
            // 2.0.10: same as the transport circles — the Fab had
            // only a 3dp border for focus, too subtle on the TV.
            // The bright zephFocus ring makes it unmistakable.
            // focusScale 1f: the Fab already scales itself.
            .zephFocus(interaction, focusScale = 1f, cornerRadius = corner)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = if (ended) "restart" else if (isPlaying) "pause" else "play",
            transitionSpec = {
                (scaleIn(morphSpec, initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                    (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(90)))
            },
            label = "fabGlyph"
        ) { state ->
            Icon(
                when (state) {
                    "restart" -> Icons.Filled.Replay10
                    "pause" -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = if (state == "pause") "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

/** The design's M3 slider: the Material3 Slider (gap + vertical
 * handle, buffered track behind it) with a value bubble while
 * focused. Left / Right seek by the skip setting; the bubble shows
 * the time at the thumb. */
@Composable
internal fun M3VideoSlider(
    positionMs: Long,
    durationMs: Long,
    bufferedFraction: Float,
    skipMs: Long,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    seekFocus: FocusRequester,
    // M10 (2.0.2): Up from the seek slider goes to the play/pause
    // Fab — the main Up/Down path in the HUD.
    playPauseFocus: FocusRequester,
    // 2.0.4 (gear fix): the gear lives in the time row above the
    // slider, and Up from the slider is the only way to reach it —
    // before, Up went straight to play/pause, so the gear was a
    // focus orphan no D-pad path could reach.
    gearFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var seeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(fraction) }
    val shownFraction = if (seeking) seekFraction else fraction
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier) {
        // The value bubble, only while the slider is focused.
        AnimatedVisibility(
            visible = focused,
            enter = fadeIn(Motion.snap()) + scaleIn(Motion.snap(), initialScale = 0.8f),
            exit = fadeOut(Motion.snap())
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        TimeFormatter.format((shownFraction * durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        // M9 (2.0.1): ONE focusable for the seek control. The outer
        // Box owns focus + Left/Right (seek by the skip amount).
        // onPreviewKeyEvent intercepts Left/Right BEFORE the inner
        // Slider's own small-step handling, so Left/Right always
        // seeks by skipMs whichever node holds focus. The Slider
        // itself is focusable(false) so focus can't land on it
        // separately and fight the Box over the same keys.
        // (Before: onKeyEvent on the Box never fired — seekFocus
        // was never requested, so the Slider took focus and
        // Left/Right only nudged by a tiny step.)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(seekFocus)
                // 2.0.4 (gear fix): Up from seek goes to the gear in
                // the time row (not straight to play/pause) — the
                // gear's own Up goes on to play/pause, so the chain
                // is seek → gear → play/pause, all reachable.
                .focusProperties { up = gearFocus }
                .focusable(interactionSource = interaction)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onSeekBy(-skipMs); true }
                        Key.DirectionRight -> { onSeekBy(skipMs); true }
                        else -> false
                    }
                }
        ) {
            // Buffered track, drawn behind the Slider's own track.
            if (bufferedFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(onSurface.copy(alpha = 0.28f))
                )
            }
            Slider(
                value = shownFraction,
                onValueChange = { seeking = true; seekFraction = it },
                onValueChangeFinished = {
                    seeking = false
                    onSeekTo((seekFraction * durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth().focusable(false),
                interactionSource = interaction,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = primary,
                    inactiveTrackColor = trackColor
                )
            )
        }
    }
}

/** The design's bottom bar — 2.0.2 declutter: essentials only.
 * The time row (elapsed, speed / A–B badges + "Ends at HH:MM",
 * remaining + the gear button), then the M3 slider. The gear opens
 * the full settings sheet; every option the old pill row held
 * (Audio, Subtitles, Speed, Aspect, A–B, Engine, Info, Pop-up)
 * lives there now. Slides up with the controls.
 *
 * The pill parameters (labels + callbacks) are retained in the
 * signature for now — the call sites still pass them and the
 * gear sheet reuses the labels — but the dock no longer renders
 * any pill. */
@Composable
internal fun M3PlayerDock(
    positionMs: Long,
    durationMs: Long,
    bufferedFraction: Float,
    speed: Float,
    skipSec: Int,
    audioLabel: String,
    subLabel: String,
    aspectLabel: String,
    abLabel: String,
    abBadge: String,
    abActive: Boolean,
    engineLabel: String,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onAspect: () -> Unit,
    onAB: () -> Unit,
    onEngine: () -> Unit,
    onInfo: () -> Unit,
    onPip: () -> Unit,
    onGear: () -> Unit,
    seekFocus: FocusRequester,
    // M10 (2.0.2): Up from the seek slider goes to play/pause.
    playPauseFocus: FocusRequester,
    // M11 (2.0.2): focus returns here when the gear sheet closes.
    gearFocus: FocusRequester,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val skipMs = skipSec * 1_000L
    val endsAt = endsAtLabel(durationMs, positionMs, speed)
    val speedText = if (speed == 1f) "1×" else {
        val s = "%.2f".format(Locale.US, speed).trimEnd('0').trimEnd('.')
        "${s}×"
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.soft()) + slideInVertically(Motion.soft()) { it / 2 },
        exit = fadeOut(Motion.soft()) + slideOutVertically(Motion.soft()) { it / 2 },
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Time row. 2.0.2 (gridlines fix 6): NO horizontal
            // padding — the times sit on the dock edge (24dp from the
            // screen, matching the top bar). (Before: 4dp, so times
            // sat 36dp in.)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    TimeFormatter.format(positionMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (speed != 1f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                speedText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    if (abBadge.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                abBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (endsAt.isNotBlank()) {
                        Text(
                            endsAt,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (durationMs > 0) "-${TimeFormatter.format(durationMs - positionMs)}" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    // 2.0.2: the gear is the ONLY settings entry on
                    // screen — the pill row is gone, so it lives here
                    // in the time row. It opens the full settings sheet.
                    val gearInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onGear,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            // M11 (2.0.2): focus returns here when the
                            // gear sheet closes.
                            .focusRequester(gearFocus)
                            // 2.0.4 (gear fix): the gear is the middle
                            // of the chain — Down back to the seek
                            // slider, Up on to play/pause.
                            .focusProperties { down = seekFocus; up = playPauseFocus }
                            .zephFocus(gearInteraction, cornerRadius = 20.dp),
                        interactionSource = gearInteraction
                    ) {
                        Icon(
                            Icons.Filled.Settings, contentDescription = "Player settings",
                            tint = Color.White, modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            M3VideoSlider(
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedFraction = bufferedFraction,
                skipMs = skipMs,
                onSeekTo = onSeekTo,
                onSeekBy = onSeekBy,
                seekFocus = seekFocus,
                playPauseFocus = playPauseFocus,
                // 2.0.4 (gear fix): Up from seek goes to the gear.
                gearFocus = gearFocus,
                modifier = Modifier.fillMaxWidth()
            )
            // 2.0.2: the pill row is gone — the dock shows essentials
            // only (time + seek + gear). Every option the pills opened
            // (Audio, Subtitles, Speed, Aspect, A–B, Engine, Info,
            // Pop-up) lives in the gear sheet now.
        }
    }
}

/** The design's side sheet: slides in from the right (420ms
 * emphasized-decelerate) over a scrim, 400dp wide, with a title
 * and a close button. The caller composes the rows.
 *
 * 2.0.1 fixes:
 * - C3: rows live in a scrollable Column, not a Box — a Box
 *   stacked every row on top of each other, so only the topmost
 *   row of Audio/Subtitles/Speed/Aspect/Engine/Info was visible.
 * - C4: the scrim is touch-only (pointerInput tap) — the old
 *   clickable scrim was a focusable full-screen target that
 *   swallowed D-pad focus and closed the sheet on OK.
 * - M6/M7: the sheet's focusRequester is the fallback focus
 *   target on the Close button. Sheets with rows attach it to
 *   their first row instead (composed later, so that wins);
 *   row-less sheets (Info, empty Audio) fall back to Close,
 *   so focus is never lost when a sheet opens.
 * - M24: the Close button has the zephFocus ring. */
@Composable
internal fun M3PlayerSheet(
    title: String,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val closeInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Touch-only scrim: tap outside closes. pointerInput adds
        // no focus, so the D-pad never lands on this full-screen
        // target (C4). Back / the Close button close it by remote.
        // 2.1.0 (motion batch, audit MOT-16): the scrim fades with
        // the sheet — it used to pop in/out instantly around the
        // sliding panel.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.scrimFade()),
            exit = fadeOut(Motion.scrimFade())
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(onClose) { detectTapGestures { onClose() } }
            )
        }
        // 2.1.0 (motion batch, audit MOT-16): `visible` drives this
        // — the sheet used to be composed with visible = true only
        // while its caller's `if (sheet != null)` held, so on close
        // it left composition instantly and this exit spec was dead
        // code. Callers now keep the sheet composed (with its last
        // kind) while it exits.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.scrimFade()) + slideInHorizontally(
                Motion.sheetSlide()
            ) { it },
            exit = fadeOut(Motion.scrimFade()) + slideOutHorizontally(
                Motion.sheetSlide()
            ) { it }
        ) {
            Column(
                modifier = Modifier.width(400.dp).fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                    )
                    .padding(24.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .zephFocus(closeInteraction, focusScale = 1f, cornerRadius = 20.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Rows stack vertically and scroll (C3) — a file
                // can have many audio/subtitle tracks.
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content
                )
            }
        }
    }
}

/** One radio row in a player side sheet. */
@Composable
internal fun M3PlayerSheetRadioRow(
    option: M3PlayerSheetOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (option.selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .zephFocus(interaction, cornerRadius = 12.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (option.sub.isNotBlank()) {
                Text(
                    option.sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        // M8 (2.0.1): the radio is visual only — the row itself
        // is the clickable/focusable target. onClick = null keeps
        // it out of focus traversal (focus used to land on this
        // small circle with no visible ring). Explicit colours so
        // a null onClick doesn't render it faded/disabled.
        RadioButton(
            selected = option.selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/** One info row in the Media information sheet. */
@Composable
internal fun M3PlayerSheetInfoRow(
    icon: ImageVector,
    overline: String,
    headline: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                overline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                headline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The design's seek-flash circle: a 112dp translucent circle
 * with the direction icon + "10 s", on the left for rewind, the
 * right for forward. The flash LIVES 700ms (the callers' timer);
 * 2.1.0 (motion batch, audit MOT-17): the grow now completes in
 * 250ms (Motion.ChromeMs), then holds, then the 150ms fade —
 * the old spec grew over the flash's whole 700ms life, so the
 * circle was dismissed exactly as it finished growing and the
 * "seek-pop" read as a slow swell. */
@Composable
internal fun M3SeekFlash(
    forward: Boolean,
    skipSec: Int,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(
            tween(Motion.ChromeMs, easing = Motion.EmphasizedDecelerate), initialScale = 0.6f
        ),
        exit = fadeOut(tween(150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(112.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "$skipSec s",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

/** The design's paused badge: an 80dp primary-container circle
 * with a pause glyph, shown when the video is paused AND the
 * controls are hidden. */
@Composable
internal fun M3PausedBadge(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380, easing = Motion.EmphasizedDecelerate)) +
            scaleIn(tween(380, easing = Motion.EmphasizedDecelerate), initialScale = 0.82f),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Pause, contentDescription = "Paused",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
