package com.playbox.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SearchScreen(items: List<VideoItem>, search: String, onSearch: (String) -> Unit, onOpen: (VideoItem) -> Unit) {
    val shown = if (search.isBlank()) items else items.filter { it.name.contains(search, ignoreCase = true) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        item {
            Text("Search", fontSize = 36.sp, color = White)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = search, onValueChange = onSearch, label = { Text("Search your files") }, modifier = Modifier.fillMaxWidth(0.5f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("${shown.size} files", fontSize = 18.sp, color = Muted)
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (shown.isEmpty()) {
            item { Text("No matches. Try a shorter word, or check Folders / USB.", fontSize = 20.sp, color = Muted) }
        } else {
            items(shown.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { item -> TvTile(item.name, if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null) { onOpen(item) } }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

