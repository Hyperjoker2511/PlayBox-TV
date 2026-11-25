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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- ZEPH Library (M3 redesign, from scratch) ----------
// A NEW Library, not a Nova/Qwen/Aura port. One M3 system: mode
// chips, sort + layout + refresh, filter chips, then the content —
// Videos / Music (List / Grid / Cinematic), Folders / USB (roots,
// SAF folders, folder contents), Network (the shared NetworkScreen),
// playlists and favourites. The data layer is unchanged
// (MediaLibrary, LibraryStores, VideoSorter) — only the design is new.

@Composable
internal fun M3LibraryScreen(
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
    onPlay: (VideoItem, List<VideoItem>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val sortPrefs = remember { context.getSharedPreferences("playbox_sort", Context.MODE_PRIVATE) }
    var sortOrder by remember {
        mutableStateOf(VideoSorter.fromName(sortPrefs.getString("order", "DATE_ADDED") ?: "DATE_ADDED"))
    }
    // Browse layout: List / Grid / Cinematic. Same prefs key as
    // before, so the user's existing choice carries over.
    var browseLayout by remember {
        mutableStateOf(
            sortPrefs.getString("browse_layout", null)
                ?: if (sortPrefs.getBoolean("view_grid", false)) "grid" else "list"
        )
    }
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
        Text(
            "Library",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Mode chips.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Videos", "Music", "Folders / USB", "Network").forEach { m ->
                ZephFilterChip(
                    label = m, selected = mode == m,
                    onClick = {
                        onMode(m); onFolder(null); onSafFolder(null)
                        filter = LibraryFilter.ALL; openPlaylist = null; cinematicFocus = null
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (mode != "Network") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZephTonalButton("Sort: ${VideoSorter.label(sortOrder)}", onClick = {
                    sortOrder = VideoSorter.next(sortOrder)
                    sortPrefs.edit().putString("order", sortOrder.name).apply()
                })
                ZephTonalButton("Layout: ${m3BrowseLayoutLabel(browseLayout)}", onClick = {
                    browseLayout = m3NextBrowseLayout(browseLayout)
                    sortPrefs.edit().putString("browse_layout", browseLayout).apply()
                })
                ZephOutlinedButton("Refresh", onClick = onRefresh)
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
                        ZephFilterChip(label, selected = filter == f, onClick = {
                            filter = f; openPlaylist = null; cinematicFocus = null
                        })
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
                    M3NetworkScreen(
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
                                        M3FolderRow(root.absolutePath) { onFolder(root) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(safFolders, key = { it.toString() }) { safUri ->
                                        M3FolderRow("Folder: ${safName(safUri)}") { onSafFolder(safUri) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    item {
                                        ZephButton("Add a folder", onClick = {
                                            safPickerError = null
                                            try {
                                                addFolderLauncher.launch(null)
                                            } catch (_: Exception) {
                                                safPickerError = "This TV has no folder picker. Use the storage rows above, or the Videos tab."
                                            }
                                        })
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "Pick a USB drive or folder once — Zeph remembers it, even after a restart.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        safPickerError?.let {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (roots.isEmpty() && safFolders.isEmpty()) {
                                        item {
                                            Text(
                                                "No storage found. Plug in a USB drive and open this screen again, or add a folder above.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else if (safFolder != null) {
                                    item {
                                        ZephTonalButton("Back", onClick = { navigateUp() })
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            safFolder?.let { safName(it) } ?: "",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    items(safDirs, key = { it.toString() }) { dirUri ->
                                        M3FolderRow("Folder: ${safName(dirUri)}") { onSafFolder(dirUri) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(sortedSafMedia, key = { it.path }) { item ->
                                        M3LibraryRow(item, resumeFraction = 0f, isFav = favourites.contains(item.path), onToggleFav = {
                                            LibraryStores.toggleFavourite(context, item.path)
                                            favourites = LibraryStores.favourites(context)
                                        }) { onOpen(item, sortedSafMedia) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    if (safDirs.isEmpty() && sortedSafMedia.isEmpty()) {
                                        item {
                                            Text(
                                                "This folder is empty.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        ZephOutlinedButton("Remove this folder", onClick = {
                                            val removed = safFolder
                                            onSafFolder(null)
                                            MediaLibrary.removeSafFolder(context, removed)
                                            safFolders = MediaLibrary.safFolders(context)
                                        })
                                    }
                                } else {
                                    item {
                                        ZephTonalButton("Back", onClick = { navigateUp() })
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            folder?.absolutePath ?: "",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    items(dirs, key = { it.absolutePath }) { dir ->
                                        M3FolderRow("Folder: ${dir.name}") { onFolder(dir) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    items(sortedMedia, key = { it.path }) { item ->
                                        M3LibraryRow(item, resumeFraction = 0f, isFav = favourites.contains(item.path), onToggleFav = {
                                            LibraryStores.toggleFavourite(context, item.path)
                                            favourites = LibraryStores.favourites(context)
                                        }) { onOpen(item, sortedMedia) }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    if (dirs.isEmpty() && sortedMedia.isEmpty()) {
                                        item {
                                            Text(
                                                "This folder is empty.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                val base = VideoSorter.sort(if (mode == "Videos") videos else audio, sortOrder)
                                if (filter == LibraryFilter.PLAYLISTS) {
                                    val open = openPlaylist
                                    if (open == null) {
                                        items(playlistNames, key = { it }) { name ->
                                            val count = LibraryStores.playlistItems(context, name).size
                                            M3FolderRow("$name  •  $count videos") { openPlaylist = name }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        item {
                                            ZephButton("New playlist", onClick = {
                                                val name = LibraryStores.nextPlaylistName(context)
                                                LibraryStores.createPlaylist(context, name)
                                                playlistNames = LibraryStores.playlistNames(context)
                                                openPlaylist = name
                                            })
                                        }
                                        if (playlistNames.isEmpty()) {
                                            item {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    "No playlists yet — tap New playlist, then add videos from their Details page.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        val inPlaylist = base.filter {
                                            LibraryStores.playlistItems(context, open).contains(it.path)
                                        }
                                        item {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                ZephTonalButton("Back", onClick = { openPlaylist = null })
                                                ZephOutlinedButton("Delete playlist", onClick = {
                                                    LibraryStores.deletePlaylist(context, open)
                                                    playlistNames = LibraryStores.playlistNames(context)
                                                    openPlaylist = null
                                                })
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                open,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        m3MediaRows(
                                            inPlaylist, browseLayout == "grid", cols, resume, favourites,
                                            onToggleFav = { path ->
                                                LibraryStores.toggleFavourite(context, path)
                                                favourites = LibraryStores.favourites(context)
                                            },
                                            onOpen = onOpen,
                                            emptyText = "This playlist is empty — add videos from their Details page."
                                        )
                                    }
                                } else {
                                    val shown = filterLibraryItems(base, filter, favourites, recent, resume.keys)
                                    val emptyText = when (filter) {
                                        LibraryFilter.ALL -> if (loading) "Loading…" else "No ${mode.lowercase()} found yet."
                                        LibraryFilter.RECENT -> "Nothing played yet — play a video and it shows up here."
                                        LibraryFilter.CONTINUE -> "No videos in progress."
                                        LibraryFilter.FAVOURITES -> "No favourites yet — tap the star on a list row, or mark one from a video's Details page."
                                        LibraryFilter.PLAYLISTS -> ""
                                    }
                                    if (browseLayout == "cinematic" && (mode == "Videos" || mode == "Music")) {
                                        m3CinematicRows(
                                            shown, cols, resume, favourites,
                                            onToggleFav = { path ->
                                                LibraryStores.toggleFavourite(context, path)
                                                favourites = LibraryStores.favourites(context)
                                            },
                                            onOpen = onOpen,
                                            onPlay = onPlay,
                                            emptyText = emptyText,
                                            focusedItem = cinematicFocus ?: shown.firstOrNull(),
                                            onFocusItem = { cinematicFocus = it }
                                        )
                                    } else {
                                        m3MediaRows(
                                            shown, browseLayout == "grid", cols, resume, favourites,
                                            onToggleFav = { path ->
                                                LibraryStores.toggleFavourite(context, path)
                                                favourites = LibraryStores.favourites(context)
                                            },
                                            onOpen = onOpen,
                                            emptyText = emptyText
                                        )
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

/** The three browse layouts. List is the default, Grid is posters
 * in rows, Cinematic is a hero above a poster grid. */
private fun m3BrowseLayoutLabel(layout: String): String = when (layout) {
    "grid" -> "Grid"
    "cinematic" -> "Cinematic"
    else -> "List"
}

private fun m3NextBrowseLayout(layout: String): String = when (layout) {
    "list" -> "grid"
    "grid" -> "cinematic"
    else -> "list"
}

/** A folder row: folder icon + name, in an M3 card. */
@Composable
private fun M3FolderRow(
    name: String,
    onClick: () -> Unit
) {
    ZephCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Folder, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A Library list row: cover tile, title + meta, resume bar, star. */
@Composable
private fun M3LibraryRow(
    item: VideoItem,
    resumeFraction: Float,
    isFav: Boolean = false,
    onToggleFav: () -> Unit = {},
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
                            Icons.Filled.MusicNote, contentDescription = null,
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
                    tileSub(item) ?: "",
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
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onToggleFav) {
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFav) "Remove from favourites" else "Add to favourites",
                    tint = if (isFav) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** A Library grid card: cover on top, title + meta below, resume bar. */
@Composable
private fun M3GridCard(
    item: VideoItem,
    resumeFraction: Float = 0f,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    ZephCard(
        modifier = modifier,
        onClick = onOpen
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
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
                            Icons.Filled.MusicNote, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                tileSub(item) ?: "",
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

/** Grid or list rows for a media list, M3 style. */
private fun LazyListScope.m3MediaRows(
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
        item {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (viewGrid) {
        items(shown.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item ->
                    val resumeMs = resume[item.path] ?: 0L
                    val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                    M3GridCard(item, resumeFraction = fraction, modifier = Modifier.weight(1f)) { onOpen(item, shown) }
                }
                repeat((cols - row.size).coerceAtLeast(0)) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    } else {
        items(shown, key = { it.path }) { item ->
            val resumeMs = resume[item.path] ?: 0L
            val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
            M3LibraryRow(
                item, resumeFraction = fraction,
                isFav = favourites.contains(item.path),
                onToggleFav = { onToggleFav(item.path) }
            ) { onOpen(item, shown) }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** Cinematic browse: the focused video's hero above a poster grid.
 * Focusing a poster updates the hero. */
private fun LazyListScope.m3CinematicRows(
    shown: List<VideoItem>,
    cols: Int,
    resume: Map<String, Long>,
    favourites: Set<String>,
    onToggleFav: (String) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlay: (VideoItem, List<VideoItem>) -> Unit,
    emptyText: String,
    focusedItem: VideoItem?,
    onFocusItem: (VideoItem) -> Unit
) {
    if (shown.isEmpty()) {
        item {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val hero = if (focusedItem != null && shown.any { it.path == focusedItem.path }) focusedItem else shown.first()
    item {
        M3CinematicHero(
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
                M3GridCard(
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

/** The cinematic hero: the video's cover fills the hero, with a
 * dark fade at the bottom for the title, meta and actions. */
@Composable
private fun M3CinematicHero(
    item: VideoItem,
    resumeMs: Long,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    ZephCard(
        modifier = Modifier.fillMaxWidth().height(300.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.isVideo) {
                VideoThumb(item, Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000)),
                        startY = 0.35f
                    )
                )
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tileSub(item) ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZephButton(
                        if (resumeMs > 0) "Resume" else "Play",
                        onClick = onPlay,
                        icon = Icons.Filled.PlayArrow
                    )
                    ZephTonalButton("Details", onClick = onDetails)
                }
            }
        }
    }
}
