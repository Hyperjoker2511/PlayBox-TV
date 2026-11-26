package com.playbox.tv

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

/** The Zeph focus treatment for TV: scale 1.04 + a 3dp primary
 * ring + a soft glow when focused, and a press to 0.96 that springs
 * back (S6). The scale and press run on M3's own spatial spring
 * (the theme's MotionScheme.expressive()), so focus travel IS
 * Material 3 motion. Use on any clickable surface. */
@Composable
internal fun Modifier.zephFocus(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (focused && enabled) DesignFocusScale else 1f,
        Motion.spatial(), label = "zephFocusScale"
    )
    val pressScale by animateFloatAsState(
        if (pressed && enabled) 0.96f else 1f,
        Motion.spatial(), label = "zephFocusPress"
    )
    return this
        .graphicsLayer { scaleX = scale * pressScale; scaleY = scale * pressScale }
        .then(
            if (focused && enabled) {
                Modifier
                    .shadow(
                        12.dp, MaterialTheme.shapes.large,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    )
                    .border(
                        3.dp, MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.large
                    )
            } else Modifier
        )
}

/** Shape-morphing button radius (S7): a button is a pill at rest
 * and morphs squarer on focus (16dp) and press (12dp), on M3's
 * spatial spring — the M3 Expressive signature for a media app. */
@Composable
internal fun morphRadius(interactionSource: MutableInteractionSource): Dp {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val target = when {
        pressed -> 12.dp
        focused -> 16.dp
        else -> 28.dp
    }
    return animateDpAsState(target, Motion.spatial(), label = "morphRadius").value
}

/** Zeph primary button — expressive filled button, violet. */
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
        shape = RoundedCornerShape(morphRadius(interaction)),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp, vertical = 14.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph tonal button — surface container fill, for secondary actions. */
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
        shape = RoundedCornerShape(morphRadius(interaction)),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp, vertical = 14.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph outlined button — for quiet actions. */
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
        shape = RoundedCornerShape(morphRadius(interaction)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp, vertical = 14.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph text button — for the quietest actions. */
@Composable
internal fun ZephTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zeph card — expressive rounded card, surface container fill,
 * 24dp radius + a 1px outline-variant border (S9). Clickable when
 * onClick is given; focused state gets the ring. */
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
                    .zephFocus(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onClick() }
            } else Modifier
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

/** Zeph filter chip — for Library modes, search filters, sort. */
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
        modifier = modifier.zephFocus(interaction),
        interactionSource = interaction,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp,
            selectedBorderWidth = 0.dp
        )
    )
}

/** Zeph section title — a headline + an optional muted subtitle. */
@Composable
internal fun ZephSectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
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

/** Zeph progress — linear, violet on surface container. */
@Composable
internal fun ZephLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
 * onClick comes before modifier/icon). */
@Composable
internal fun M3TvButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val radius = morphRadius(interaction)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .zephFocus(interaction)
            .clip(RoundedCornerShape(radius))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(radius)
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(radius)
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

/** The phone bottom navigation bar (Ph1–Ph5): 4 items — Home /
 * Search / Library / Settings (Sources is not in the phone nav;
 * it is reached from Home's Folders/USB tile). 10sp labels; the
 * active item is onSurface, inactive is onSurfaceVariant — no
 * underline bar, no Player button, no brand row. `onPlayer` is
 * kept for API stability. */
@Composable
internal fun M3TabStrip(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        listOf(Tab.Home, Tab.Search, Tab.Library, Tab.Settings).forEach { t ->
            val selected = tab == t
            Column(
                modifier = Modifier.weight(1f).clickable { onTab(t) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    designIconFor(t),
                    contentDescription = designLabelFor(t),
                    tint = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    designLabelFor(t),
                    fontSize = 10.sp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = AppFont
                )
            }
        }
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
