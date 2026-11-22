package com.playbox.tv

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    // Batch 2: sort order, remembered in prefs.
    val sortPrefs = remember { context.getSharedPreferences("playbox_sort", Context.MODE_PRIVATE) }
    var sortOrder by remember { mutableStateOf(VideoSorter.fromName(sortPrefs.getString("order", "DATE_ADDED") ?: "DATE_ADDED")) }
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

    // Header stays fixed at the top now (Batch 2: the Network screen is
    // its own scrolling list, and a LazyColumn cannot nest inside one).
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text("Library", fontSize = 36.sp, color = White)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Videos", "Music", "Folders / USB", "Network").forEach { m ->
                TvButton(if (mode == m) "● $m" else m) { onMode(m); onFolder(null); onSafFolder(null) }
            }
            // B8: reload the MediaStore lists on demand.
            TvButton("Refresh") { onRefresh() }
            // Batch 2: sort order for the lists below.
            TvButton("Sort: ${VideoSorter.label(sortOrder)}") {
                sortOrder = VideoSorter.next(sortOrder)
                sortPrefs.edit().putString("order", sortOrder.name).apply()
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        when (mode) {
            "Network" -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    NetworkScreen(
                        server = networkServer, onServer = onNetworkServer,
                        path = networkPath, onPath = onNetworkPath,
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
                            Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onFolder(root) }.padding(20.dp)) {
                                Text(root.absolutePath, fontSize = 20.sp, color = White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        items(safFolders, key = { it.toString() }) { safUri ->
                            Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onSafFolder(safUri) }.padding(20.dp)) {
                                Text("Folder: ${safName(safUri)}", fontSize = 20.sp, color = White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            TvButton("Add a folder") {
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
                            val currentSaf = safFolder
                            TvButton("Back") { navigateUp() }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(currentSaf?.let { safName(it) } ?: "", fontSize = 20.sp, color = Muted)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        items(safDirs, key = { it.toString() }) { dirUri ->
                            Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onSafFolder(dirUri) }.padding(20.dp)) {
                                Text("Folder: ${safName(dirUri)}", fontSize = 20.sp, color = White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        items(sortedSafMedia, key = { it.path }) { item ->
                            Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onOpen(item, sortedSafMedia) }.padding(20.dp)) {
                                Text(if (item.likelyUnsupported) "${item.name}  ·  May not play" else item.name, fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (safDirs.isEmpty() && sortedSafMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 20.sp, color = Muted) }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            TvButton("Remove this folder") {
                                val removed = safFolder
                                onSafFolder(null)
                                // Non-null here: this branch only runs when safFolder != null.
                                MediaLibrary.removeSafFolder(context, removed)
                                safFolders = MediaLibrary.safFolders(context)
                            }
                        }
                    } else {
                        item {
                            val currentFolder = folder
                            TvButton("Back") { navigateUp() }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(currentFolder?.absolutePath ?: "", fontSize = 20.sp, color = Muted)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        items(dirs, key = { it.absolutePath }) { dir ->
                            Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onFolder(dir) }.padding(20.dp)) {
                                Text("Folder: ${dir.name}", fontSize = 20.sp, color = White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        items(sortedMedia, key = { it.path }) { item ->
                            Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onOpen(item, sortedMedia) }.padding(20.dp)) {
                                Text(if (item.likelyUnsupported) "${item.name}  ·  May not play" else item.name, fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (dirs.isEmpty() && sortedMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 20.sp, color = Muted) }
                    }
                }
                else -> {
                    val list = VideoSorter.sort(if (mode == "Videos") videos else audio, sortOrder)
                    if (list.isEmpty()) {
                        item { Text(if (loading) "Loading…" else "No ${mode.lowercase()} found yet.", fontSize = 20.sp, color = Muted) }
                    } else {
                        items(list.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { item -> ThumbTile(item, tileSub(item)) { onOpen(item, list) } }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
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
