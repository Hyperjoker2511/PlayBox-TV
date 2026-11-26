package com.playbox.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ---------- ZEPH Search (M3 redesign, from scratch) ----------
// A NEW Search, not a Nova/Qwen/Aura port — but it keeps the
// animation the user picked from the Short (2026-11-22): #1 Icon
// Expand + #5 Focus Morph, one continuous flow. The pill stretches
// into a full-width field, then on focus the title slides away, the
// field rises, Cancel slides in and the Recent chips drop below.
// All transform + opacity only, so it stays cheap on the 2 GB box.
// Voice search (mic, only when the box has a recognizer) and recent
// searches carry over unchanged.
// v5 (1.9.0): an ON-SCREEN QWERTY KEYBOARD — on a remote, text
// comes from the keyboard or voice. On a wide screen (TV) the
// keyboard sits on the left and the results on the right; on a
// narrow screen it drops in under the field when the field is
// focused. The keyboard writes into the same query the field edits.

/** The placeholder hint types itself in, holds, deletes, and moves
 * to the next hint — the #1 typing effect, as a placeholder only. */
@Composable
private fun M3TypewriterHint() {
    val hints = listOf(
        "Search videos, music, folders…",
        "Try a movie name…",
        "Try a song…"
    )
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        var h = 0
        while (true) {
            val hint = hints[h % hints.size]
            for (i in 1..hint.length) {
                text = hint.take(i)
                delay(45)
            }
            delay(1500)
            for (i in hint.length downTo 1) {
                text = hint.take(i - 1)
                delay(12)
            }
            h++
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun M3SearchScreen(
    items: List<VideoItem>,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var recent by remember { mutableStateOf(recentSearches(context)) }
    var fieldFocused by remember { mutableStateOf(false) }
    // Reduce motion skips the entry stretch — the field is full width
    // from the first frame, and transitions are fade-only.
    val reduceMotion = remember { isReduceMotion(context) }
    // #1: the field starts as a small pill and springs open.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val fieldWidth by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0.16f,
        animationSpec = Motion.bouncy(),
        label = "searchExpand"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0.7f,
        animationSpec = Motion.bouncy(),
        label = "searchIcon"
    )
    // #5: the field rises a little when focused.
    val fieldRise by animateDpAsState(
        targetValue = if (fieldFocused && !reduceMotion) (-8).dp else 0.dp,
        animationSpec = Motion.soft(),
        label = "searchRise"
    )
    val shown = searchFilter(items, search)
    val resume = remember { resumePositions(context) }
    // Voice search — mic button only when the box has a recognizer.
    val onVoice = rememberVoiceSearch(context) { spoken ->
        onSearch(spoken)
        saveRecentSearch(context, spoken)
        recent = recentSearches(context)
    }
    // Running a search: save it to Recent and leave the field.
    val runSearch: () -> Unit = {
        if (search.isNotBlank()) {
            saveRecentSearch(context, search)
            recent = recentSearches(context)
            focusManager.clearFocus()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        if (wide) {
            // TV: title + field on top, keyboard left, results right.
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                M3SearchTitle(visible = !fieldFocused, reduceMotion = reduceMotion)
                M3SearchFieldRow(
                    search = search, onSearch = onSearch,
                    fieldWidth = fieldWidth, iconScale = iconScale, fieldRise = fieldRise,
                    fieldFocused = fieldFocused, onFieldFocused = { fieldFocused = it },
                    onVoice = onVoice, onCancel = {
                        onSearch("")
                        focusManager.clearFocus()
                    },
                    onSubmit = runSearch,
                    reduceMotion = reduceMotion
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1.25f)) {
                        M3OnScreenKeyboard(
                            query = search, onQuery = onSearch,
                            onSearch = runSearch, onVoice = onVoice
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        M3SearchMeta(
                            search = search, shownCount = shown.size, totalCount = items.size,
                            recent = recent, fieldFocused = fieldFocused,
                            reduceMotion = reduceMotion,
                            onRecent = { onSearch(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            searchResultsItems(shown, search, resume, onOpen) {
                                runSearch()
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            // Narrow: the field, then the keyboard under it when
            // focused, then the results — one scrolling list.
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    M3SearchTitle(visible = !fieldFocused, reduceMotion = reduceMotion)
                    M3SearchFieldRow(
                        search = search, onSearch = onSearch,
                        fieldWidth = fieldWidth, iconScale = iconScale, fieldRise = fieldRise,
                        fieldFocused = fieldFocused, onFieldFocused = { fieldFocused = it },
                        onVoice = onVoice, onCancel = {
                            onSearch("")
                            focusManager.clearFocus()
                        },
                        onSubmit = runSearch,
                        reduceMotion = reduceMotion
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (fieldFocused) {
                    item {
                        M3OnScreenKeyboard(
                            query = search, onQuery = onSearch,
                            onSearch = runSearch, onVoice = onVoice
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item {
                    M3SearchMeta(
                        search = search, shownCount = shown.size, totalCount = items.size,
                        recent = recent, fieldFocused = fieldFocused,
                        reduceMotion = reduceMotion,
                        onRecent = { onSearch(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                searchResultsItems(shown, search, resume, onOpen) {
                    runSearch()
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/** The "Search" title — slides away when the field is focused (#5). */
@Composable
private fun M3SearchTitle(visible: Boolean, reduceMotion: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { -it / 2 },
        exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutVertically { -it / 2 }
    ) {
        Column {
            Text(
                "Search",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** The search field row: the expanding pill (#1), the field rising
 * on focus (#5), Cancel sliding in beside it. `onSubmit` runs the
 * search (the IME Search key) — it saves the query to Recent and
 * leaves the field; it does NOT clear the query. */
@Composable
private fun M3SearchFieldRow(
    search: String,
    onSearch: (String) -> Unit,
    fieldWidth: Float,
    iconScale: Float,
    fieldRise: androidx.compose.ui.unit.Dp,
    fieldFocused: Boolean,
    onFieldFocused: (Boolean) -> Unit,
    onVoice: (() -> Unit)?,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    reduceMotion: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.offset { IntOffset(0, fieldRise.roundToPx()) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fieldWidth)
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    if (fieldFocused) 2.dp else 1.dp,
                    if (fieldFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                )
                Spacer(modifier = Modifier.width(12.dp))
                // The text field fades in only once the pill is wide
                // enough to hold it — before that the pill is just
                // the icon, expanding.
                AnimatedVisibility(
                    visible = fieldWidth > 0.55f || reduceMotion,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = search,
                            onValueChange = onSearch,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                            modifier = Modifier.weight(1f)
                                .onFocusChanged { onFieldFocused(it.hasFocus) },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (search.isEmpty()) M3TypewriterHint()
                                    inner()
                                }
                            }
                        )
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { onSearch("") }) {
                                Icon(
                                    Icons.Filled.Clear, contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (onVoice != null) {
                            IconButton(onClick = { onVoice() }) {
                                Icon(
                                    Icons.Filled.Mic, contentDescription = "Voice search",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        // #5: Cancel slides in beside the field when focused.
        AnimatedVisibility(
            visible = fieldFocused,
            enter = if (reduceMotion) fadeIn() else fadeIn() + slideInHorizontally { it },
            exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutHorizontally { it }
        ) {
            ZephTextButton("Cancel", onClick = onCancel)
        }
    }
}

/** The count line + Recent chips, above the results. */
@Composable
private fun M3SearchMeta(
    search: String,
    shownCount: Int,
    totalCount: Int,
    recent: List<String>,
    fieldFocused: Boolean,
    reduceMotion: Boolean,
    onRecent: (String) -> Unit
) {
    if (search.isBlank()) {
        // #5: the Recent chips drop in below the field when it is
        // focused, and slide away with Cancel.
        AnimatedVisibility(
            visible = fieldFocused && recent.isNotEmpty(),
            enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
            exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutVertically { it / 2 }
        ) {
            Column {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it }) { q ->
                        ZephFilterChip(q, selected = false, onClick = { onRecent(q) })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        Text(
            "$totalCount files",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            "$shownCount results for '$search'",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The results, as LazyListScope items — shared by the wide and
 * narrow layouts. */
private fun LazyListScope.searchResultsItems(
    shown: List<VideoItem>,
    search: String,
    resume: Map<String, Long>,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onRunSearch: () -> Unit
) {
    if (shown.isEmpty() && search.isNotBlank()) {
        item {
            Text(
                "No results for '$search'. Try a shorter name or browse Folders.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        items(shown, key = { it.path }) { item ->
            val resumeMs = resume[item.path] ?: 0L
            val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
            M3SearchRow(item, resumeFraction = fraction) {
                onRunSearch()
                onOpen(item, shown)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------- The on-screen keyboard (v5) ----------
// A real QWERTY keyboard for the remote: 58dp keys with 16dp
// expressive radii, a real stagger (rows 2–3 indented), clear
// groups — letter keys in surfaceContainer, function keys (123,
// caps, backspace, mic) in secondaryContainer, Space wide, and
// Search as a proper primary Filled button with the word "Search".
// Focus fills the key with primary (like the design's M key).
// 123 swaps to a symbols row; caps toggles case. Every key is
// D-pad focusable (zephFocus: scale + ring).

@Composable
private fun M3OnScreenKeyboard(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onVoice: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var caps by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    val letters = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )
    val symbolRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "&", "@", "\""),
        listOf(".", ",", "?", "!", "'")
    )
    val rows = if (symbols) symbolRows else letters
    fun type(text: String) {
        onQuery(query + if (caps && !symbols) text.uppercase() else text.lowercase())
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Keyboard, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Keyboard",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "D-pad to move · OK to type",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = when (rowIndex) { 1 -> 26.dp; 2 -> 52.dp; else -> 0.dp }),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                row.forEach { key ->
                    M3Key(
                        label = key,
                        onClick = { type(key) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Backspace sits at the end of the bottom letter row.
                if (rowIndex == 2) {
                    M3Key(
                        onClick = { onQuery(query.dropLast(1)) },
                        modifier = Modifier.weight(1.6f),
                        fn = true,
                        backspace = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(9.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            M3Key(
                label = if (symbols) "ABC" else "123",
                onClick = { symbols = !symbols },
                modifier = Modifier.weight(1f),
                fn = true
            )
            M3Key(
                icon = Icons.Filled.KeyboardCapslock,
                onClick = { caps = !caps },
                modifier = Modifier.weight(1f),
                fn = true,
                active = caps
            )
            M3Key(
                label = "Space",
                onClick = { onQuery("$query ") },
                modifier = Modifier.weight(3.2f)
            )
            if (onVoice != null) {
                M3Key(
                    icon = Icons.Filled.Mic,
                    onClick = { onVoice() },
                    modifier = Modifier.weight(1f),
                    fn = true
                )
            }
            M3Key(
                label = "Search",
                icon = Icons.Filled.Search,
                onClick = onSearch,
                modifier = Modifier.weight(2f),
                primary = true
            )
        }
    }
}

/** One keyboard key: 58dp, 16dp radius, primary fill on focus. */
@Composable
private fun M3Key(
    label: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fn: Boolean = false,
    primary: Boolean = false,
    active: Boolean = false,
    backspace: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val container = when {
        primary -> MaterialTheme.colorScheme.primary
        focused -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primaryContainer
        fn -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val content = when {
        primary || focused -> MaterialTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        fn -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(58.dp)
            .zephFocus(interaction)
            .clip(RoundedCornerShape(16.dp))
            .background(container, RoundedCornerShape(16.dp))
            .border(
                if (fn || primary || focused) 0.dp else 1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (backspace) {
            Icon(
                Icons.Filled.Backspace, contentDescription = "Backspace",
                tint = content, modifier = Modifier.size(24.dp)
            )
        } else if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                if (label != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = content
                    )
                }
            }
        } else {
            Text(
                label ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = content
            )
        }
    }
}

/** A Search result row: cover tile, title + meta, resume bar. */
@Composable
private fun M3SearchRow(
    item: VideoItem,
    resumeFraction: Float,
    onOpen: () -> Unit
) {
    ZephCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(120.dp, 68.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (item.isVideo) {
                    VideoThumb(item, Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Search, contentDescription = null,
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
                            .padding(4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Color(0xB3000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    searchSub(item) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (resumeFraction > 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ZephLinearProgress(progress = resumeFraction)
                }
            }
        }
    }
}
