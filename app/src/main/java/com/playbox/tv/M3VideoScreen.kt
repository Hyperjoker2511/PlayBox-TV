package com.playbox.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---------- ZEPH Video screen (Patch 3 — the design's Videos.tsx) ----------
// Header + grid/list segmented, filter chips (All / In progress /
// Unwatched) + sort chips (Newest / A–Z / Longest), a 4-col grid of
// VideoCards or list rows. Cards and rows open Details (Zeph's flow
// — Details carries the engine door; the design opens the player
// directly, but Zeph's Details-first flow is the kept behaviour).
// No "4K HDR" chip: there is no per-file HDR flag until playback,
// so a filter on it would be a dead button (same reason Home has
// no HDR badge).

@Composable
internal fun M3VideoScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    // 2.0.4 (search-icon fix): the rail's Search button is cut off
    // when the Library is selected, so the Library gets its own
    // Search button in the header — search is reachable from here
    // even when the rail icon is clipped.
    onSearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val sortPrefs = remember { context.getSharedPreferences("playbox_sort", Context.MODE_PRIVATE) }
    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("recent") }
    var view by remember {
        mutableStateOf(
            when (sortPrefs.getString("browse_layout", "grid")) {
                "list" -> "list"
                else -> "grid"
            }
        )
    }
    val shown = remember(videos, filter, sort, resume) {
        val filtered = videos.filter { item ->
            val p = resumeFractionOf(item, resume)
            when (filter) {
                "progress" -> p > 0.01f && p < 0.99f
                "unwatched" -> p < 0.01f
                else -> true
            }
        }
        when (sort) {
            "title" -> filtered.sortedBy { it.name.lowercase() }
            "length" -> filtered.sortedByDescending { it.durationMs }
            else -> filtered.sortedByDescending { it.dateAddedSec }
        }
    }
    // M15 (2.0.1): autofocus on entry — opening this tab from
    // Home "See all", Details "See all" or the player landed
    // with nothing focused. The first filter chip is the entry
    // point; the user navigates down into the grid from there.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(20.dp))
        M3ScreenHeader(
            title = "Video",
            subtitle = "${shown.size} videos · ${videos.size} in library",
            actions = {
                // 2.0.4 (search-icon fix): a Search button in the
                // Library header, so search is reachable from here.
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Outlined.Search, contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                SingleChoiceSegmentedButtonRow {
                    listOf(
                        Triple("grid", Icons.Filled.GridView, "Grid"),
                        Triple("list", Icons.Filled.List, "List")
                    ).forEachIndexed { index, (value, icon, label) ->
                        SegmentedButton(
                            selected = view == value,
                            onClick = {
                                view = value
                                sortPrefs.edit().putString("browse_layout", value).apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            icon = {
                                Icon(
                                    if (view == value) Icons.Filled.Check else icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        )
        // Filter chips + divider + sort icon + sort chips. A LazyRow
        // so the row scrolls sideways on a narrow phone instead of
        // wrapping or clipping. (The header's own 16dp bottom margin
        // is the design's mb-4; the chips' 12dp below is its mb-3.)
        // 2.0.4: end content padding, so the last chip is never cut
        // off at the screen edge.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 24.dp)
        ) {
            item {
                ZephFilterChip(
                    "All", selected = filter == "all", onClick = { filter = "all" },
                    modifier = Modifier.focusRequester(firstFocus)
                )
            }
            item {
                ZephFilterChip("In progress", selected = filter == "progress", onClick = { filter = "progress" })
            }
            item {
                ZephFilterChip("Unwatched", selected = filter == "unwatched", onClick = { filter = "unwatched" })
            }
            item {
                Box(
                    modifier = Modifier.height(32.dp).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier.height(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Sort, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            item {
                ZephFilterChip("Newest", selected = sort == "recent", onClick = { sort = "recent" })
            }
            item {
                ZephFilterChip("A–Z", selected = sort == "title", onClick = { sort = "title" })
            }
            item {
                ZephFilterChip("Longest", selected = sort == "length", onClick = { sort = "length" })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val cols = if (maxWidth >= 700.dp) 4 else 2
            // The design re-mounts the grid on filter/sort change
            // (key on the container), replaying the rise.
            key(filter, sort) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (shown.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.VideoLibrary, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (loading) "Loading…" else "Nothing here yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (view == "grid") {
                        itemsIndexed(shown.chunked(cols), key = { _, row -> row.firstOrNull()?.path ?: "row" }) { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEachIndexed { colIndex, item ->
                                    M3VideoCard(
                                        item = item,
                                        progress = resumeFractionOf(item, resume),
                                        index = rowIndex * cols + colIndex,
                                        onOpen = { onOpen(item, shown) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat((cols - row.size).coerceAtLeast(0)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    } else {
                        items(shown, key = { it.path }) { item ->
                            M3VideoListRow(
                                item = item,
                                progress = resumeFractionOf(item, resume),
                                index = shown.indexOf(item),
                                onOpen = { onOpen(item, shown) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun resumeFractionOf(item: VideoItem, resume: Map<String, Long>): Float {
    val ms = resume[item.path] ?: 0L
    return if (item.durationMs > 0) ms.toFloat() / item.durationMs else 0f
}

/** The design's Video list row (Videos.tsx): 96×54 thumb with a
 * progress bar at its base, title + "duration · container · size"
 * meta, and a trailing duration + progress column (96dp). A
 * ListItem: 28dp radius (rounded-xl), lift-sm focus (1.03). */
@Composable
private fun M3VideoListRow(
    item: VideoItem,
    progress: Float,
    index: Int,
    onOpen: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(28.dp))
            .zephFocus(interaction, focusScale = 1.03f, cornerRadius = 28.dp)
            .clickable(interactionSource = interaction, indication = null) { onOpen() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .riseIn(minOf(index, 10) * 35),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(96.dp, 54.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            VideoThumb(item, Modifier.fillMaxSize())
            if (progress > 0.01f && progress < 0.99f) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(
                    if (item.durationMs > 0L) TimeFormatter.format(item.durationMs) else "",
                    containerOf(item),
                    formatSize(item.sizeBytes)
                ).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(96.dp)
        ) {
            if (item.durationMs > 0L) {
                Text(
                    TimeFormatter.format(item.durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress > 0.01f && progress < 0.99f) {
                Spacer(modifier = Modifier.height(4.dp))
                ZephLinearProgress(progress = progress)
            }
        }
    }
}
