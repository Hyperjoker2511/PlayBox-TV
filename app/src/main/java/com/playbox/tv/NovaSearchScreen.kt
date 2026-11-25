package com.playbox.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- NOVA Search (2026-11-23) ----------
// No expanding pill, no title that slides away, no Cancel that
// slides in. The field is simply there — full width, 64dp tall, at
// the top — with the results as a vertical list underneath. Recent
// searches are chips under the field while it is empty. The Qwen
// search animation lives in SearchScreen; Nova is deliberately
// plain: the field is the screen.

@Composable
internal fun NovaSearchScreen(
    items: List<VideoItem>,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var recent by remember { mutableStateOf(recentSearches(context)) }
    var fieldFocused by remember { mutableStateOf(false) }
    // Patch 4 (Jio): voice search — the mic button only shows when
    // the box has a voice recognizer (see VoiceSearch.kt).
    val onVoice = rememberVoiceSearch(context) { spoken ->
        onSearch(spoken)
        saveRecentSearch(context, spoken)
        recent = recentSearches(context)
    }
    val shown = searchFilter(items, search)
    val resume = remember { resumePositions(context) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Search", fontSize = 28.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .background(NovaSurface, RoundedCornerShape(18.dp))
                    .border(if (fieldFocused) 2.dp else 1.dp, if (fieldFocused) NovaViolet else NovaOutline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", fontSize = 24.sp, color = NovaMint)
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = search,
                        onValueChange = onSearch,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 20.sp, color = NovaWhite),
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
                                if (search.isEmpty()) Text("Search your videos and music…", fontSize = 20.sp, color = NovaMuted)
                                inner()
                            }
                        }
                    )
                    if (search.isNotEmpty()) {
                        Text(
                            "×", fontSize = 26.sp, color = NovaMuted,
                            modifier = Modifier.clickable { onSearch("") }.padding(8.dp)
                        )
                    }
                    if (onVoice != null) {
                        Text(
                            "🎙", fontSize = 22.sp, color = NovaMint,
                            modifier = Modifier.clickable { onVoice() }.padding(8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (search.isBlank() && recent.isNotEmpty()) {
            item {
                Text("RECENT", fontSize = 13.sp, color = NovaMuted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it }) { q ->
                        NovaChip(q, active = false) { onSearch(q) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        if (search.isNotBlank()) {
            item {
                NovaSectionHeader("Results", "${shown.size}")
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (shown.isEmpty()) {
                item { Text("Nothing matches \"$search\".", fontSize = 16.sp, color = NovaMuted) }
            } else {
                items(shown, key = { it.path }) { item ->
                    val resumeMs = resume[item.path] ?: 0L
                    val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                    NovaRow(item, resumeFraction = fraction) { onOpen(item, shown) }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        } else {
            item {
                Text("Type to search your videos and music.", fontSize = 16.sp, color = NovaMuted)
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
