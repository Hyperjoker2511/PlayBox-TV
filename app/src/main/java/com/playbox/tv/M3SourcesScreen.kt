package com.playbox.tv

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- ZEPH Sources (v7, 1.9.1) ----------
// How content ARRIVES: device folders, USB, SMB/FTP/SFTP and DLNA.
// One screen, everything that can hold a video — the design's
// sections ("On this device" / "USB" / "Network"), every row a
// 44dp icon circle + real data (path + video count, size +
// Connected, server URL + status). "Add a source" opens the
// network-server form (Library → Network); the folder picker
// lives with the Picked folders. DLNA stays honest: Zeph is a
// DLNA RENDERER (it receives casts from the phone) — it does not
// browse DLNA servers yet, so the row says so instead of faking
// a browser (decision 1). Focus starts on the SMB row.

@Composable
internal fun M3SourcesScreen(
    videos: List<VideoItem>,
    onOpenFolder: (File) -> Unit,
    onOpenSafFolder: (Uri) -> Unit,
    onOpenSmbServer: (SmbServer) -> Unit,
    onOpenFtpServer: (FtpServer) -> Unit,
    onAddNetworkServer: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var scanNonce by remember { mutableStateOf(0) }
    // Re-read on scan (and on first show): saved servers + roots.
    val roots = remember(scanNonce) { MediaLibrary.storageRoots() }
    val smbServers = remember(scanNonce) { SmbNetwork.servers(context) }
    val ftpServers = remember(scanNonce) { FtpNetwork.servers(context) }
    var safFolders by remember(scanNonce) { mutableStateOf(MediaLibrary.safFolders(context)) }
    var safNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(safFolders) {
        val uris = safFolders.distinct()
        safNames = withContext(Dispatchers.IO) {
            uris.associate { it.toString() to MediaLibrary.safFolderName(context, it) }
        }
    }
    fun safName(uri: Uri): String = safNames[uri.toString()] ?: "Folder"
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            MediaLibrary.addSafFolder(context, uri)
            safFolders = MediaLibrary.safFolders(context)
            onOpenSafFolder(uri)
        }
    }
    var pickerError by remember { mutableStateOf<String?>(null) }
    // So13: focus starts on the SMB row (or the first row, when
    // there is no SMB server yet).
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRowFocus.requestFocus() }

    fun videosUnder(prefix: String): Int =
        videos.count { it.path.startsWith(prefix) }
    fun countSuffix(n: Int): String = if (n > 0) " · $n videos" else ""
    val internalRoot = roots.firstOrNull { it.absolutePath == "/storage/emulated/0" }
    val usbRoots = roots.filter { it.absolutePath != "/storage/emulated/0" }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            ZephScreenTopBar("Sources")
            Spacer(modifier = Modifier.height(24.dp))
        }
        // On this device: internal storage + Downloads.
        item {
            M3SourcesHeader("On this device")
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (internalRoot != null) {
            item {
                M3SourceRow(
                    icon = Icons.Filled.Folder,
                    title = "Internal storage",
                    subtitle = "/storage/emulated/0" + countSuffix(videosUnder("/storage/emulated/0")),
                    onClick = { onOpenFolder(internalRoot) },
                    focusRequester = if (smbServers.isEmpty()) firstRowFocus else null
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        item {
            M3SourceRow(
                icon = Icons.Filled.Download,
                title = "Downloads",
                subtitle = "/storage/emulated/0/Download" + countSuffix(videosUnder("/storage/emulated/0/Download")),
                onClick = {
                    val downloads = File("/storage/emulated/0/Download")
                    if (downloads.isDirectory) onOpenFolder(downloads)
                    else onOpenFolder(File("/storage/emulated/0"))
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        // USB: its own section (So3), with size + Connected + count.
        if (usbRoots.isNotEmpty()) {
            item {
                M3SourcesHeader("USB")
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(usbRoots.size) { i ->
                val root = usbRoots[i]
                val size = root.totalSpace
                val sizePart = if (size > 0) formatSize(size) + " · " else ""
                M3SourceRow(
                    icon = Icons.Filled.Usb,
                    title = root.name,
                    subtitle = sizePart + "Connected" + countSuffix(videosUnder(root.absolutePath)),
                    onClick = { onOpenFolder(root) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (roots.isEmpty()) {
            item {
                Text(
                    "No storage found yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        // Picked folders (So14, kept): SAF folders the user picked.
        if (safFolders.isNotEmpty()) {
            item {
                M3SourcesHeader("Picked folders")
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(safFolders.size) { i ->
                val uri = safFolders[i]
                M3SourceRow(
                    icon = Icons.Filled.Folder,
                    title = safName(uri),
                    subtitle = "Picked folder — remembered after restart",
                    onClick = { onOpenSafFolder(uri) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                ZephOutlinedButton("Add a folder", onClick = {
                    pickerError = null
                    try {
                        addFolderLauncher.launch(null)
                    } catch (_: Exception) {
                        pickerError = "This TV has no folder picker. Use the storage rows above, or Library → Folders / USB."
                    }
                })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        // Network: SMB + FTP + the honest DLNA row.
        item {
            M3SourcesHeader("Network")
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (smbServers.isEmpty() && ftpServers.isEmpty()) {
            item {
                Text(
                    "No network servers yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            items(smbServers.size) { i ->
                val server = smbServers[i]
                M3SourceRow(
                    icon = Icons.Filled.FolderShared,
                    title = server.host,
                    subtitle = "SMB · smb://${server.host}/${server.share} · Connected",
                    onClick = { onOpenSmbServer(server) },
                    focusRequester = if (i == 0) firstRowFocus else null
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(ftpServers.size) { i ->
                val server = ftpServers[i]
                M3SourceRow(
                    icon = Icons.Filled.Storage,
                    title = "${server.protocol.uppercase()} — ${server.host}",
                    subtitle = "${formatFtpServer(server)} · Saved, not connected",
                    onClick = { onOpenFtpServer(server) },
                    focusRequester = if (smbServers.isEmpty() && i == 0) firstRowFocus else null
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        // DLNA — receiver only, said plainly (So10).
        item {
            M3SourceRow(
                icon = Icons.Filled.Cast,
                title = "DLNA — cast receiver",
                subtitle = "On — your phone can cast videos to this TV. Zeph does not browse DLNA servers yet.",
                onClick = null
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        // Actions + the design's caption (So11/So15).
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZephButton(
                    text = "Add a source",
                    onClick = onAddNetworkServer,
                    icon = Icons.Filled.Add
                )
                ZephTonalButton(
                    text = "Scan network",
                    onClick = {
                        scanNonce++
                        onRefresh()
                    },
                    icon = Icons.Filled.Refresh
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Add a source: SMB, FTP or SFTP — address, login, done. " +
                    "DLNA works the other way: your phone casts to this TV.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            pickerError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** A section header (So2): 16sp bold — "On this device" / "USB" /
 * "Network". Not the 28sp ZephSectionTitle. */
@Composable
private fun M3SourcesHeader(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** One source row (So4): a 44dp primaryContainer icon circle +
 * title + subtitle, in an M3 card. */
@Composable
private fun M3SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    focusRequester: FocusRequester? = null
) {
    var cardModifier = Modifier.fillMaxWidth()
    if (focusRequester != null) cardModifier = cardModifier.focusRequester(focusRequester)
    ZephCard(
        modifier = cardModifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
