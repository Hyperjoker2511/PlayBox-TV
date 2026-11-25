package com.playbox.tv

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- NOVA Library (2026-11-23) ----------
// The same Library as Qwen (modes, sort, filters, playlists,
// folders, SAF, Network) — but laid out as a list, not a grid of
// glass tiles: a mode strip of chips, a toolbar of solid buttons,
// and the videos as NovaRows. The folder browsing, the SAF picker
// and the Network screen are the same logic, restyled. The grid
// view is still there (NovaGridCard), but List is the default —
// Nova lists, it does not grid.

@Composable
internal fun NovaLibraryScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    mode: String,
    onMode: (String) -> Unit,
    folder: File?,
    onFolder: (File?) -> Unit,
    safFolder: Uri?,
    onSafFolder: (Uri?) -> Unit,
    listState: LazyListState,
    networkServer: SmbServer?,
    onNetworkServer: (SmbServer?) -> Unit,
    networkPath: String?,
    onNetworkPath: (String?) -> Unit,
    ftpServer: FtpServer?,
    onFtpServer: (FtpServer?) -> Unit,
    ftpPath: String?,
    onFtpPath: (String?) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onRefresh: () -> Unit,
    // Patch 4 (cinematic): play a video straight from the hero.
    onPlay: (VideoItem, List<VideoItem>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val sortPrefs = remember { context.getSharedPreferences("playbox_sort", Context.MODE_PRIVATE) }
    var sortOrder by remember { mutableStateOf(VideoSorter.fromName(sortPrefs.getString("order", "DATE_ADDED") ?: "DATE_ADDED")) }
    // Patch 4 (cinematic browse): List / Grid / Cinematic. Migrates
    // from the old view_grid boolean (true = grid, false = list).
    var browseLayout by remember {
        mutableStateOf(
            sortPrefs.getString("browse_layout", null)
                ?: if (sortPrefs.getBoolean("view_grid", false)) "grid" else "list"
        )
    }
    // Patch 4: the item the cinematic hero shows — the focused grid
    // card drives it; null = the first item in the list.
    var cinematicFocus by remember { mutableStateOf<VideoItem?>(null) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var favourites by remember { mutableStateOf(LibraryStores.favourites(context)) }
    var playlistNames by remember { mutableStateOf(LibraryStores.playlistNames(context)) }
    val recent = remember { LibraryStores.recentPaths(context) }
    val resume = remember { resumePositions(context) }
    var safFolders by remember { mutableStateOf(MediaLibrary.safFolders(context)) }
    var safPickerError by remember { mutableStateOf<String?>(null) }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            MediaLibrary.addSafFolder(context, uri)
            safFolders = MediaLibrary.safFolders(context)
            onSafFolder(uri)
        }
    }
    val roots = remember { MediaLibrary.storageRoots() }

    var folderContents by remember { mutableStateOf<Pair<List<File>, List<VideoItem>>>(emptyList<File>() to emptyList()) }
    LaunchedEffect(folder) {
        folderContents = emptyList<File>() to emptyList()
        val f = folder
        if (f != null) folderContents = withContext(Dispatchers.IO) { MediaLibrary.listFolder(f) }
    }
    var safContents by remember { mutableStateOf<Pair<List<Uri>, List<VideoItem>>>(emptyList<Uri>() to emptyList()) }
    LaunchedEffect(safFolder) {
        safContents = emptyList<Uri>() to emptyList()
        val u = safFolder
        if (u != null) safContents = withContext(Dispatchers.IO) { MediaLibrary.listSafFolder(context, u) }
    }
    val (dirs, media) = folderContents
    val (safDirs, safMedia) = safContents
    val sortedMedia = remember(media, sortOrder) { VideoSorter.sort(media, sortOrder) }
    val sortedSafMedia = remember(safMedia, sortOrder) { VideoSorter.sort(safMedia, sortOrder) }

    var safNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(safFolders, safDirs) {
        val uris = (safFolders + safDirs).distinct()
        safNames = withContext(Dispatchers.IO) {
            uris.associate { it.toString() to MediaLibrary.safFolderName(context, it) }
        }
    }
    fun safName(uri: Uri): String = safNames[uri.toString()] ?: "Folder"

    fun navigateUp() {
        if (safFolder != null) {
            val parent = runCatching {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(context, safFolder)?.parentFile?.uri
            }.getOrNull()
            onSafFolder(if (parent != null && safFolders.any { parent.toString().startsWith(it.toString()) }) parent else null)
        } else {
            val parent = folder?.parentFile
            onFolder(if (parent != null && roots.any { parent.absolutePath.startsWith(it.absolutePath) }) parent else null)
        }
    }
    BackHandler(enabled = folder != null || safFolder != null) { navigateUp() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Library", fontSize = 28.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Videos", "Music", "Folders / USB", "Network").forEach { m ->
                NovaChip(m, active = mode == m) {
                    onMode(m); onFolder(null); onSafFolder(null)
                    filter = LibraryFilter.ALL; openPlaylist = null; cinematicFocus = null
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (mode != "Network") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaButton("Sort: ${VideoSorter.label(sortOrder)}", onClick = {
                    sortOrder = VideoSorter.next(sortOrder)
                    sortPrefs.edit().putString("order", sortOrder.name).apply()
                }, modifier = Modifier.height(44.dp))
                NovaButton("Layout: ${browseLayoutLabel(browseLayout)}", onClick = {
                    browseLayout = nextBrowseLayout(browseLayout)
                    sortPrefs.edit().putString("browse_layout", browseLayout).apply()
                }, modifier = Modifier.height(44.dp))
                NovaButton("Refresh", onClick = onRefresh, modifier = Modifier.height(44.dp))
            }
            if (mode == "Videos" || mode == "Music") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        LibraryFilter.ALL to "All",
                        LibraryFilter.RECENT to "Recently played",
                        LibraryFilter.CONTINUE to "Continue watching",
                        LibraryFilter.FAVOURITES to "Favourites",
                        LibraryFilter.PLAYLISTS to "Playlists"
                    ).forEach { (f, label) ->
                        NovaChip(label, active = filter == f) { filter = f; openPlaylist = null; cinematicFocus = null }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
        when (mode) {
            "Network" -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    NetworkScreen(
                        server = networkServer, onServer = onNetworkServer,
                        path = networkPath, onPath = onNetworkPath,
                        ftpServer = ftpServer, onFtpServer = onFtpServer,
                        ftpPath = ftpPath, onFtpPath = onFtpPath,
                        onOpen = onOpen
                    )
                }
            }
            else -> {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val cols = (maxWidth / 240.dp).toInt().coerceIn(1, 6)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        when (mode) {
                            "Folders / USB" -> {
                                if (folder == null && safFolder == null) {
                                    items(roots, key = { it.absolutePath }) { root ->
                                        NovaFolderRow(root.absolutePath, icon = "▤") { onFolder(root) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(safFolders, key = { it.toString() }) { safUri ->
                                        NovaFolderRow("Folder: ${safName(safUri)}", icon = "▤") { onSafFolder(safUri) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    item {
                                        NovaButton("Add a folder") {
                                            safPickerError = null
                                            try { addFolderLauncher.launch(null) } catch (_: Exception) {
                                                safPickerError = "This TV has no folder picker. Use the storage rows above, or the Videos tab."
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Pick a USB drive or folder once — PlayBox remembers it, even after a restart.", fontSize = 16.sp, color = NovaMuted)
                                        safPickerError?.let { Spacer(modifier = Modifier.height(12.dp)); Text(it, fontSize = 16.sp, color = NovaMuted) }
                                    }
                                    if (roots.isEmpty() && safFolders.isEmpty()) item { Text("No storage found. Plug in a USB drive and open this screen again, or add a folder above.", fontSize = 16.sp, color = NovaMuted) }
                                } else if (safFolder != null) {
                                    item {
                                        NovaButton("Back", onClick = { navigateUp() }, modifier = Modifier.height(44.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(safFolder?.let { safName(it) } ?: "", fontSize = 18.sp, color = NovaMuted)
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    items(safDirs, key = { it.toString() }) { dirUri ->
                                        NovaFolderRow("Folder: ${safName(dirUri)}", icon = "▤") { onSafFolder(dirUri) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(sortedSafMedia, key = { it.path }) { item ->
                                        NovaRow(item, resumeFraction = 0f) { onOpen(item, sortedSafMedia) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    if (safDirs.isEmpty() && sortedSafMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 16.sp, color = NovaMuted) }
                                    item {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        NovaButton("Remove this folder") {
                                            val removed = safFolder
                                            onSafFolder(null)
                                            MediaLibrary.removeSafFolder(context, removed)
                                            safFolders = MediaLibrary.safFolders(context)
                                        }
                                    }
                                } else {
                                    item {
                                        NovaButton("Back", onClick = { navigateUp() }, modifier = Modifier.height(44.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(folder?.absolutePath ?: "", fontSize = 18.sp, color = NovaMuted)
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    items(dirs, key = { it.absolutePath }) { dir ->
                                        NovaFolderRow("Folder: ${dir.name}", icon = "▤") { onFolder(dir) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(sortedMedia, key = { it.path }) { item ->
                                        NovaRow(item, resumeFraction = 0f) { onOpen(item, sortedMedia) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    if (dirs.isEmpty() && sortedMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 16.sp, color = NovaMuted) }
                                }
                            }
                            else -> {
                                val base = VideoSorter.sort(if (mode == "Videos") videos else audio, sortOrder)
                                if (filter == LibraryFilter.PLAYLISTS) {
                                    val open = openPlaylist
                                    if (open == null) {
                                        items(playlistNames, key = { it }) { name ->
                                            val count = LibraryStores.playlistItems(context, name).size
                                            NovaFolderRow("$name  •  $count videos", icon = "≡") { openPlaylist = name }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        item {
                                            NovaButton("New playlist") {
                                                val name = LibraryStores.nextPlaylistName(context)
                                                LibraryStores.createPlaylist(context, name)
                                                playlistNames = LibraryStores.playlistNames(context)
                                                openPlaylist = name
                                            }
                                        }
                                        if (playlistNames.isEmpty()) item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("No playlists yet — tap New playlist, then add videos from their Details page.", fontSize = 16.sp, color = NovaMuted)
                                        }
                                    } else {
                                        val inPlaylist = base.filter { LibraryStores.playlistItems(context, open).contains(it.path) }
                                        item {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                NovaButton("Back", onClick = { openPlaylist = null }, modifier = Modifier.height(44.dp))
                                                NovaButton("Delete playlist", onClick = {
                                                    LibraryStores.deletePlaylist(context, open)
                                                    playlistNames = LibraryStores.playlistNames(context)
                                                    openPlaylist = null
                                                }, modifier = Modifier.height(44.dp))
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(open, fontSize = 24.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        novaMediaRows(inPlaylist, browseLayout == "grid", cols, resume, favourites,
                                            onToggleFav = { path ->
                                                LibraryStores.toggleFavourite(context, path)
                                                favourites = LibraryStores.favourites(context)
                                            },
                                            onOpen = onOpen,
                                            emptyText = "This playlist is empty — add videos from their Details page.")
                                    }
                                } else {
                                    val shown = filterLibraryItems(base, filter, favourites, recent, resume.keys)
                                    val emptyText = when (filter) {
                                        LibraryFilter.ALL -> if (loading) "Loading…" else "No ${mode.lowercase()} found yet."
                                        LibraryFilter.RECENT -> "Nothing played yet — play a video and it shows up here."
                                        LibraryFilter.CONTINUE -> "No videos in progress."
                                        LibraryFilter.FAVOURITES -> "No favourites yet — tap ☆ on a list row, or mark one from a video's Details page."
                                        LibraryFilter.PLAYLISTS -> ""
                                    }
                                    if (browseLayout == "cinematic" && (mode == "Videos" || mode == "Music")) {
                                        // Patch 4: cinematic browse — the
                                        // focused video's backdrop fills
                                        // a hero above a poster grid.
                                        cinematicMediaRows(shown, cols, resume, favourites,
                                            onToggleFav = { path ->
                                                LibraryStores.toggleFavourite(context, path)
                                                favourites = LibraryStores.favourites(context)
                                            },
                                            onOpen = onOpen,
                                            emptyText = emptyText,
                                            focusedItem = cinematicFocus ?: shown.firstOrNull(),
                                            onFocusItem = { cinematicFocus = it })
                                    } else {
                                        novaMediaRows(shown, browseLayout == "grid", cols, resume, favourites,
                                            onToggleFav = { path ->
                                                LibraryStores.toggleFavourite(context, path)
                                                favourites = LibraryStores.favourites(context)
                                            },
                                            onOpen = onOpen,
                                            emptyText = emptyText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Patch 4 (cinematic browse): the three browse layouts. List is
 * the Nova default, Grid is posters in rows, Cinematic is a hero
 * above a poster grid. */
private fun browseLayoutLabel(layout: String): String = when (layout) {
    "grid" -> "Grid"
    "cinematic" -> "Cinematic"
    else -> "List"
}

private fun nextBrowseLayout(layout: String): String = when (layout) {
    "list" -> "grid"
    "grid" -> "cinematic"
    else -> "list"
}

/** Grid or list rows for a media list, Nova style. The grid uses
 * NovaGridCard (solid, cover on top, title below); the list uses
 * NovaRow. List is the Nova default. */
private fun LazyListScope.novaMediaRows(
    shown: List<VideoItem>,
    viewGrid: Boolean,
    cols: Int,
    resume: Map<String, Long>,
    favourites: Set<String>,
    onToggleFav: (String) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    emptyText: String
) {
    if (shown.isEmpty()) {
        item { Text(emptyText, fontSize = 16.sp, color = NovaMuted) }
    } else if (viewGrid) {
        items(shown.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item ->
                    val resumeMs = resume[item.path] ?: 0L
                    val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                    NovaGridCard(item, resumeFraction = fraction, modifier = Modifier.weight(1f)) { onOpen(item, shown) }
                }
                repeat((cols - row.size).coerceAtLeast(0)) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    } else {
        items(shown, key = { it.path }) { item ->
            val resumeMs = resume[item.path] ?: 0L
            val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
            NovaRow(item, resumeFraction = fraction, isFav = favourites.contains(item.path), onToggleFav = { onToggleFav(item.path) }) { onOpen(item, shown) }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** Patch 4 (cinematic browse): the focused video's backdrop fills a
 * hero (title, meta, Play / Details) above a poster grid. Focusing
 * a poster updates the hero. The mode chips at the top of the
 * Library are the categories — D-pad up from the grid reaches them. */
private fun LazyListScope.cinematicMediaRows(
    shown: List<VideoItem>,
    cols: Int,
    resume: Map<String, Long>,
    favourites: Set<String>,
    onToggleFav: (String) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    emptyText: String,
    focusedItem: VideoItem?,
    onFocusItem: (VideoItem) -> Unit
) {
    if (shown.isEmpty()) {
        item { Text(emptyText, fontSize = 16.sp, color = NovaMuted) }
        return
    }
    // The focused item drives the hero — but only if it is still in
    // this list (mode/filter may have changed since it was focused).
    val hero = if (focusedItem != null && shown.any { it.path == focusedItem.path }) focusedItem else shown.first()
    item {
        CinematicHero(
            item = hero,
            resumeMs = resume[hero.path] ?: 0L,
            onPlay = { onPlay(hero, shown) },
            onDetails = { onOpen(hero, shown) }
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
    items(shown.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { item ->
                val resumeMs = resume[item.path] ?: 0L
                val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                NovaGridCard(
                    item, resumeFraction = fraction,
                    modifier = Modifier.weight(1f).onFocusChanged { state ->
                        if (state.hasFocus) onFocusItem(item)
                    }
                ) { onOpen(item, shown) }
            }
            repeat((cols - row.size).coerceAtLeast(0)) { Spacer(modifier = Modifier.weight(1f)) }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The cinematic hero: the video's cover fills the hero (thumbnail
 * when there is one, Nova gradient when there is not), with a dark
 * fade at the bottom for the title, meta and actions. Read-only —
 * Play opens the video, Details opens its page. */
@Composable
private fun CinematicHero(
    item: VideoItem,
    resumeMs: Long,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(item.path) { mutableStateOf(MediaLibrary.ThumbnailCache.get(item.path)) }
    LaunchedEffect(item.path) {
        if (bitmap == null && item.isVideo) {
            bitmap = withContext(Dispatchers.IO) { MediaLibrary.loadVideoThumbnail(context, item) }
        }
    }
    val bmp = bitmap
    Box(
        modifier = Modifier.fillMaxWidth().height(300.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(NovaViolet, NovaSurface, NovaBg))
                ),
                contentAlignment = Alignment.Center
            ) {
                if (!item.isVideo) Text("♫", fontSize = 64.sp, color = NovaMint)
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)), startY = 0.35f)
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
        ) {
            Text(item.name, fontSize = 26.sp, color = NovaWhite, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            Text(tileSub(item) ?: "", fontSize = 16.sp, color = NovaWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NovaButton(if (resumeMs > 0) "Resume" else "Play", primary = true, onClick = onPlay, modifier = Modifier.height(48.dp))
                NovaButton("Details", onClick = onDetails, modifier = Modifier.height(48.dp))
            }
        }
    }
}

// A grid card: the cover on top (16:9), the title + meta below,
// and a thin mint resume bar. Solid — no glass, no tile chrome.
@Composable
private fun NovaGridCard(
    item: VideoItem,
    resumeFraction: Float = 0f,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    NovaCard(onClick = onOpen, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
                else Box(
                    Modifier.fillMaxSize().background(NovaSurfaceHigh),
                    contentAlignment = Alignment.Center
                ) { Text("♫", fontSize = 28.sp, color = NovaMint) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(item.name, fontSize = 17.sp, color = NovaWhite, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(tileSub(item) ?: "", fontSize = 14.sp, color = NovaMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (resumeFraction > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(NovaOutline)) {
                    Box(modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(4.dp).background(NovaMint))
                }
            }
        }
    }
}
