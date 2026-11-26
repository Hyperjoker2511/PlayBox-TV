package com.playbox.tv

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- ZEPH Sources (v5, 1.9.0) ----------
// How content ARRIVES: device folders, USB, SMB/FTP/SFTP and DLNA.
// This screen is the front door to all of them — it lists what is
// already there and opens it in Library, where the browsing lives.
//   - Device: internal storage + every /storage root (USB, SD) from
//     MediaLibrary.storageRoots(), plus the SAF folders the user
//     picked (they survive reboots).
//   - Network: the saved SMB and FTP servers, opened in Library →
//     Network at that server.
//   - DLNA: honest — Zeph is a DLNA RENDERER (it receives casts
//     from the phone). It does not browse DLNA servers yet, so the
//     row says so instead of faking a browser.
// "Add a source" picks a folder (SAF — USB included). "Scan
// network" re-reads the saved servers and storage roots. Adding a
// network server lives in Library → Network, where the forms are.

@Composable
internal fun M3SourcesScreen(
    onOpenFolder: (File) -> Unit,
    onOpenSafFolder: (Uri) -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenSmbServer: (SmbServer) -> Unit,
    onOpenFtpServer: (FtpServer) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var scanNonce by remember { mutableStateOf(0) }
    var scanNote by remember { mutableStateOf<String?>(null) }
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Sources",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Where your videos come from — folders, USB, network shares.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        // Device.
        item {
            ZephSectionTitle("Device")
            Spacer(modifier = Modifier.height(12.dp))
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
        } else {
            items(roots.size) { i ->
                val root = roots[i]
                val isInternal = root.absolutePath == "/storage/emulated/0"
                M3SourceRow(
                    icon = if (isInternal) Icons.Filled.Folder else Icons.Filled.Usb,
                    title = if (isInternal) "Internal storage" else root.name,
                    subtitle = root.absolutePath,
                    onClick = { onOpenFolder(root) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        // Downloads (inside internal storage).
        item {
            M3SourceRow(
                icon = Icons.Filled.Download,
                title = "Downloads",
                subtitle = "Internal storage / Download",
                onClick = {
                    val downloads = File("/storage/emulated/0/Download")
                    if (downloads.isDirectory) onOpenFolder(downloads)
                    else onOpenFolder(File("/storage/emulated/0"))
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        // SAF folders the user picked.
        if (safFolders.isNotEmpty()) {
            item {
                Text(
                    "Picked folders",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
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
        }
        // Network.
        item {
            ZephSectionTitle("Network")
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
                    title = "SMB — ${formatSmbServer(server)}",
                    subtitle = "Windows / NAS share",
                    onClick = { onOpenSmbServer(server) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(ftpServers.size) { i ->
                val server = ftpServers[i]
                M3SourceRow(
                    icon = Icons.Filled.Storage,
                    title = "FTP — ${formatFtpServer(server)}",
                    subtitle = "FTP / SFTP server",
                    onClick = { onOpenFtpServer(server) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        // DLNA — receiver only, said plainly.
        item {
            M3SourceRow(
                icon = Icons.Filled.Cast,
                title = "DLNA — cast receiver",
                subtitle = "On — your phone can cast videos to this TV. Zeph does not browse DLNA servers yet.",
                onClick = null
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Add a network server (SMB / FTP) from Library → Network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        // Actions.
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZephButton(
                    text = "Add a source",
                    onClick = {
                        pickerError = null
                        try {
                            addFolderLauncher.launch(null)
                        } catch (_: Exception) {
                            pickerError = "This TV has no folder picker. Use the storage rows above, or Library → Folders / USB."
                        }
                    },
                    icon = Icons.Filled.Folder
                )
                ZephTonalButton(
                    text = "Scan network",
                    onClick = {
                        scanNonce++
                        onRefresh()
                        scanNote = "Scanned — saved servers and storage re-read."
                    }
                )
            }
            scanNote?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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

/** One source row: icon tile, title + subtitle, chevron-free. */
@Composable
private fun M3SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
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
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
