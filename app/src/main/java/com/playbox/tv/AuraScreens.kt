package com.playbox.tv

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- AURA Search (2026-11-23, 1.4.5 Patch 2) ----------
// The clean Aura file's search, rebuilt as PlayBox: a full-width
// field at the top, recent-search chips while it is empty, and
// the results as Aura cards underneath. Aura's chips are pills;
// the field has a 3dp lavender border on focus.

@Composable
internal fun AuraSearchScreen(
    items: List<VideoItem>,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var recent by remember { mutableStateOf(recentSearches(context)) }
    var fieldFocused by remember { mutableStateOf(false) }
    val shown = searchFilter(items, search)
    val resume = remember { resumePositions(context) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Search", fontSize = 28.sp, color = AuraOnSurface, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .background(AuraSurface3, RoundedCornerShape(18.dp))
                    .border(if (fieldFocused) 3.dp else 1.dp, if (fieldFocused) AuraPrimary else AuraOutlineVariant, RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", fontSize = 24.sp, color = AuraPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = search,
                        onValueChange = onSearch,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 20.sp, color = AuraOnSurface),
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
                                if (search.isEmpty()) Text("Search your videos and music…", fontSize = 20.sp, color = AuraOnSurfaceVariant)
                                inner()
                            }
                        }
                    )
                    if (search.isNotEmpty()) {
                        Text(
                            "×", fontSize = 26.sp, color = AuraOnSurfaceVariant,
                            modifier = Modifier.clickable { onSearch("") }.padding(8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (search.isBlank() && recent.isNotEmpty()) {
            item {
                Text("RECENT", fontSize = 13.sp, color = AuraOnSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it }) { q ->
                        AuraChip(q, active = false) { onSearch(q) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        if (search.isNotBlank()) {
            item {
                AuraSectionHeader("Results")
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (shown.isEmpty()) {
                item { Text("Nothing matches \"$search\".", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(shown, key = { it.path }) { item ->
                            val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                            AuraCard(item = item, resumeFraction = fraction, poster = false) { onOpen(item, shown) }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        } else {
            item {
                Text("Type to search your videos and music.", fontSize = 16.sp, color = AuraOnSurfaceVariant)
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ---------- AURA Explore (2026-11-23, 1.4.5 Patch 2) ----------
// Aura's fifth rail item. A browse screen over the real library:
// Trending (the most-resumed), Recently added, and All videos —
// each a horizontal row of Aura cards. No streaming catalogue;
// everything here is a local file.

@Composable
internal fun AuraExploreScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    onOpen: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val resumeTime = remember { resumeTimes(context) }
    val all = videos + audio
    val trending = videos.filter { resume.containsKey(it.path) }
        .sortedByDescending { resumeTime[it.path] ?: 0L }.take(10)
    val recent = all.sortedByDescending { it.dateAddedSec }.take(12)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Explore", fontSize = 28.sp, color = AuraOnSurface, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Browse your library — trending, recent, and everything.", fontSize = 16.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (trending.isNotEmpty()) {
            item {
                AuraSectionHeader("Trending in your library")
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(trending, key = { it.path }) { item ->
                        val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                        AuraCard(item = item, resumeFraction = fraction, poster = false) { onOpen(item, all) }
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
        if (recent.isNotEmpty()) {
            item {
                AuraSectionHeader("Recently added")
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(recent, key = { it.path }) { item ->
                        val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                        AuraCard(item = item, resumeFraction = fraction, poster = true) { onOpen(item, all) }
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
        item {
            AuraSectionHeader("All videos", onSeeAll = null)
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (all.isEmpty()) {
            item { Text("Nothing here yet. Your local and USB files will appear here.", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(all, key = { it.path }) { item ->
                        val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                        AuraCard(item = item, resumeFraction = fraction, poster = false) { onOpen(item, all) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        item { Spacer(modifier = Modifier.height(56.dp)) }
    }
}

// ---------- AURA Details (2026-11-23, 1.4.5 Patch 2) ----------
// The clean Aura file's detail overlay, rebuilt as PlayBox: a
// backdrop with a scrim, a 48sp title, meta, the resume line,
// Play Now + Download actions, and a "More Like This" row of
// Aura cards. Same signature as NovaDetailsScreen so the
// MainActivity wiring is identical.

@Composable
internal fun AuraDetailsScreen(
    item: VideoItem,
    list: List<VideoItem>,
    onBack: () -> Unit,
    onPlay: (VideoItem, Boolean) -> Unit,
    onQueue: (VideoItem) -> Unit,
    onOpen: (VideoItem) -> Unit
) {
    androidx.activity.compose.BackHandler { onBack() }
    val context = LocalContext.current
    val saved = remember { savedResumePosition(context, item.path) }
    val resumeFraction = if (item.durationMs > 0) saved.toFloat() / item.durationMs else 0f
    var isFav by remember { mutableStateOf(LibraryStores.isFavourite(context, item.path)) }
    var addedNote by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().background(AuraSurface)) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(320.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(AuraPosterGradStart, AuraPosterGradEnd)
                        )
                    )
            ) {
                VideoThumb(item, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    AuraSurface
                                )
                            )
                        )
                )
                AuraButton("← Back", modifier = Modifier.padding(24.dp).align(Alignment.TopStart), onClick = onBack)
            }
        }
        item {
            androidx.compose.foundation.layout.Column(modifier = Modifier.padding(horizontal = 48.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    item.name, fontSize = 48.sp, color = AuraOnSurface,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                val sub = tileSub(item)
                if (sub != null) Text(sub, fontSize = 14.sp, color = AuraOnSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(containerOf(item), fontSize = 16.sp, color = AuraOnSurfaceVariant)
                if (saved > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Resume from ${TimeFormatter.format(saved)}", fontSize = 16.sp, color = AuraPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(0.5f).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(androidx.compose.ui.graphics.Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AuraPrimary)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuraButton(
                        if (saved > 0) "▶ Resume" else "▶ Play Now",
                        filled = true, onClick = { onPlay(item, false) }
                    )
                    AuraButton("↻ From start", onClick = { onPlay(item, true) })
                    AuraButton(if (isFav) "♥ Saved" else "♡ Save", onClick = {
                        isFav = LibraryStores.toggleFavourite(context, item.path)
                    })
                    AuraButton("+ Queue", onClick = { onQueue(item); addedNote = "Added to the queue." })
                }
                addedNote?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, fontSize = 15.sp, color = AuraOnSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(36.dp))
                AuraSectionHeader("More Like This")
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        item {
            val more = list.filter { it.path != item.path }.take(10)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                items(more, key = { it.path }) { other ->
                    val fraction = if (other.durationMs > 0) (remember { resumePositions(context) }[other.path] ?: 0L).toFloat() / other.durationMs else 0f
                    AuraCard(item = other, resumeFraction = fraction, poster = false) { onOpen(other) }
                }
            }
            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

// ---------- AURA Library (2026-11-23, 1.4.5 Patch 2) ----------
// The library as Aura rows: mode chips (Videos / Music /
// Folders / USB / Network), sort + refresh, filter chips
// (All / Recently played / Continue / Favourites / Playlists),
// and the files as a vertical list of Aura rows. Folders and
// Network reuse the same data as the Nova library (storage
// roots, SAF folders, the shared NetworkScreen) — only the
// look is Aura. Same signature as NovaLibraryScreen so the
// MainActivity wiring is identical.

@Composable
internal fun AuraLibraryScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    mode: String,
    onMode: (String) -> Unit,
    folder: java.io.File?,
    onFolder: (java.io.File?) -> Unit,
    safFolder: android.net.Uri?,
    onSafFolder: (android.net.Uri?) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    networkServer: SmbServer?,
    onNetworkServer: (SmbServer?) -> Unit,
    networkPath: String?,
    onNetworkPath: (String?) -> Unit,
    ftpServer: FtpServer?,
    onFtpServer: (FtpServer?) -> Unit,
    ftpPath: String?,
    onFtpPath: (String?) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val sortPrefs = remember { context.getSharedPreferences("playbox_sort", Context.MODE_PRIVATE) }
    var sortOrder by remember { mutableStateOf(VideoSorter.fromName(sortPrefs.getString("order", "DATE_ADDED") ?: "DATE_ADDED")) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var favourites by remember { mutableStateOf(LibraryStores.favourites(context)) }
    var playlistNames by remember { mutableStateOf(LibraryStores.playlistNames(context)) }
    val recent = remember { LibraryStores.recentPaths(context) }
    val resume = remember { resumePositions(context) }
    var safFolders by remember { mutableStateOf(MediaLibrary.safFolders(context)) }
    var safPickerError by remember { mutableStateOf<String?>(null) }
    val addFolderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            MediaLibrary.addSafFolder(context, uri)
            safFolders = MediaLibrary.safFolders(context)
            onSafFolder(uri)
        }
    }
    val roots = remember { MediaLibrary.storageRoots() }
    var folderContents by remember { mutableStateOf<Pair<List<java.io.File>, List<VideoItem>>>(emptyList<java.io.File>() to emptyList()) }
    androidx.compose.runtime.LaunchedEffect(folder) {
        folderContents = emptyList<java.io.File>() to emptyList()
        val f = folder
        if (f != null) folderContents = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MediaLibrary.listFolder(f) }
    }
    var safContents by remember { mutableStateOf<Pair<List<android.net.Uri>, List<VideoItem>>>(emptyList<android.net.Uri>() to emptyList()) }
    androidx.compose.runtime.LaunchedEffect(safFolder) {
        safContents = emptyList<android.net.Uri>() to emptyList()
        val u = safFolder
        if (u != null) safContents = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MediaLibrary.listSafFolder(context, u) }
    }
    val (dirs, media) = folderContents
    val (safDirs, safMedia) = safContents
    val sortedMedia = remember(media, sortOrder) { VideoSorter.sort(media, sortOrder) }
    val sortedSafMedia = remember(safMedia, sortOrder) { VideoSorter.sort(safMedia, sortOrder) }
    var safNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    androidx.compose.runtime.LaunchedEffect(safFolders, safDirs) {
        val uris = (safFolders + safDirs).distinct()
        safNames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            uris.associate { it.toString() to MediaLibrary.safFolderName(context, it) }
        }
    }
    fun safName(uri: android.net.Uri): String = safNames[uri.toString()] ?: "Folder"
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
    androidx.activity.compose.BackHandler(enabled = folder != null || safFolder != null) { navigateUp() }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Library", fontSize = 28.sp, color = AuraOnSurface, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Videos", "Music", "Folders / USB", "Network").forEach { m ->
                item {
                    AuraChip(m, active = mode == m) {
                        onMode(m); onFolder(null); onSafFolder(null)
                        filter = LibraryFilter.ALL
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (mode != "Network") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AuraButton("Sort: ${VideoSorter.label(sortOrder)}", onClick = {
                    sortOrder = VideoSorter.next(sortOrder)
                    sortPrefs.edit().putString("order", sortOrder.name).apply()
                }, modifier = Modifier.height(44.dp))
                AuraButton("Refresh", onClick = onRefresh, modifier = Modifier.height(44.dp))
            }
            if (mode == "Videos" || mode == "Music") {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        LibraryFilter.ALL to "All",
                        LibraryFilter.RECENT to "Recently played",
                        LibraryFilter.CONTINUE to "Continue watching",
                        LibraryFilter.FAVOURITES to "Favourites",
                        LibraryFilter.PLAYLISTS to "Playlists"
                    ).forEach { (f, label) ->
                        item { AuraChip(label, active = filter == f) { filter = f } }
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
            "Folders / USB" -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (folder == null && safFolder == null) {
                            items(roots, key = { it.absolutePath }) { root ->
                                AuraFolderRow(root.absolutePath, icon = "▤") { onFolder(root) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            items(safFolders, key = { it.toString() }) { safUri ->
                                AuraFolderRow("Folder: ${safName(safUri)}", icon = "▤") { onSafFolder(safUri) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            item {
                                AuraButton("Add a folder") {
                                    safPickerError = null
                                    try { addFolderLauncher.launch(null) } catch (_: Exception) {
                                        safPickerError = "This TV has no folder picker. Use the storage rows above, or the Videos tab."
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Pick a USB drive or folder once — PlayBox remembers it, even after a restart.", fontSize = 16.sp, color = AuraOnSurfaceVariant)
                                safPickerError?.let { Spacer(modifier = Modifier.height(12.dp)); Text(it, fontSize = 16.sp, color = AuraOnSurfaceVariant) }
                            }
                            if (roots.isEmpty() && safFolders.isEmpty()) item { Text("No storage found. Plug in a USB drive and open this screen again, or add a folder above.", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
                        } else if (safFolder != null) {
                            item {
                                AuraButton("Back", onClick = { navigateUp() }, modifier = Modifier.height(44.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(safFolder?.let { safName(it) } ?: "", fontSize = 18.sp, color = AuraOnSurfaceVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            items(safDirs, key = { it.toString() }) { dirUri ->
                                AuraFolderRow("Folder: ${safName(dirUri)}", icon = "▤") { onSafFolder(dirUri) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            items(sortedSafMedia, key = { it.path }) { item ->
                                AuraListRow(item, resumeFraction = 0f) { onOpen(item, sortedSafMedia) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (safDirs.isEmpty() && sortedSafMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                AuraButton("Remove this folder") {
                                    val removed = safFolder
                                    onSafFolder(null)
                                    MediaLibrary.removeSafFolder(context, removed)
                                    safFolders = MediaLibrary.safFolders(context)
                                }
                            }
                        } else {
                            item {
                                AuraButton("Back", onClick = { navigateUp() }, modifier = Modifier.height(44.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(folder?.absolutePath ?: "", fontSize = 18.sp, color = AuraOnSurfaceVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            items(dirs, key = { it.absolutePath }) { dir ->
                                AuraFolderRow(dir.name, icon = "▤") { onFolder(dir) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            items(sortedMedia, key = { it.path }) { item ->
                                val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                                AuraListRow(item, resumeFraction = fraction) { onOpen(item, sortedMedia) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (dirs.isEmpty() && sortedMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
                        }
                    }
                }
            }
            else -> {
                val base = if (mode == "Videos") videos else audio
                val shown = filterLibraryItems(
                    VideoSorter.sort(base, sortOrder), filter, favourites, recent, resume.keys
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (loading) {
                            item { Text("Loading your files…", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
                        } else if (shown.isEmpty()) {
                            item { Text("Nothing here yet.", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
                        } else {
                            items(shown, key = { it.path }) { item ->
                                val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                                AuraListRow(item, resumeFraction = fraction) { onOpen(item, shown) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// A folder row: an icon, the path, and a chevron. Aura surface3
// fill, 1dp outline, 20dp corners, 3dp lavender border on focus.
@Composable
private fun AuraFolderRow(text: String, icon: String, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (focused) 1.02f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 400f), label = "auraFolderScale"
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(AuraSurface3)
            .border(if (focused) 3.dp else 1.dp, if (focused) AuraPrimary else AuraOutlineVariant, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 20.sp, color = AuraPrimary)
        Text(text, fontSize = 16.sp, color = AuraOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("›", fontSize = 20.sp, color = AuraOnSurfaceVariant)
    }
}

// A list row: a small thumbnail, the title, the subtitle, and the
// resume bar. Aura surface3 fill, 20dp corners, 3dp lavender
// border on focus.
@Composable
private fun AuraListRow(item: VideoItem, resumeFraction: Float, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (focused) 1.02f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 400f), label = "auraRowScale"
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(AuraSurface3)
            .border(if (focused) 3.dp else 1.dp, if (focused) AuraPrimary else AuraOutlineVariant, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.width(112.dp).height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(AuraPosterGradStart, AuraPosterGradEnd)
                    )
                )
        ) {
            VideoThumb(item, modifier = Modifier.fillMaxSize())
        }
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontSize = 16.sp, color = AuraOnSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = tileSub(item)
            if (sub != null) Text(sub, fontSize = 13.sp, color = AuraOnSurfaceVariant, maxLines = 1)
            if (resumeFraction > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(androidx.compose.ui.graphics.Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(resumeFraction.coerceIn(0f, 1f)).height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AuraPrimary)
                    )
                }
            }
        }
    }
}

// ---------- AURA Settings (2026-11-23, 1.4.5 Patch 2) ----------
// The real PlayBox settings, in Aura's look: Playback, Subtitles,
// Audio, Network, Appearance (App design: Nova / Qwen / Aura —
// Aura is the third option), Surface, Player UI, About. The
// prefs are the same ones the Nova and Qwen settings read, so
// switching designs never loses a setting.

@Composable
internal fun AuraSettingsScreen(
    currentDesign: AppDesign,
    onDesignChange: (AppDesign) -> Unit,
    railMode: RailMode = RailMode.DOCKED,
    onRailModeChange: (RailMode) -> Unit = {},
    labelMode: RailLabelMode = RailLabelMode.FOCUSED,
    onLabelModeChange: (RailLabelMode) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    var skip by remember { mutableStateOf(prefs.getInt("skip_seconds", 10)) }
    var speed by remember { mutableStateOf(prefs.getFloat("default_speed", 1f)) }
    var subsOn by remember { mutableStateOf(prefs.getBoolean("subtitles_on", true)) }
    var resumeOn by remember { mutableStateOf(isResumeOn(context)) }
    var subSize by remember { mutableStateOf(subStyle(context).sizeSp) }
    var subColor by remember { mutableStateOf(context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE).getString("color", "white") ?: "white") }
    var subBg by remember { mutableStateOf(context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE).getString("background", "black") ?: "black") }
    var eqOn by remember { mutableStateOf(prefs.getBoolean("eq_on", false)) }
    var loudness by remember { mutableStateOf(prefs.getInt("loudness_pct", 0)) }
    var smbServers by remember { mutableStateOf(SmbNetwork.servers(context)) }
    var reduceMotion by remember { mutableStateOf(isReduceMotion(context)) }
    var uiStyle by remember { mutableStateOf(playerUiStyle(context)) }
    var defaultEngine by remember { mutableStateOf(defaultEnginePref(context)) }
    var surface by remember { mutableStateOf(surfaceStyle(context)) }

    fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Settings", fontSize = 28.sp, color = AuraOnSurface, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            AuraSectionHeader("Playback")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Default speed", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    item { AuraChip("${s}x", active = speed == s) { speed = s; save { putFloat("default_speed", s) } } }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("New videos start at this speed.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Skip", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 30).forEach { s ->
                    item { AuraChip("${s}s", active = skip == s) { skip = s; save { putInt("skip_seconds", s) } } }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("How far −10s / +10s and the remote FF/RW keys jump.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            AuraButton(if (subsOn) "Subtitles: On" else "Subtitles: Off", onClick = { subsOn = !subsOn; save { putBoolean("subtitles_on", subsOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("Subtitles are on by default for new videos.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            AuraButton(if (resumeOn) "Resume: On" else "Resume: Off", onClick = { resumeOn = !resumeOn; save { putBoolean("resume_on", resumeOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("When off, every video starts from the beginning.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Player", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { AuraChip("Media3", active = defaultEngine == PlayerEngine.MEDIA3) { defaultEngine = PlayerEngine.MEDIA3; save { putString("default_engine", "media3") } } }
                item { AuraChip("VLC", active = defaultEngine == PlayerEngine.VLC) { defaultEngine = PlayerEngine.VLC; save { putString("default_engine", "vlc") } } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Media3 plays the common formats. VLC takes over on its own for FTP/SFTP/NFS files and formats Media3 cannot play — and any file can be given its own player on its Details page.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            AuraSectionHeader("Subtitles")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Size", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("S" to 18f, "M" to 24f, "L" to 32f, "XL" to 40f).forEach { (label, size) ->
                    item {
                        AuraChip(label, active = subSize == size) {
                            subSize = size
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putFloat("size_sp", size).apply()
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Colour", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("white", "yellow", "cyan", "green").forEach { c ->
                    item {
                        AuraChip(c.replaceFirstChar { it.uppercase() }, active = subColor == c) {
                            subColor = c
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putString("color", c).apply()
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Background", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("black", "none").forEach { b ->
                    item {
                        AuraChip(b.replaceFirstChar { it.uppercase() }, active = subBg == b) {
                            subBg = b
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putString("background", b).apply()
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("These are the same subtitle settings as in the player's gear.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            AuraSectionHeader("Audio")
            Spacer(modifier = Modifier.height(12.dp))
            AuraButton(if (eqOn) "Equalizer: On" else "Equalizer: Off", onClick = { eqOn = !eqOn; save { putBoolean("eq_on", eqOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("The bands and presets live in the player's gear.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Loudness", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AuraButton("−", onClick = { loudness = (loudness - 10).coerceAtLeast(0); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
                Text("$loudness%", fontSize = 18.sp, color = AuraOnSurface, modifier = Modifier.width(64.dp))
                AuraButton("+", onClick = { loudness = (loudness + 10).coerceAtMost(100); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Boosts quiet audio. 0% is off — this is the night-mode control.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Equalizer and loudness apply to the next video you play.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            AuraSectionHeader("Network")
            Spacer(modifier = Modifier.height(12.dp))
            if (smbServers.isEmpty()) {
                Text("No servers yet — add one from Library → Network.", fontSize = 16.sp, color = AuraOnSurfaceVariant)
            } else {
                smbServers.forEach { server ->
                    Text(formatSmbServer(server), fontSize = 16.sp, color = AuraOnSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Add more servers from Library → Network.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Play from URL", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("In Library → Network. HTTP, HTTPS, HLS, DASH, RTSP, RTMP and NFS links.", fontSize = 16.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            AuraSectionHeader("Appearance")
            Spacer(modifier = Modifier.height(12.dp))
            Text("App design", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { AuraChip("Nova", active = currentDesign == AppDesign.NOVA) { onDesignChange(AppDesign.NOVA) } }
                item { AuraChip("Qwen", active = currentDesign == AppDesign.QWEN) { onDesignChange(AppDesign.QWEN) } }
                item { AuraChip("Aura", active = currentDesign == AppDesign.AURA) { onDesignChange(AppDesign.AURA) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Nova is the from-scratch design — a left rail, lists, flat solid surfaces. Qwen is the earlier glass design. Aura is the Material 3 lavender design, your favourite. Switching takes effect immediately.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Surface", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { AuraChip("Solid", active = surface == SurfaceStyle.SOLID) { surface = SurfaceStyle.SOLID; saveSurfaceStyle(context, SurfaceStyle.SOLID) } }
                item { AuraChip("Liquid", active = surface == SurfaceStyle.LIQUID) { surface = SurfaceStyle.LIQUID; saveSurfaceStyle(context, SurfaceStyle.LIQUID) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Solid is flat with no blur — the safe default for the 2 GB box. Liquid is a light glass look (translucent surfaces, no heavy blur). If Liquid ever lags, switch back to Solid.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Navigation rail", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { AuraChip("Docked", active = railMode == RailMode.DOCKED) { onRailModeChange(RailMode.DOCKED) } }
                item { AuraChip("Floating", active = railMode == RailMode.FLOATING) { onRailModeChange(RailMode.FLOATING) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Docked is always there and the content sits beside it. Floating is a capsule over the content — press ◀ to open it, it hides a few seconds after you leave it. Applies on wide screens (TV, landscape); narrow screens keep the top tabs.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Menu labels", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { AuraChip("Off", active = labelMode == RailLabelMode.OFF) { onLabelModeChange(RailLabelMode.OFF) } }
                item { AuraChip("When focused", active = labelMode == RailLabelMode.FOCUSED) { onLabelModeChange(RailLabelMode.FOCUSED) } }
                item { AuraChip("Always", active = labelMode == RailLabelMode.ALWAYS) { onLabelModeChange(RailLabelMode.ALWAYS) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Off shows icons only. When focused shows the label on the item you are on. Always shows every label and keeps the rail expanded.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Player UI", fontSize = 18.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    PlayerUiStyle.NOVA to "Nova",
                    PlayerUiStyle.LUMEN to "Lumen",
                    PlayerUiStyle.LIQUID_GLASS to "Liquid Glass",
                    PlayerUiStyle.PRISM to "Prism",
                    PlayerUiStyle.AURA to "Aura"
                ).forEach { (style, label) ->
                    item {
                        AuraChip(label, active = uiStyle == style) {
                            uiStyle = style; savePlayerUiStyle(context, style)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Five player looks, same player underneath. Aura is the lavender bottom UI with a symmetric transport.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            AuraButton(if (reduceMotion) "Reduce motion: On" else "Reduce motion: Off", onClick = {
                reduceMotion = !reduceMotion
                context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
                    .edit().putBoolean("reduce_motion", reduceMotion).apply()
            })
            Spacer(modifier = Modifier.height(8.dp))
            Text("Turns off the focus scale and the hero motion.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            AuraSectionHeader("About")
            Spacer(modifier = Modifier.height(12.dp))
            Text("PlayBox TV 1.4.6 — Patch 2: Aura, plus VLC Patches B, C and D (HDR).", fontSize = 16.sp, color = AuraOnSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Local and USB video and audio. Seek, fast-forward, rewind, speed, subtitles and audio tracks.", fontSize = 16.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Player: Media3 / ExoPlayer 1.6.1 and LibVLC. DLNA cast from your phone. SMB / NAS folders.", fontSize = 16.sp, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}
