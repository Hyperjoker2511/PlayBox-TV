package com.playbox.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class Tab { Home, Search, Library, Settings }

/** Tile subtitle: duration plus a "May not play" badge for formats
 * Media3 cannot play yet (Batch 1.4 polish). Null when there is nothing
 * to show, so the tile stays clean. */
internal fun tileSub(item: VideoItem): String? {
    val parts = listOfNotNull(
        if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
        if (item.likelyUnsupported) "May not play" else null
    )
    return parts.joinToString("  ·  ").ifBlank { null }
}

@Composable
internal fun TvTile(text: String, sub: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .width(220.dp)
            .background(if (focused) PrimaryContainer else Surface, RoundedCornerShape(24.dp))
            .border(if (focused) 4.dp else 0.dp, if (focused) Primary else Color.Transparent, RoundedCornerShape(24.dp))
            .focusRequester(req).onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Text(text, fontSize = 22.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(sub, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun TvButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    Box(
        modifier = modifier
            .background(if (focused) White else Surface, RoundedCornerShape(28.dp))
            .border(if (focused) 4.dp else 0.dp, if (focused) Primary else Color.Transparent, RoundedCornerShape(28.dp))
            .focusRequester(req).onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Text(text, fontSize = 20.sp, color = if (focused) Color.Black else White, maxLines = 1)
    }
}

@Composable
internal fun Rail(tab: Tab, onTab: (Tab) -> Unit) {
    val items = listOf(
        Tab.Home to "Home", Tab.Search to "Search",
        Tab.Library to "Library", Tab.Settings to "Settings"
    )
    Column(
        modifier = Modifier.width(140.dp).fillMaxHeight().background(Bg).padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("▶", fontSize = 28.sp, color = Primary)
        Text("PlayBox", fontSize = 16.sp, color = White)
        Spacer(modifier = Modifier.height(48.dp))
        items.forEach { (t, label) ->
            var focused by remember { mutableStateOf(false) }
            val req = remember { FocusRequester() }
            val active = tab == t
            Column(
                modifier = Modifier
                    .width(116.dp)
                    .background(if (active) SecondaryContainer else Color.Transparent, RoundedCornerShape(28.dp))
                    .border(if (focused) 4.dp else 0.dp, if (focused) Primary else Color.Transparent, RoundedCornerShape(28.dp))
                    .focusRequester(req).onFocusChanged { focused = it.isFocused }
                    .clickable { onTab(t) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, fontSize = 20.sp, color = if (active) White else Muted)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("You", fontSize = 18.sp, color = Muted)
    }
}

