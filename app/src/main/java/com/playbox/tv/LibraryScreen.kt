package com.playbox.tv

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- Patch 2 (Qwen Library) ----------
// Large glass pill tabs, a toolbar (Sort / Grid-List / Refresh), filter
// pills for Videos and Music, and honest empty states. The folder, SAF
// and Network logic is unchanged from Batches 1–2 — only the surfaces
// are glass now.

@Composable
internal fun LibraryScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    // Batch 1.3 (B4): mode / folder / safFolder live in PlayBoxApp, not in
    // this screen's remember — so opening a video and coming back no
    // longer dumps the user at the Library root. The list state is hoisted
    // too, so the scroll position survives.
    mode: String,
    onMode: (String) -> Unit,
    folder: File?,
    onFolder: (File?) -> Unit,
    safFolder: Uri?,
    onSafFolder: (Uri?) -> Unit,
    listState: LazyListState,
    // Batch 2: the Network browse state is hoisted too (like the folder
    // state above), so coming back from a video keeps your place.
    networkServer: SmbServer?,
    onNetworkServer: (SmbServer?) -> Unit,
    networkPath: String?,
    onNetworkPath: (String?) -> Unit,
    // Patch 7: FTP/SFTP browse state, same pattern as SMB.
    ftpServer: FtpServer?,
    onFtpServer: (FtpServer?) -> Unit,
    ftpPath: String?,
    onFtpPath: (String?) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    // Batch 2: sort order, remembered in prefs.
    val sortPrefs = remember { context.getSharedPreferences("playbox_sort", Context.MODE_PRIVATE) }
    var sortOrder by remember { mutableStateOf(VideoSorter.fromName(sortPrefs.getString("order", "DATE_ADDED") ?: "DATE_ADDED")) }
    var viewGrid by remember { mutableStateOf(sortPrefs.getBoolean("view_grid", true)) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    // Patch 2 stores. The screen leaves composition entirely while a
    // video or its Details are open, so these remember reads refresh
    // whenever the user comes back.
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

    // B7: folder listings run on Dispatchers.IO, not in remember during
    // composition — DocumentFile does a query per child attribute, and a
    // 500-file USB folder froze the UI on the weak Jio box.
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
    // Batch 2: folder lists follow the same sort order as the grids.
    val sortedMedia = remember(media, sortOrder) { VideoSorter.sort(media, sortOrder) }
    val sortedSafMedia = remember(safMedia, sortOrder) { VideoSorter.sort(safMedia, sortOrder) }

    // B7: safFolderName(...) does a DocumentFile lookup per call — it was
    // called once per row during composition. Cache the names instead.
    var safNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(safFolders, safDirs) {
        val uris = (safFolders + safDirs).distinct()
        safNames = withContext(Dispatchers.IO) {
            uris.associate { it.toString() to MediaLibrary.safFolderName(context, it) }
        }
    }
    fun safName(uri: Uri): String = safNames[uri.toString()] ?: "Folder"

    // B9a: one "go up" used by both the system Back key and the on-screen
    // Back button, so the button can no longer walk above the storage root.
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Library", fontSize = 36.sp, color = White)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Videos", "Music", "Folders / USB", "Network").forEach { m ->
                GlassButton(m, active = mode == m) {
                    onMode(m); onFolder(null); onSafFolder(null)
                    filter = LibraryFilter.ALL; openPlaylist = null
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (mode != "Network") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("Sort: ${VideoSorter.label(sortOrder)}") {
                    sortOrder = VideoSorter.next(sortOrder)
                    sortPrefs.edit().putString("order", sortOrder.name).apply()
                }
                GlassButton(if (viewGrid) "Grid" else "List") {
                    viewGrid = !viewGrid
                    sortPrefs.edit().putBoolean("view_grid", viewGrid).apply()
                }
                GlassButton("Refresh") { onRefresh() }
            }
            if (mode == "Videos" || mode == "Music") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        LibraryFilter.ALL to "All",
                        LibraryFilter.RECENT to "Recently played",
                        LibraryFilter.CONTINUE to "Continue watching",
                        LibraryFilter.FAVOURITES to "Favourites",
                        LibraryFilter.PLAYLISTS to "Playlists"
                    ).forEach { (f, label) ->
                        GlassButton(label, active = filter == f) { filter = f; openPlaylist = null }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
                // Adaptive columns (Batch 1.3, Round 2): see HomeScreen.
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val cols = (maxWidth / 240.dp).toInt().coerceIn(1, 6)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        when (mode) {
                            "Folders / USB" -> {
                                if (folder == null && safFolder == null) {
                                    items(roots, key = { it.absolutePath }) { root ->
                                        FolderRow(root.absolutePath) { onFolder(root) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(safFolders, key = { it.toString() }) { safUri ->
                                        FolderRow("Folder: ${safName(safUri)}") { onSafFolder(safUri) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    item {
                                        GlassButton("Add a folder") {
                                            safPickerError = null
                                            try { addFolderLauncher.launch(null) } catch (_: Exception) {
                                                safPickerError = "This TV has no folder picker. Use the storage rows above, or the Videos tab."
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Pick a USB drive or folder once — PlayBox remembers it, even after a restart.", fontSize = 18.sp, color = Muted)
                                        safPickerError?.let { Spacer(modifier = Modifier.height(12.dp)); Text(it, fontSize = 18.sp, color = Muted) }
                                    }
                                    if (roots.isEmpty() && safFolders.isEmpty()) item { Text("No storage found. Plug in a USB drive and open this screen again, or add a folder above.", fontSize = 20.sp, color = Muted) }
                                } else if (safFolder != null) {
                                    item {
                                        GlassButton("Back") { navigateUp() }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(safFolder?.let { safName(it) } ?: "", fontSize = 20.sp, color = Muted)
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    items(safDirs, key = { it.toString() }) { dirUri ->
                                        FolderRow("Folder: ${safName(dirUri)}") { onSafFolder(dirUri) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(sortedSafMedia, key = { it.path }) { item ->
                                        FolderRow(if (item.likelyUnsupported) "${item.name}  ·  May not play" else item.name) { onOpen(item, sortedSafMedia) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    if (safDirs.isEmpty() && sortedSafMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 20.sp, color = Muted) }
                                    item {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        GlassButton("Remove this folder") {
                                            val removed = safFolder
                                            onSafFolder(null)
                                            // Non-null here: this branch only runs when safFolder != null.
                                            MediaLibrary.removeSafFolder(context, removed)
                                            safFolders = MediaLibrary.safFolders(context)
                                        }
                                    }
                                } else {
                                    item {
                                        GlassButton("Back") { navigateUp() }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(folder?.absolutePath ?: "", fontSize = 20.sp, color = Muted)
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    items(dirs, key = { it.absolutePath }) { dir ->
                                        FolderRow("Folder: ${dir.name}") { onFolder(dir) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(sortedMedia, key = { it.path }) { item ->
                                        FolderRow(if (item.likelyUnsupported) "${item.name}  ·  May not play" else item.name) { onOpen(item, sortedMedia) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    if (dirs.isEmpty() && sortedMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 20.sp, color = Muted) }
                                }
                            }
                            else -> {
                                val base = VideoSorter.sort(if (mode == "Videos") videos else audio, sortOrder)
                                if (filter == LibraryFilter.PLAYLISTS) {
                                    val open = openPlaylist
                                    if (open == null) {
                                        items(playlistNames, key = { it }) { name ->
                                            val count = LibraryStores.playlistItems(context, name).size
                                            GlassTile("≡", name, "$count videos", modifier = Modifier.width(280.dp)) { openPlaylist = name }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        item {
                                            GlassButton("New playlist") {
                                                val name = LibraryStores.nextPlaylistName(context)
                                                LibraryStores.createPlaylist(context, name)
                                                playlistNames = LibraryStores.playlistNames(context)
                                                openPlaylist = name
                                            }
                                        }
                                        if (playlistNames.isEmpty()) item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("No playlists yet — tap New playlist, then add videos from their Details page.", fontSize = 20.sp, color = Muted)
                                        }
                                    } else {
                                        val inPlaylist = base.filter { LibraryStores.playlistItems(context, open).contains(it.path) }
                                        item {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                GlassButton("Back") { openPlaylist = null }
                                                GlassButton("Delete playlist") {
                                                    LibraryStores.deletePlaylist(context, open)
                                                    playlistNames = LibraryStores.playlistNames(context)
                                                    openPlaylist = null
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(open, fontSize = 28.sp, color = White)
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        mediaRows(inPlaylist, viewGrid, cols, resume, favourites,
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
                                    mediaRows(shown, viewGrid, cols, resume, favourites,
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

/** Grid or list rows for a media list, with resume progress and an
 * honest empty state. Shared by the filters and by open playlists. */
private fun LazyListScope.mediaRows(
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
        item { Text(emptyText, fontSize = 20.sp, color = Muted) }
    } else if (viewGrid) {
        items(shown.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item ->
                    val resumeMs = resume[item.path] ?: 0L
                    val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                    ThumbTile(item, tileSub(item), resumeFraction = fraction) { onOpen(item, shown) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    } else {
        items(shown, key = { it.path }) { item ->
            val resumeMs = resume[item.path] ?: 0L
            val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
            ListRow(item, fraction, favourites.contains(item.path), onToggleFav = { onToggleFav(item.path) }) { onOpen(item, shown) }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** A folder / file row: one glass surface, full width. */
@Composable
private fun FolderRow(text: String, onClick: () -> Unit) {
    GlassSurface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text, fontSize = 20.sp, color = White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(20.dp)
        )
    }
}

/** A media row: thumbnail + title + meta + a star to favourite. */
@Composable
private fun ListRow(
    item: VideoItem,
    resumeFraction: Float,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onOpen: () -> Unit
) {
    GlassSurface(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(96.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(140.dp).fillMaxHeight()) {
                if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(item.name, fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(tileSub(item) ?: "", fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassButton(if (isFav) "★" else "☆") { onToggleFav() }
        }
        if (resumeFraction > 0f) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                    .background(Color(0x44FFFFFF))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(4.dp)
                        .background(Primary)
                )
            }
        }
    }
}
