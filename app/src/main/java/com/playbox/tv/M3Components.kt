package com.playbox.tv

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------- ZEPH shared components (M3 expressive) ----------
// Every new screen is built from these. One focus treatment, one
// button language, one card language — so the whole app feels like
// one design, not a pile of screens. Motion is transform + opacity
// only (scale on focus, fade on press), 60fps, no live blur.

/** The AnimatedVisibilityScope of the tab AnimatedContent in
 * MainActivity — the scope grid/row covers share with the Details
 * hero for the container transform (S14). Provided by MainActivity;
 * null outside the tab content. */
internal val LocalTabAnimatedVisibilityScope =
    compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The SharedTransitionScope (S14): Compose exposes it only as
 * the receiver of SharedTransitionLayout's content lambda — there
 * is no public LocalSharedTransitionScope — so MainActivity
 * provides the app's own local from that lambda, and the shared-
 * element helpers below read it from here. */
internal val LocalZephSharedTransitionScope =
    compositionLocalOf<SharedTransitionScope?> { null }

/** A shared-element modifier for a cover (S14): the cover morphs
 * into the Details hero. A plain Modifier when the shared
 * transition is not available (outside the tab content). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun coverSharedModifier(key: String): Modifier {
    val scope = LocalZephSharedTransitionScope.current ?: return Modifier
    val animScope = LocalTabAnimatedVisibilityScope.current ?: return Modifier
    val state = scope.rememberSharedContentState(key = key)
    return with(scope) { Modifier.sharedElement(state, animScope) }
}

/** The same, with an explicit AnimatedVisibilityScope — for the
 * Details hero, which lives in the Details overlay, not the tab
 * content. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun sharedModifier(key: String, animScope: AnimatedVisibilityScope?): Modifier {
    val scope = LocalZephSharedTransitionScope.current ?: return Modifier
    if (animScope == null) return Modifier
    val state = scope.rememberSharedContentState(key = key)
    return with(scope) { Modifier.sharedElement(state, animScope) }
}

/** The Home SearchBar (H1): 56dp pill, surface-high, search icon
 * + "Search videos, music…" + mic in a 38dp secondaryContainer
 * circle. Tapping it opens Search — the container transform grows
 * it into the Search field (S14). */
@Composable
internal fun ZephSearchBar(
    onClick: () -> Unit,
    onVoice: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth().height(56.dp)
            .then(coverSharedModifier("search-bar"))
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(start = 18.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Search, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(23.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Search videos, music…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (onVoice != null) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onVoice() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Mic, contentDescription = "Voice search",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** A settings row with an M3 Switch (Se3/Se6/Se8): title +
 * subtitle on the left, the Switch on the right. */
@Composable
internal fun ZephSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        )
    }
}

/** A settings slider row (Se7 Brightness): title + Dim/Bright
 * labels + a Slider. `value` is 0..1. */
@Composable
internal fun ZephSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    startLabel: String = "Dim",
    endLabel: String = "Bright",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                startLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text(
                endLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The Zeph focus treatment — the design's [data-f] (index.css):
 * a 3dp secondary outline drawn OUTSIDE the element (3dp offset),
 * following the element's own radius, plus a lift on focus and a
 * press. The lift depends on the element, like the design's classes:
 *   - focusScale 1.06f — `.lift`: cards, video cards, album cards.
 *   - focusScale 1.03f — `.lift-sm`: list rows, quick-access cards.
 *   - focusScale 1.0f — no lift: the rail items (outline + state
 *     layer only).
 * The scale and press run on the design's 300ms emphasized tween
 * (not a spring). Press is 0.97, or 0.98 when focused + lifting.
 * `cornerRadius` is the element's own radius — the outline is
 * drawn 3dp outside it, so pass the radius the element is drawn
 * with. Use on any clickable surface. */
@Composable
internal fun Modifier.zephFocus(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    focusScale: Float = DesignFocusScale,
    cornerRadius: Dp = 16.dp
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focusAnim = tween<Float>(durationMillis = 300, easing = Motion.Emphasized)
    val scale by animateFloatAsState(
        if (focused && enabled) focusScale else 1f,
        focusAnim, label = "zephFocusScale"
    )
    val pressTarget = when {
        !pressed || !enabled -> 1f
        focused && focusScale > 1f -> 0.98f
        else -> 0.97f
    }
    val pressScale by animateFloatAsState(pressTarget, focusAnim, label = "zephFocusPress")
    // 2.0.2: the ring is on-surface (near-white in dark, near-black
    // in light) — the old secondary ring was invisible on the TV
    // because the muted violet blended into the dark background.
    val focusRing = MaterialTheme.colorScheme.onSurface
    return this
        .graphicsLayer { scaleX = scale * pressScale; scaleY = scale * pressScale }
        .then(
            // The lift shadow only exists when the element lifts
            // (focusScale > 1) — a no-lift element (the rail) has an
            // outline and a state layer, but no shadow.
            if (focused && enabled && focusScale > 1f) {
                Modifier.shadow(
                    18.dp, RoundedCornerShape(cornerRadius),
                    spotColor = Color.Black.copy(alpha = 0.6f),
                    ambientColor = Color.Black.copy(alpha = 0.6f)
                )
            } else Modifier
        )
        .drawWithContent {
            if (focused && enabled) {
                // 2.0.2: state-layer fill so the focused item is
                // visibly lighter even before the ring registers.
                // 2.0.5: stronger (0.16) — the user found the
                // highlight hard to follow, so the focused item
                // must stand out unmistakably.
                drawRoundRect(
                    color = focusRing.copy(alpha = 0.16f),
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius.toPx())
                )
            }
            drawContent()
            if (focused && enabled) {
                // 2.0.2: thick bright ring — 5dp, clearly separated
                // from the item, so it reads from the couch.
                // 2.0.5: 6dp — the highlight must be unmissable.
                val stroke = 6.dp.toPx()
                val offset = 4.dp.toPx()
                drawRoundRect(
                    color = focusRing,
                    topLeft = Offset(-offset, -offset),
                    size = Size(size.width + offset * 2f, size.height + offset * 2f),
                    cornerRadius = CornerRadius((cornerRadius + 4.dp).toPx()),
                    style = Stroke(width = stroke)
                )
            }
        }
}

/** Zeph primary button — the design's filled button (m3.tsx):
 * 40dp tall, rounded-full, 24dp horizontal padding, 18dp icon,
 * label-l text. No shape morph — only the Fab morphs. */
@Composable
internal fun ZephButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.zephFocus(interaction, enabled),
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp, vertical = 10.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph tonal button — the design's tonal button: 40dp, pill,
 * 24dp horizontal padding, 18dp icon. */
@Composable
internal fun ZephTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.zephFocus(interaction, enabled),
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp, vertical = 10.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph outlined button — the design's outlined button: 40dp,
 * pill, 24dp horizontal padding, 18dp icon. */
@Composable
internal fun ZephOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.zephFocus(interaction, enabled),
        interactionSource = interaction,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp, vertical = 10.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph text button — the design's text button (12dp horizontal
 * padding). It is focusable like every other button: the focus
 * outline must be visible on it. */
@Composable
internal fun ZephTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.zephFocus(interaction, enabled, cornerRadius = 20.dp),
        interactionSource = interaction,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 10.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph card — the design's card: a surface-container fill with
 * a 16dp radius (rounded-lg) and NO border — the design's cards
 * are borderless. Clickable when onClick is given; focused state
 * gets the ring. */
@Composable
internal fun ZephCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .zephFocus(interaction, cornerRadius = 16.dp)
                    .clickable(interactionSource = interaction, indication = null) { onClick() }
            } else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

/** Zeph filter chip — the design's Chip (m3.tsx): 32dp tall,
 * 8dp radius (rounded-sm), label-l text, 18dp check/icon.
 * Selected = secondary-container + check; unselected =
 * transparent background, on-surface-variant label, 1dp
 * outline-variant border. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZephFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
        modifier = modifier.zephFocus(interaction, cornerRadius = 8.dp),
        interactionSource = interaction,
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp,
            selectedBorderWidth = 0.dp
        )
    )
}

/** Zeph section title — the design's SectionTitle (m3.tsx):
 * title-l (22sp Regular), 12dp below. 2.0.2 (gridlines fix 1):
 * NO horizontal padding — the title sits on the content edge
 * (0dp), matching the screen headers. (Before: 4dp, so titles
 * sat 4dp in from the headers.)
 * An optional muted subtitle follows it. */
@Composable
internal fun ZephSectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The engine door — Auto / Media3 / VLC as a real M3
 * SegmentedButton. Auto is FIRST: Media3 leads, VLC steps in on
 * its own for network shares and formats Media3 can't play.
 * Shared by Details and the player dock, so the door is the same
 * control in both places. `choice` null = Auto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3EngineDoor(
    choice: PlayerEngine?,
    onChoice: (PlayerEngine?) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        val options = listOf(
            "Auto" to null,
            "Media3" to PlayerEngine.MEDIA3,
            "VLC" to PlayerEngine.VLC
        )
        options.forEachIndexed { index, (label, engine) ->
            SegmentedButton(
                selected = choice == engine,
                onClick = { onChoice(engine) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    when {
                        engine == PlayerEngine.MEDIA3 || engine == PlayerEngine.VLC -> {
                            Icon(
                                Icons.Filled.PlayCircle, contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        choice == engine -> {
                            Icon(
                                Icons.Filled.Check, contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The shared TV top bar (S12): 52dp primaryContainer logo tile +
 * screen name 26sp semibold + clock pill. Every TV screen draws
 * this same bar — Home ("Zeph Player"), Library, Search, Sources,
 * Settings, Details. */
@Composable
internal fun ZephScreenTopBar(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            title,
            fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, fontFamily = AppFont,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        ClockPill()
    }
}

/** Clock in a pill (S11): 19sp semibold, surface-high background,
 * full radius. `dark` = the player HUD variant (black .45 pill,
 * white text). */
@Composable
internal fun ClockPill(
    dark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (dark) Color.Black.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        HomeClock(
            color = if (dark) Color.White else MaterialTheme.colorScheme.primary
        )
    }
}

/** Zeph top bar — centred title, expressive, transparent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZephTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        modifier = modifier,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** Zeph search field — rounded, surface container, with a leading
 * search icon and an optional trailing action (voice mic). */
@Composable
internal fun ZephSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = if (leadingIcon != null) {
            { Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        trailingIcon = trailingIcon,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Zeph dialog — M3 AlertDialog with the Zeph shape and colours. */
@Composable
internal fun ZephDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = { ZephButton(confirmText, onClick = onConfirm) },
        dismissButton = { ZephTextButton(dismissText, onClick = onDismiss) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** The design's LinearProgress (m3.tsx): 4dp tall, primary fill
 * on a secondary-container track. The hero passes its own track
 * (on-surface at 0.2, because it sits over a poster). */
@Composable
internal fun ZephLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(4.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = trackColor,
        drawStopIndicator = {}
    )
}

/** Zeph loading spinner — violet, for empty/loading states. */
@Composable
internal fun ZephLoading(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

/** Zeph empty state — a centred message for "nothing here yet". */
@Composable
internal fun ZephEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            action()
        }
    }
}

/** An M3 button with the old TvButton signature (text, modifier,
 * onClick last) — so screens that used TvButton / GlassButton can
 * restyle by renaming the call. onClick is LAST here, so
 * trailing-lambda syntax works (unlike the Zeph buttons, where
 * onClick comes before modifier/icon). A pill, like the design's
 * buttons — no shape morph. */
@Composable
internal fun M3TvButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .zephFocus(interaction, cornerRadius = 20.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                CircleShape
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}


/** A live clock (HH:mm), 19sp semibold (S11). The state lives
 * inside this composable so only it recomposes, once a second.
 * Used by the top bar's clock pill and the player HUD. */
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
        fontSize = 19.sp, color = color, fontWeight = FontWeight.SemiBold,
        fontFamily = AppFont, modifier = modifier
    )
}

/** The design's ScreenHeader (media.tsx): display-s title +
 * body-m subtitle on the left, actions on the right, 16dp below
 * (mb-4). Shared by Video, Audio, Browse, Playlists and Search. */
@Composable
internal fun M3ScreenHeader(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { actions() }
    }
}

/** The design's AlbumArt (media.tsx): procedural art from a hue —
 * a 135° linear gradient, two radial gradients (top-left and
 * bottom-right), a ring circle at the top-right and a blob at the
 * bottom-left, with a white icon (with a soft drop shadow) on top.
 * Shared by Home's album cards, the Audio screen and (Patch 5)
 * the mini-player / Now Playing. */
@Composable
internal fun M3AlbumArt(
    hue: Int,
    icon: ImageVector = Icons.Filled.MusicNote,
    iconSize: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Linear base, 135°: hsl(hue 60% 38%) → hsl(hue+30 65% 22%).
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.hsl(hue.toFloat(), 0.6f, 0.38f),
                        Color.hsl(((hue + 30) % 360).toFloat(), 0.65f, 0.22f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )
            // Radial, top-left (25%, 20%): hsl(hue 85% 68%) → clear.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.hsl(hue.toFloat(), 0.85f, 0.68f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.25f, h * 0.20f),
                    radius = size.minDimension * 0.55f
                )
            )
            // Radial, bottom-right (85%, 90%): hsl(hue+50 80% 50%) → clear.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.hsl(((hue + 50) % 360).toFloat(), 0.8f, 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.90f),
                    radius = size.minDimension * 0.60f
                )
            )
            // Ring: a 75%-sized circle at the top-right, 10dp stroke,
            // 20% opacity, hsl(hue 100% 90%).
            drawCircle(
                color = Color.hsl(hue.toFloat(), 1f, 0.9f).copy(alpha = 0.2f),
                radius = w * 0.375f,
                center = Offset(w * 0.875f, h * 0.125f),
                style = Stroke(width = 10.dp.toPx())
            )
            // Blob: a 75%-sized circle at the bottom-left, 20%
            // opacity, hsl(hue+80 90% 70%).
            drawCircle(
                color = Color.hsl(((hue + 80) % 360).toFloat(), 0.9f, 0.7f).copy(alpha = 0.2f),
                radius = w * 0.375f,
                center = Offset(w * 0.125f, h * 0.955f)
            )
        }
        // The icon, with a soft offset shadow behind it (the
        // design's drop-shadow-lg).
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, contentDescription = null,
                tint = Color.Black.copy(alpha = 0.25f),
                modifier = Modifier.size(iconSize).offset(2.dp, 2.dp)
            )
            Icon(
                icon, contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** The design's EqBars (media.tsx): 3dp bars, 2dp gaps, 16dp
 * tall, scaleY animation (700 + i*173 % 500 ms, i*90 ms stagger,
 * ease-in-out, 0.18→1). Paused = the bars freeze at scaleY 0.12
 * (index.css `.paused .eq-bar`). */
@Composable
internal fun ZephEqBars(
    playing: Boolean,
    count: Int = 4,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val transition = rememberInfiniteTransition(label = "eq")
        for (i in 0 until count) {
            val scale by transition.animateFloat(
                initialValue = 0.18f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 700 + (i * 173) % 500,
                        delayMillis = i * 90,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "eq-$i"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = if (playing) scale else 0.12f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(color, CircleShape)
            )
        }
    }
}
