package com.playbox.tv

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun LibraryScreen(videos: List<VideoItem>, audio: List<VideoItem>, loading: Boolean, onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("Videos") }
    var folder by remember { mutableStateOf<File?>(null) }
    // SAF folders (Storage Access Framework): the reliable way to reach USB /
    // shared folders on Android 11+, where raw File access is blocked.
    var safFolder by remember { mutableStateOf<Uri?>(null) }
    var safFolders by remember { mutableStateOf(MediaLibrary.safFolders(context)) }
    var safPickerError by remember { mutableStateOf<String?>(null) }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            MediaLibrary.addSafFolder(context, uri)
            safFolders = MediaLibrary.safFolders(context)
            safFolder = uri
        }
    }
    val roots = remember { MediaLibrary.storageRoots() }
    BackHandler(enabled = folder != null || safFolder != null) {
        if (safFolder != null) {
            // Go to the parent of the picked folder, or back to the roots.
            val parent = runCatching {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(context, safFolder!!)?.parentFile?.uri
            }.getOrNull()
            safFolder = if (parent != null && safFolders.any { parent.toString().startsWith(it.toString()) }) parent else null
        } else {
            val parent = folder?.parentFile
            folder = if (parent != null && roots.any { parent.absolutePath.startsWith(it.absolutePath) }) parent else null
        }
    }
    val (dirs, media) = remember(folder) { val f = folder; if (f == null) emptyList<File>() to emptyList() else MediaLibrary.listFolder(f) }
    val (safDirs, safMedia) = remember(safFolder) { val u = safFolder; if (u == null) emptyList<Uri>() to emptyList() else MediaLibrary.listSafFolder(context, u) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        item {
            Text("Library", fontSize = 36.sp, color = White)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Videos", "Music", "Folders / USB").forEach { m -> TvButton(if (mode == m) "● $m" else m) { mode = m; folder = null; safFolder = null } }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        when (mode) {
            "Folders / USB" -> {
                if (folder == null && safFolder == null) {
                    items(roots) { root ->
                        Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { folder = root }.padding(20.dp)) {
                            Text(root.absolutePath, fontSize = 20.sp, color = White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(safFolders) { safUri ->
                        Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { safFolder = safUri }.padding(20.dp)) {
                            Text("Folder: ${MediaLibrary.safFolderName(context, safUri)}", fontSize = 20.sp, color = White)
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
                        TvButton("Back") { safFolder = null }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(currentSaf?.let { MediaLibrary.safFolderName(context, it) } ?: "", fontSize = 20.sp, color = Muted)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    items(safDirs) { dirUri ->
                        Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { safFolder = dirUri }.padding(20.dp)) {
                            Text("Folder: ${MediaLibrary.safFolderName(context, dirUri)}", fontSize = 20.sp, color = White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(safMedia) { item ->
                        Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onOpen(item) }.padding(20.dp)) {
                            Text(item.name, fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (safDirs.isEmpty() && safMedia.isEmpty()) item { Text("This folder is empty.", fontSize = 20.sp, color = Muted) }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        TvButton("Remove this folder") {
                            val removed = safFolder
                            safFolder = null
                            if (removed != null) {
                                MediaLibrary.removeSafFolder(context, removed)
                                safFolders = MediaLibrary.safFolders(context)
                            }
                        }
                    }
                } else {
                    item {
                        val currentFolder = folder
                        TvButton("Back") { folder = currentFolder?.parentFile?.takeIf { it.absolutePath != "/storage/emulated/0" } }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(currentFolder?.absolutePath ?: "", fontSize = 20.sp, color = Muted)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    items(dirs) { dir ->
                        Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { folder = dir }.padding(20.dp)) {
                            Text("Folder: ${dir.name}", fontSize = 20.sp, color = White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(media) { item ->
                        Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onOpen(item) }.padding(20.dp)) {
                            Text(item.name, fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (dirs.isEmpty() && media.isEmpty()) item { Text("This folder is empty.", fontSize = 20.sp, color = Muted) }
                }
            }
            else -> {
                val list = if (mode == "Videos") videos else audio
                if (list.isEmpty()) {
                    item { Text(if (loading) "Loading…" else "No ${mode.lowercase()} found yet.", fontSize = 20.sp, color = Muted) }
                } else {
                    items(list.chunked(3)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { item -> TvTile(item.name, if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null) { onOpen(item) } }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

