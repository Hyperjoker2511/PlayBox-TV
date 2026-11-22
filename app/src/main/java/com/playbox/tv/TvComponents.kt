package com.playbox.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** Batch 2: the video's thumbnail, loaded on IO and cached (see
 * MediaLibrary.loadVideoThumbnail). Draws nothing when there is no
 * thumbnail yet — the gradient tile stays underneath. */
@Composable
internal fun VideoThumb(item: VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(item.path) { mutableStateOf(MediaLibrary.ThumbnailCache.get(item.path)) }
    LaunchedEffect(item.path) {
        if (bitmap == null && item.isVideo) {
            bitmap = withContext(Dispatchers.IO) { MediaLibrary.loadVideoThumbnail(context, item) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(24.dp))
        )
    }
}

/** Batch 2: same tile as TvTile, but with the video's thumbnail behind
 * the title. Audio tiles have no thumbnail, so they look like before. */
@Composable
internal fun ThumbTile(item: VideoItem, sub: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(180.dp)
            .background(if (focused) PrimaryContainer else Surface, RoundedCornerShape(24.dp))
            .border(if (focused) 4.dp else 0.dp, if (focused) Primary else Color.Transparent, RoundedCornerShape(24.dp))
            .focusRequester(req).onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
    ) {
        if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
        // Dark scrim at the bottom so the title stays readable on a photo.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, Color(0xCC000000))
                )
            )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(item.name, fontSize = 20.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(sub, fontSize = 15.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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

