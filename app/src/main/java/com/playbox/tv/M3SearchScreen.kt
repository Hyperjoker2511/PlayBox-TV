package com.playbox.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "searchExpand"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0.7f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "searchIcon"
    )
    // #5: the field rises a little when focused.
    val fieldRise by animateDpAsState(
        targetValue = if (fieldFocused && !reduceMotion) (-8).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            // #5: the title slides away when the field is focused
            // (fade only when reduce motion is on).
            AnimatedVisibility(
                visible = !fieldFocused,
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
                        // The text field fades in only once the pill
                        // is wide enough to hold it — before that
                        // the pill is just the icon, expanding.
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
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (search.isNotBlank()) {
                                            saveRecentSearch(context, search)
                                            recent = recentSearches(context)
                                            focusManager.clearFocus()
                                        }
                                    }),
                                    modifier = Modifier.weight(1f)
                                        .onFocusChanged { fieldFocused = it.hasFocus },
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
                // #5: Cancel slides in beside the field when focused
                // (fade only when reduce motion is on).
                AnimatedVisibility(
                    visible = fieldFocused,
                    enter = if (reduceMotion) fadeIn() else fadeIn() + slideInHorizontally { it },
                    exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutHorizontally { it }
                ) {
                    ZephTextButton("Cancel", onClick = {
                        onSearch("")
                        focusManager.clearFocus()
                    })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (search.isBlank()) {
                // #5: the Recent chips drop in below the field when
                // it is focused, and slide away with Cancel.
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
                                ZephFilterChip(q, selected = false, onClick = { onSearch(q) })
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                Text(
                    "${items.size} files",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${shown.size} results for '$search'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
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
                    if (search.isNotBlank()) {
                        saveRecentSearch(context, search)
                        recent = recentSearches(context)
                    }
                    onOpen(item, shown)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
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
