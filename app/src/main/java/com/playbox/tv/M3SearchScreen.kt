package com.playbox.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- ZEPH Search (v7 1.9.1 — keyboard rebuilt from research) ----------
// The keyboard is HIDDEN until you want to type: it only appears
// when you tap the search field (or press OK on it with the remote),
// and Back hides it again. The keyboard itself is rebuilt from the
// Gboard-TV / YouTube TV / Netflix research: a compact floating
// panel, suggestion pills on top, NO stagger (rows line up straight,
// because D-pad moves are up/down/left/right), one key size (48dp),
// Space widest, Search the only accent key, number superscripts on
// the top row, focus starts on Q. On a small screen it goes compact
// (36dp keys, no suggestions) so it never clips off the bottom.
// The field is read-only — the on-screen keyboard IS the input, so
// the phone's soft keyboard never fights it. The #1/#5 Focus Morph
// (expanding pill, sliding title, Cancel) is gone: the design has
// the shared top bar and a plain 56dp field instead.

@Composable
internal fun M3SearchScreen(
    items: List<VideoItem>,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    voiceRequest: Int = 0
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var recent by remember { mutableStateOf(recentSearches(context)) }
    var keyboardVisible by remember { mutableStateOf(false) }
    // K7: Back hides the keyboard first; only then does Back leave
    // Search (MainActivity's handler takes it from there).
    BackHandler(enabled = keyboardVisible) {
        keyboardVisible = false
        focusManager.clearFocus()
    }
    val shown = searchFilter(items, search)
    // Voice search — mic button only when the box has a recognizer.
    val onVoice = rememberVoiceSearch(context) { spoken ->
        onSearch(spoken)
        saveRecentSearch(context, spoken)
        recent = recentSearches(context)
    }
    // v7: the mic on the Home SearchBar opens Search and starts
    // listening at once — the nonce fires a new listen per tap.
    LaunchedEffect(voiceRequest) {
        if (voiceRequest > 0) onVoice?.invoke()
    }
    // Running a search: save it to Recent and hide the keyboard.
    val runSearch: () -> Unit = {
        if (search.isNotBlank()) {
            saveRecentSearch(context, search)
            recent = recentSearches(context)
        }
        keyboardVisible = false
        focusManager.clearFocus()
    }
    val hideKeyboard: () -> Unit = {
        keyboardVisible = false
        focusManager.clearFocus()
    }
    val showKeyboard: () -> Unit = { keyboardVisible = true }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val short = maxHeight < 500.dp
        // Compact keys on any small screen (phone, or a short TV
        // frame) — full 48dp keys only on a tall wide screen.
        val compactKeyboard = !wide || maxHeight < 600.dp
        if (wide && !short) {
            // TV: top bar + field, then the keyboard on the left
            // (1.25fr) and the results on the right (1fr) — or the
            // results full width while the keyboard is hidden.
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                ZephScreenTopBar("Search")
                Spacer(modifier = Modifier.height(16.dp))
                M3SearchField(
                    search = search, onSearch = onSearch,
                    onVoice = onVoice, onFieldTap = showKeyboard
                )
                if (!keyboardVisible) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Tap the field to type — the keyboard appears · or tap the mic to speak",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    M3RecentChips(recent = recent, onRecent = onSearch)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        searchResultsItems(shown, search, onOpen, runSearch)
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.weight(1.25f)) {
                            M3OnScreenKeyboard(
                                query = search, onQuery = onSearch,
                                onSearch = runSearch, onVoice = onVoice,
                                onHide = hideKeyboard,
                                items = items, recent = recent,
                                compact = compactKeyboard
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            M3RecentChips(recent = recent, onRecent = onSearch)
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                searchResultsItems(shown, search, onOpen, runSearch)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else if (wide && short) {
            // Landscape phone: the keyboard is a floating sheet
            // over the bottom of the results, capped at 62% of the
            // height — it can never clip off the bottom (K2).
            var kbHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ZephScreenTopBar("Search")
                    Spacer(modifier = Modifier.height(12.dp))
                    M3SearchField(
                        search = search, onSearch = onSearch,
                        onVoice = onVoice, onFieldTap = showKeyboard
                    )
                    if (!keyboardVisible) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Tap the field to type — the keyboard appears · or tap the mic to speak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    M3RecentChips(recent = recent, onRecent = onSearch)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = kbHeight + 16.dp)
                    ) {
                        searchResultsItems(shown, search, onOpen, runSearch)
                    }
                }
                if (keyboardVisible) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = maxHeight * 0.62f)
                            .onSizeChanged { kbHeight = with(density) { it.height.toDp() } }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        M3OnScreenKeyboard(
                            query = search, onQuery = onSearch,
                            onSearch = runSearch, onVoice = onVoice,
                            onHide = hideKeyboard,
                            items = items, recent = recent,
                            compact = true
                        )
                    }
                }
            }
        } else {
            // Narrow portrait phone: one scrolling list — field,
            // hint, recents, the compact keyboard in-flow when
            // shown, then the results.
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    ZephScreenTopBar("Search")
                    Spacer(modifier = Modifier.height(16.dp))
                    M3SearchField(
                        search = search, onSearch = onSearch,
                        onVoice = onVoice, onFieldTap = showKeyboard
                    )
                    if (!keyboardVisible) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Tap the field to type — the keyboard appears · or tap the mic to speak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    M3RecentChips(recent = recent, onRecent = onSearch)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (keyboardVisible) {
                    item {
                        M3OnScreenKeyboard(
                            query = search, onQuery = onSearch,
                            onSearch = runSearch, onVoice = onVoice,
                            onHide = hideKeyboard,
                            items = items, recent = recent,
                            compact = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                searchResultsItems(shown, search, onOpen, runSearch)
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/** The search field (K10): 56dp, surface-high, NO border, search
 * icon onSurfaceVariant, a static placeholder (K11 — no
 * typewriter), the mic in a 38dp secondaryContainer circle, and no
 * Clear button. It morphs from the Home SearchBar (S14). Read-only:
 * the on-screen keyboard is the input, so the phone's soft keyboard
 * never appears. Tapping it shows the on-screen keyboard. */
@Composable
private fun M3SearchField(
    search: String,
    onSearch: (String) -> Unit,
    onVoice: (() -> Unit)?,
    onFieldTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth().height(56.dp)
            .then(coverSharedModifier("search-bar"))
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onFieldTap() }
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
            BasicTextField(
                value = search,
                onValueChange = onSearch,
                singleLine = true,
                readOnly = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                modifier = Modifier.weight(1f)
                    .onFocusChanged { if (it.hasFocus) onFieldTap() },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (search.isEmpty()) {
                            Text(
                                "Search videos, music…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            if (onVoice != null) {
                Spacer(modifier = Modifier.width(8.dp))
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

/** Recent searches (K12/K16): AssistChips with a history icon,
 * shown in the hidden frame AND while typing, above the results. */
@Composable
private fun M3RecentChips(
    recent: List<String>,
    onRecent: (String) -> Unit
) {
    if (recent.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(recent, key = { it }) { q ->
            AssistChip(
                onClick = { onRecent(q) },
                label = { Text(q, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.History, contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    borderWidth = 1.dp
                )
            )
        }
    }
}

/** The results, as LazyListScope items — shared by all layouts. */
private fun LazyListScope.searchResultsItems(
    shown: List<VideoItem>,
    search: String,
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
    } else if (shown.isEmpty()) {
        item {
            Text(
                "No videos yet — plug in a USB drive, or copy a video to this TV.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        items(shown, key = { it.path }) { item ->
            M3SearchRow(item) {
                onRunSearch()
                onOpen(item, shown)
            }
            Spacer(modifier = Modifier.height(9.dp))
        }
    }
}

// ---------- The on-screen keyboard (v7, rebuilt from research) ----------
// One key size, no stagger, suggestion pills on top (full size
// only), number superscripts on the top row, focus starts on Q.
// Shift is one-shot (tap again quickly to lock); long-press
// Backspace clears the query. Every key is D-pad focusable
// (zephFocus: scale + ring).

@Composable
private fun M3OnScreenKeyboard(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onVoice: (() -> Unit)?,
    onHide: () -> Unit,
    items: List<VideoItem>,
    recent: List<String>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    var caps by remember { mutableStateOf(false) }
    var capsLock by remember { mutableStateOf(false) }
    var lastShiftTap by remember { mutableLongStateOf(0L) }
    var symbols by remember { mutableStateOf(false) }
    val keyHeight: Dp = if (compact) 36.dp else 48.dp
    val keyRadius: Dp = if (compact) 10.dp else 12.dp
    val gap: Dp = if (compact) 6.dp else 8.dp
    val qFocus = remember { FocusRequester() }
    // K6: focus starts on Q when the keyboard appears.
    LaunchedEffect(Unit) { qFocus.requestFocus() }

    val letters = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "."),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )
    val symbolRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "&", "@", "\"", "%"),
        listOf(".", ",", "?", "!", "'", "_", "=", "+", "*")
    )
    val rows = if (symbols) symbolRows else letters
    val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    fun capsOn(): Boolean = caps || capsLock
    fun type(text: String) {
        onQuery(query + if (capsOn() && !symbols) text.uppercase() else text.lowercase())
        // One-shot caps clears after one letter; a lock stays.
        if (caps && !capsLock) caps = false
    }
    fun onShift() {
        val now = System.currentTimeMillis()
        when {
            capsLock -> { capsLock = false; caps = false }
            caps && now - lastShiftTap < 400L -> { capsLock = true }
            else -> caps = true
        }
        lastShiftTap = now
    }
    // K4: suggestion pills — item names and recents that start
    // with the query, up to 3. Full-size keyboard only.
    val suggestions = remember(query, items, recent) {
        if (query.isBlank()) emptyList()
        else {
            val prefix = query.lowercase()
            val fromItems = items.map { it.name }
                .filter { it.lowercase().startsWith(prefix) && !it.equals(query, ignoreCase = true) }
            val fromRecent = recent.filter { it.lowercase().startsWith(prefix) && !it.equals(query, ignoreCase = true) }
            (fromItems + fromRecent).distinct().take(3)
        }
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(if (compact) 12.dp else 18.dp)
    ) {
        // K8: tools row — keyboard icon + hint + Hide at right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Keyboard, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "D-pad to move · OK to type",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable { onHide() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.KeyboardHide, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Hide keyboard",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // K4: suggestion pills, full size only.
        if (!compact && suggestions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s ->
                    Box(
                        modifier = Modifier.height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onQuery(s) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                if (!symbols && rowIndex == 2) {
                    // Row 3, letters: Shift + Z–M + @ + Backspace.
                    M3Key(
                        icon = Icons.Filled.KeyboardCapslock,
                        onClick = { onShift() },
                        modifier = Modifier.weight(1f),
                        fn = true, active = capsOn(),
                        keyHeight = keyHeight, keyRadius = keyRadius
                    )
                }
                row.forEachIndexed { keyIndex, key ->
                    M3Key(
                        label = if (symbols) key else if (capsOn()) key.uppercase() else key.lowercase(),
                        // K5: number superscripts on the top row.
                        superscript = if (!symbols && rowIndex == 0) numbers.getOrNull(keyIndex) else null,
                        onClick = { type(key) },
                        modifier = Modifier.weight(1f),
                        focusRequester = if (!symbols && rowIndex == 0 && keyIndex == 0) qFocus else null,
                        keyHeight = keyHeight, keyRadius = keyRadius
                    )
                }
                if (!symbols && rowIndex == 2) {
                    M3Key(
                        label = "@",
                        onClick = { type("@") },
                        modifier = Modifier.weight(1f),
                        keyHeight = keyHeight, keyRadius = keyRadius
                    )
                    M3Key(
                        onClick = { onQuery(query.dropLast(1)) },
                        onLongClick = { onQuery("") },
                        modifier = Modifier.weight(1f),
                        fn = true, backspace = true,
                        keyHeight = keyHeight, keyRadius = keyRadius
                    )
                }
                if (symbols && rowIndex == 2) {
                    M3Key(
                        onClick = { onQuery(query.dropLast(1)) },
                        onLongClick = { onQuery("") },
                        modifier = Modifier.weight(1f),
                        fn = true, backspace = true,
                        keyHeight = keyHeight, keyRadius = keyRadius
                    )
                }
            }
            Spacer(modifier = Modifier.height(gap))
        }
        // Bottom row: 123? + Space (flex 4) + - + mic + Search
        // (flex 2, the only accent key) + Hide.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            M3Key(
                label = if (symbols) "ABC" else "123",
                onClick = { symbols = !symbols },
                modifier = Modifier.weight(1f),
                fn = true,
                keyHeight = keyHeight, keyRadius = keyRadius
            )
            M3Key(
                label = "Space",
                onClick = { onQuery("$query ") },
                modifier = Modifier.weight(4f),
                keyHeight = keyHeight, keyRadius = keyRadius
            )
            M3Key(
                label = "-",
                onClick = { type("-") },
                modifier = Modifier.weight(1f),
                fn = true,
                keyHeight = keyHeight, keyRadius = keyRadius
            )
            if (onVoice != null) {
                M3Key(
                    icon = Icons.Filled.Mic,
                    onClick = { onVoice() },
                    modifier = Modifier.weight(1f),
                    fn = true,
                    keyHeight = keyHeight, keyRadius = keyRadius
                )
            }
            M3Key(
                label = "Search",
                icon = Icons.Filled.Search,
                onClick = onSearch,
                modifier = Modifier.weight(2f),
                primary = true,
                keyHeight = keyHeight, keyRadius = keyRadius
            )
            M3Key(
                icon = Icons.Filled.KeyboardHide,
                onClick = onHide,
                modifier = Modifier.weight(1f),
                fn = true,
                keyHeight = keyHeight, keyRadius = keyRadius
            )
        }
    }
}

/** One keyboard key: 48dp (36dp compact), 12dp (10dp) radius,
 * primary fill on focus. A superscript draws the top-row number;
 * a long-press clears the query (Backspace). */
@Composable
private fun M3Key(
    label: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fn: Boolean = false,
    primary: Boolean = false,
    active: Boolean = false,
    backspace: Boolean = false,
    superscript: String? = null,
    focusRequester: FocusRequester? = null,
    keyHeight: Dp,
    keyRadius: Dp
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
    var boxModifier = modifier
        .height(keyHeight)
        .zephFocus(interaction)
        .clip(RoundedCornerShape(keyRadius))
        .background(container, RoundedCornerShape(keyRadius))
        .border(
            if (fn || primary || focused) 0.dp else 1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(keyRadius)
        )
    if (focusRequester != null) boxModifier = boxModifier.focusRequester(focusRequester)
    boxModifier = if (onLongClick != null) {
        boxModifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        boxModifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    }
    Box(
        modifier = boxModifier,
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
        // K5: the top-row number, small, top-right.
        if (superscript != null) {
            Text(
                superscript,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 6.dp)
            )
        }
    }
}

/** A Search result row (K15): a 44dp primaryContainer icon
 * circle + title + "duration · size · container" — no thumbnail,
 * no badge, no star, no resume bar. */
@Composable
private fun M3SearchRow(
    item: VideoItem,
    onOpen: () -> Unit
) {
    ZephCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    listOf(
                        TimeFormatter.format(item.durationMs),
                        formatSize(item.sizeBytes),
                        containerOf(item)
                    ).filter { it.isNotBlank() }.joinToString("  ·  "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
