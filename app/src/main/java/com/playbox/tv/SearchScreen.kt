package com.playbox.tv

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------- Patch 3 (Qwen Search + the Short's animations #1 + #5) ----------
// One combined flow: the field stretches open from a narrow pill (#1),
// then on focus the title slides away, the field rises, Cancel slides
// in and Recent chips drop below (#5). All transform + opacity only,
// so it stays cheap on the 2 GB box.

/** Recent searches, newest first, max 8 — one string, queries joined. */
private const val RECENT_SEARCH_PREFS = "playbox_recent_searches"
private const val RECENT_SEARCH_KEY = "queries"

internal fun recentSearches(context: Context): List<String> {
    val raw = context.getSharedPreferences(RECENT_SEARCH_PREFS, Context.MODE_PRIVATE)
        .getString(RECENT_SEARCH_KEY, "") ?: ""
    return raw.split("\u0001").filter { it.isNotBlank() }
}

internal fun saveRecentSearch(context: Context, query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    val prefs = context.getSharedPreferences(RECENT_SEARCH_PREFS, Context.MODE_PRIVATE)
    val updated = (listOf(q) + recentSearches(context).filter { it != q }).take(8)
    prefs.edit().putString(RECENT_SEARCH_KEY, updated.joinToString("\u0001")).apply()
}

/** Pure search filter (unit-tested). */
internal fun searchFilter(items: List<VideoItem>, query: String): List<VideoItem> =
    if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }

/** The type label on a result: "Video • 1:42:07" / "Music • 52:18". */
internal fun searchSub(item: VideoItem): String? {
    val dur = tileSub(item)
    val type = if (item.isVideo) "Video" else "Music"
    return if (dur != null) "$type  •  $dur" else type
}

/** The placeholder hint types itself in, holds, deletes, and moves to
 * the next hint — the #1 typing effect, as a placeholder only. Real
 * text comes from the remote keyboard. */
@Composable
private fun TypewriterHint() {
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
    Text(text, fontSize = 22.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun SearchScreen(items: List<VideoItem>, search: String, onSearch: (String) -> Unit, onOpen: (VideoItem, List<VideoItem>) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var recent by remember { mutableStateOf(recentSearches(context)) }
    var fieldFocused by remember { mutableStateOf(false) }
    // Patch 6: reduce motion skips the entry stretch — the field is
    // full width from the first frame.
    val reduceMotion = remember { isReduceMotion(context) }
    // #1: the field starts narrow and stretches open when the screen appears.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val fieldWidth by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 200f),
        label = "searchExpand"
    )
    val shown = searchFilter(items, search)
    val resume = remember { resumePositions(context) }
    // Adaptive columns (Batch 1.3, Round 2): fixed chunked(3) clips on odd
    // densities, and stable row keys stop focus jumping while typing.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = (maxWidth / 240.dp).toInt().coerceIn(1, 6)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                // #5: the title slides away when the field is focused
                // (fade only when reduce motion is on).
                AnimatedVisibility(
                    visible = !fieldFocused,
                    enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { -it / 2 },
                    exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    Column {
                        Text("Search", fontSize = 36.sp, color = White)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fieldWidth)
                            .height(72.dp)
                            .shadow(if (fieldFocused) 12.dp else 0.dp, RoundedCornerShape(36.dp), ambientColor = FocusGlow, spotColor = FocusGlow)
                            .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)), RoundedCornerShape(36.dp))
                            .border(2.dp, if (fieldFocused) FocusBorder else GlassBorder, RoundedCornerShape(36.dp))
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⌕", fontSize = 26.sp, color = Periwinkle)
                            Spacer(modifier = Modifier.width(12.dp))
                            BasicTextField(
                                value = search,
                                onValueChange = onSearch,
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 22.sp, color = White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (search.isNotBlank()) {
                                        saveRecentSearch(context, search)
                                        recent = recentSearches(context)
                                        focusManager.clearFocus()
                                    }
                                }),
                                modifier = Modifier.weight(1f).onFocusChanged { fieldFocused = it.hasFocus },
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (search.isEmpty()) TypewriterHint()
                                        inner()
                                    }
                                }
                            )
                            if (search.isNotEmpty()) {
                                Text(
                                    "×", fontSize = 26.sp, color = Muted,
                                    modifier = Modifier.clickable { onSearch("") }.padding(8.dp)
                                )
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
                        GlassButton("Cancel") {
                            onSearch("")
                            focusManager.clearFocus()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (search.isBlank()) {
                    if (recent.isNotEmpty()) {
                        Text("Recent", fontSize = 20.sp, color = Muted)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recent, key = { it }) { q ->
                                GlassButton(q) { onSearch(q) }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text("${items.size} files", fontSize = 18.sp, color = Muted)
                } else {
                    Text("${shown.size} results for '$search'", fontSize = 18.sp, color = Muted)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (shown.isEmpty() && search.isNotBlank()) {
                item { Text("No results for '$search'. Try a shorter name or browse Folders.", fontSize = 20.sp, color = Muted) }
            } else {
                items(shown.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { item ->
                            val resumeMs = resume[item.path] ?: 0L
                            val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                            ThumbTile(item, searchSub(item), resumeFraction = fraction) {
                                if (search.isNotBlank()) {
                                    saveRecentSearch(context, search)
                                    recent = recentSearches(context)
                                }
                                onOpen(item, shown)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
