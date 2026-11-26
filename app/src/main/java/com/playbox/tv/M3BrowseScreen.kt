package com.playbox.tv

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

// ---------- ZEPH Browse screen (Patch 4 — the design's Browse.tsx) ----------
// Header + Open stream / Add server, Storage cards with REAL used
// space, Network server rows with protocol badges, a footer with
// REAL counts, and a folder view with breadcrumb chips + type-
// coloured rows — ONE folder view for local storage AND network
// servers.
//
// Kept Zeph features the design lacks: SAF picked folders (a
// section under Storage), the DLNA receiver row (receiver-only,
// said plainly), the real add-server forms (SMB + FTP/SFTP, shown
// through the existing network screen), and the network stream
// dialog. No fake connecting dialog: the design's 1.3s
// "Authenticating" delay is a demo artefact — a real listing
// shows a spinner while it loads. No fake "last scan" in the
// footer (no scan time is recorded).

@Composable
internal fun M3BrowseScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlayFromStart: (VideoItem, List<VideoItem>) -> Unit,
    networkServer: SmbServer?,
    onNetworkServer: (SmbServer?) -> Unit,
    networkPath: String?,
    onNetworkPath: (String?) -> Unit,
    ftpServer: FtpServer?,
    onFtpServer: (FtpServer?) -> Unit,
    ftpPath: String?,
    onFtpPath: (String?) -> Unit,
    addFormRequest: Int,
    onAddNetworkServer: () -> Unit
) {
    val context = LocalContext.current
    var folderStack by remember { mutableStateOf<List<File>>(emptyList()) }
    var safStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showNetworkScreen by remember { mutableStateOf(false) }
    var showStreamDialog by remember { mutableStateOf(false) }
    // When a server opens (e.g. saved from the add form), the
    // network screen steps aside for the folder view.
    LaunchedEffect(networkServer, ftpServer) {
        if (networkServer != null || ftpServer != null) showNetworkScreen = false
    }
    val folder = folderStack.lastOrNull()
    val safFolder = safStack.lastOrNull()

    fun navigateUp() {
        when {
            safFolder != null -> safStack = safStack.dropLast(1)
            folder != null -> folderStack = folderStack.dropLast(1)
            networkServer != null -> {
                val parent = parentSmbPath(networkPath, networkServer)
                if (parent == null) {
                    onNetworkServer(null); onNetworkPath(null)
                } else onNetworkPath(parent)
            }
            ftpServer != null -> {
                val parent = parentFtpPath(ftpPath)
                if (parent == null) {
                    onFtpServer(null); onFtpPath(null)
                } else onFtpPath(parent)
            }
            showNetworkScreen -> showNetworkScreen = false
        }
    }
    BackHandler(
        enabled = safFolder != null || folder != null || networkServer != null ||
            ftpServer != null || showNetworkScreen
    ) { navigateUp() }

    when {
        folder != null -> M3BrowseLocalFolderView(
            stack = folderStack,
            onStack = { folderStack = it },
            onOpen = onOpen,
            onPlayFromStart = onPlayFromStart
        )
        safFolder != null -> M3BrowseSafFolderView(
            stack = safStack,
            onStack = { safStack = it },
            onOpen = onOpen,
            onPlayFromStart = onPlayFromStart
        )
        networkServer != null -> M3BrowseSmbFolderView(
            server = networkServer,
            path = networkPath ?: networkServer.url(),
            onPath = onNetworkPath,
            onClose = { onNetworkServer(null); onNetworkPath(null) },
            onOpen = onOpen,
            onPlayFromStart = onPlayFromStart
        )
        ftpServer != null -> M3BrowseFtpFolderView(
            server = ftpServer,
            path = ftpPath ?: ftpServer.rootPath(),
            onPath = onFtpPath,
            onClose = { onFtpServer(null); onFtpPath(null) },
            onOpen = onOpen,
            onPlayFromStart = onPlayFromStart
        )
        showNetworkScreen -> M3NetworkScreen(
            server = networkServer, onServer = onNetworkServer,
            path = networkPath, onPath = onNetworkPath,
            ftpServer = ftpServer, onFtpServer = onFtpServer,
            ftpPath = ftpPath, onFtpPath = onFtpPath,
            onOpen = onOpen,
            addFormRequest = addFormRequest
        )
        else -> M3BrowseRoot(
            videos = videos,
            audio = audio,
            onOpenStream = { showStreamDialog = true },
            onAddServer = {
                onAddNetworkServer()
                showNetworkScreen = true
            },
            onOpenFolder = { folderStack = listOf(it) },
            onOpenSafFolder = { safStack = listOf(it) },
            onOpenSmbServer = { srv ->
                onNetworkServer(srv); onNetworkPath(srv.url())
            },
            onOpenFtpServer = { srv ->
                onFtpServer(srv); onFtpPath(srv.rootPath())
            }
        )
    }
    if (showStreamDialog) {
        M3NetworkStreamDialog(
            onClose = { showStreamDialog = false },
            onOpen = onOpen
        )
    }
}

/** The SMB parent path (copied from M3NetworkScreen): null at the
 * server root, otherwise the parent URL with a trailing slash. */
private fun parentSmbPath(url: String?, srv: SmbServer): String? {
    val u = url ?: return null
    if (u.trimEnd('/') == srv.url().trimEnd('/')) return null
    return u.trimEnd('/').substringBeforeLast('/', "") + "/"
}

/** The FTP parent path (copied from M3NetworkScreen): null at "/". */
private fun parentFtpPath(path: String?): String? {
    val trimmed = path?.trimEnd('/') ?: return null
    if (trimmed.isEmpty() || trimmed == "/") return null
    return trimmed.substringBeforeLast('/', "/") + "/"
}

/** "duration · size" for a media file's supporting line. */
private fun browseMediaInfo(item: VideoItem): String =
    listOf(
        if (item.durationMs > 0L) TimeFormatter.format(item.durationMs) else "",
        formatSize(item.sizeBytes)
    ).filter { it.isNotBlank() }.joinToString("  ·  ")

private fun folderDisplayName(file: File): String =
    if (file.absolutePath == "/storage/emulated/0") "Internal storage" else file.name

// ---------- Root view ----------

@Composable
private fun M3BrowseRoot(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    onOpenStream: () -> Unit,
    onAddServer: () -> Unit,
    onOpenFolder: (File) -> Unit,
    onOpenSafFolder: (Uri) -> Unit,
    onOpenSmbServer: (SmbServer) -> Unit,
    onOpenFtpServer: (FtpServer) -> Unit
) {
    val context = LocalContext.current
    // Re-read on every visit: the tab content is recreated on tab
    // switch, so remember re-runs then.
    val roots = remember { MediaLibrary.storageRoots() }
    val smbServers = remember { SmbNetwork.servers(context) }
    val ftpServers = remember { FtpNetwork.servers(context) }
    var safFolders by remember { mutableStateOf(MediaLibrary.safFolders(context)) }
    var safNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(safFolders) {
        safNames = withContext(Dispatchers.IO) {
            safFolders.distinct().associate {
                it.toString() to MediaLibrary.safFolderName(context, it)
            }
        }
    }
    fun safName(uri: Uri): String = safNames[uri.toString()] ?: "Folder"
    val addFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            MediaLibrary.addSafFolder(context, uri)
            safFolders = MediaLibrary.safFolders(context)
            onOpenSafFolder(uri)
        }
    }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstCardFocus.requestFocus() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = if (maxWidth >= 700.dp) 3 else 1
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                M3ScreenHeader(
                    title = "Browse",
                    subtitle = "Local storage, USB drives and network shares",
                    actions = {
                        ZephTonalButton(
                            text = "Open stream",
                            onClick = onOpenStream,
                            icon = Icons.Filled.Stream
                        )
                        ZephButton(
                            text = "Add server",
                            onClick = onAddServer,
                            icon = Icons.Filled.Add
                        )
                    }
                )
            }
            item {
                M3BrowseSectionTitle("Storage")
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
                itemsIndexed(
                    roots.chunked(cols),
                    key = { _, row -> row.firstOrNull()?.absolutePath ?: "row" }
                ) { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { colIndex, root ->
                            M3StorageCard(
                                root = root,
                                index = rowIndex * cols + colIndex,
                                onClick = { onOpenFolder(root) },
                                focusRequester = if (rowIndex == 0 && colIndex == 0) firstCardFocus else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat((cols - row.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            if (safFolders.isNotEmpty()) {
                item {
                    M3BrowseSectionTitle("Picked folders")
                    Spacer(modifier = Modifier.height(12.dp))
                }
                itemsIndexed(safFolders, key = { _, uri -> uri.toString() }) { i, uri ->
                    M3BrowseListRow(
                        row = BrowseRow(
                            name = safName(uri),
                            info = "Picked folder — remembered after restart",
                            kind = BrowseRowKind.FOLDER,
                            icon = Icons.Filled.Folder,
                            onClick = { onOpenSafFolder(uri) }
                        ),
                        index = i,
                        riseDelayMs = 200 + i * 60
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                item {
                    ZephOutlinedButton("Add a folder", onClick = {
                        pickerError = null
                        try {
                            addFolderLauncher.launch(null)
                        } catch (_: Exception) {
                            pickerError = "This TV has no folder picker. Use the storage cards above, or the Video tab."
                        }
                    })
                    Spacer(modifier = Modifier.height(12.dp))
                    pickerError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            item {
                M3BrowseSectionTitle("Network")
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
                itemsIndexed(smbServers, key = { _, srv -> srv.host + "/" + srv.share }) { i, srv ->
                    M3BrowseListRow(
                        row = BrowseRow(
                            name = "${srv.host} / ${srv.share}",
                            info = "smb://${srv.host}/${srv.share}",
                            kind = BrowseRowKind.SERVER,
                            // The design's server icon is a DNS glyph
                            // (Browse.tsx's server rows use "dns").
                            icon = Icons.Filled.Dns,
                            badge = "SMB",
                            onClick = { onOpenSmbServer(srv) }
                        ),
                        index = i,
                        riseDelayMs = 200 + i * 60
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                itemsIndexed(ftpServers, key = { _, srv -> srv.label() }) { i, srv ->
                    M3BrowseListRow(
                        row = BrowseRow(
                            name = srv.label(),
                            info = formatFtpServer(srv),
                            kind = BrowseRowKind.SERVER,
                            // The design's FTP icon is a cloud glyph.
                            icon = Icons.Filled.Cloud,
                            badge = srv.protocol.uppercase(),
                            onClick = { onOpenFtpServer(srv) }
                        ),
                        index = smbServers.size + i,
                        riseDelayMs = 200 + (smbServers.size + i) * 60
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            item {
                M3BrowseListRow(
                    row = BrowseRow(
                        name = "DLNA — cast receiver",
                        info = "On — your phone can cast videos to this TV",
                        kind = BrowseRowKind.SERVER,
                        icon = Icons.Filled.Cast,
                        onClick = null
                    ),
                    index = smbServers.size + ftpServers.size,
                    riseDelayMs = 200 + (smbServers.size + ftpServers.size) * 60
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${audio.size} songs and ${videos.size} videos indexed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** The design's SectionTitle: title-l, plain (Regular 400 —
 * the design's type scale has no bold section titles). */
@Composable
private fun M3BrowseSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** One storage card (Browse.tsx): 48dp secondaryContainer icon
 * circle + name + "X GB of Y GB used" + a used-space progress
 * bar. All of it real (StatFs). */
@Composable
private fun M3StorageCard(
    root: File,
    index: Int,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isInternal = root.absolutePath == "/storage/emulated/0"
    val stat = remember(root) { runCatching { StatFs(root.path) }.getOrNull() }
    val total = stat?.totalBytes ?: 0L
    val free = stat?.availableBytes ?: 0L
    val used = (total - free).coerceAtLeast(0L)
    val fraction = if (total > 0) used.toFloat() / total else 0f
    var cardModifier = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (focusRequester != null) cardModifier = cardModifier.focusRequester(focusRequester)
    Column(
        modifier = cardModifier
            .zephFocus(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(16.dp)
            .riseIn(minOf(index, 10) * 70)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isInternal) Icons.Filled.Tv else Icons.Filled.Usb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isInternal) "Internal storage" else root.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (total > 0) "${formatSize(used)} of ${formatSize(total)} used" else "Storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ZephLinearProgress(progress = fraction)
    }
}

// ---------- Folder view (shared by local + network) ----------

private enum class BrowseRowKind { FOLDER, VIDEO, AUDIO, SERVER, OTHER }

private data class BrowseRow(
    val name: String,
    val info: String,
    val kind: BrowseRowKind,
    val icon: ImageVector,
    val badge: String? = null,
    val onClick: (() -> Unit)? = null
)

/** The design's folder view (Browse.tsx): header with an "Up one
 * level" button, breadcrumb chips (Root + segments, last one
 * selected), and type-coloured icon rows. The list re-mounts on
 * navigation (key on the crumbs), replaying the rise. */
@Composable
private fun M3BrowseFolderView(
    title: String,
    crumbs: List<String>,
    onCrumb: (Int) -> Unit,
    onUp: () -> Unit,
    rows: List<BrowseRow>,
    loading: Boolean,
    error: String?
) {
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(crumbs) { firstRowFocus.requestFocus() }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(20.dp))
        M3ScreenHeader(
            title = title,
            subtitle = "${rows.size} items",
            actions = {
                // The design's "Up one level" is a 40dp ROUND
                // tonal icon button (Browse.tsx: IconButton
                // variant="tonal"), not a wide pill.
                val upInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .zephFocus(upInteraction, focusScale = 1f, cornerRadius = 20.dp)
                        .clickable(interactionSource = upInteraction, indication = null) { onUp() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowBack, contentDescription = "Up one level",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
        // Breadcrumb chips (Root + segments, last one selected).
        // A plain scrolling Row, not a LazyRow: the chips are few,
        // and item() cannot be called inside itemsIndexed.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            crumbs.forEachIndexed { i, crumb ->
                if (i > 0) {
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                ZephFilterChip(
                    // The design's first chip is "Root" (with the
                    // storage icon) — the storage/server name is
                    // the screen title, not the first crumb.
                    label = if (i == 0) "Root" else crumb,
                    selected = i == crumbs.lastIndex,
                    onClick = { onCrumb(i) },
                    leadingIcon = if (i == 0) Icons.Filled.Storage else null
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        key(crumbs.joinToString("/")) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (loading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Loading…",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (error != null) {
                    item {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else if (rows.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.FolderOpen, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "This folder is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(rows, key = { _, row -> row.kind.name + ":" + row.name }) { index, row ->
                        M3BrowseListRow(
                            row = row,
                            index = index,
                            focusRequester = if (index == 0) firstRowFocus else null
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/** The design's ListItem, as Zeph uses it here: a 40dp
 * type-coloured icon circle, headline + supporting, and a
 * trailing chevron (folders) or protocol badge (servers).
 * Non-media files are OTHER rows (the design's "other" type):
 * a surface-container-highest circle, a lightbulb icon, the
 * file size — and NOT clickable. */
@Composable
private fun M3BrowseListRow(
    row: BrowseRow,
    index: Int,
    focusRequester: FocusRequester? = null,
    // The rise delay in ms. Null = the folder view's stagger
    // (min(index, 10) * 35); the root view's server rows pass
    // the design's 200 + i * 60.
    riseDelayMs: Int? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val (container, tint) = when (row.kind) {
        BrowseRowKind.FOLDER -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        BrowseRowKind.VIDEO, BrowseRowKind.SERVER -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        BrowseRowKind.AUDIO -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        BrowseRowKind.OTHER -> MaterialTheme.colorScheme.surfaceContainerHighest to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    var modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .clip(RoundedCornerShape(16.dp))
    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)
    Row(
        modifier = modifier
            .then(
                if (row.onClick != null) {
                    Modifier.zephFocus(interaction)
                        .clickable(interactionSource = interaction, indication = null) { row.onClick?.invoke() }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .riseIn(riseDelayMs ?: minOf(index, 10) * 35),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                row.icon, contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.info.isNotBlank()) {
                Text(
                    row.info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (row.badge != null) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                row.badge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        } else if (row.kind == BrowseRowKind.FOLDER) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Local folder backend ----------

@Composable
private fun M3BrowseLocalFolderView(
    stack: List<File>,
    onStack: (List<File>) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlayFromStart: (VideoItem, List<VideoItem>) -> Unit
) {
    val folder = stack.last()
    var contents by remember {
        mutableStateOf<Pair<List<File>, List<VideoItem>>>(emptyList<File>() to emptyList())
    }
    // Non-media files in this folder (the design's "file" type)
    // + each subfolder's direct-children count, both read in the
    // same IO pass as the listing.
    var others by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var childCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(folder) {
        contents = emptyList<File>() to emptyList()
        others = emptyList()
        childCounts = emptyMap()
        val (listed, otherFiles, counts) = withContext(Dispatchers.IO) {
            val listed = MediaLibrary.listFolder(folder)
            val all = folder.listFiles() ?: emptyArray()
            val nonMedia = all.filter { it.isFile && !VideoFilter.isMediaFile(it.name) }
                .map {
                    VideoItem(
                        path = it.absolutePath, name = it.name,
                        sizeBytes = it.length(), dateAddedSec = it.lastModified() / 1000
                    )
                }
            val counts = listed.first.associate { dir ->
                dir.absolutePath to (dir.listFiles()?.size ?: 0)
            }
            Triple(listed, nonMedia, counts)
        }
        contents = listed
        others = otherFiles
        childCounts = counts
    }
    val (dirs, media) = contents
    val rows = remember(dirs, media, others, childCounts) {
        buildList {
            dirs.forEach { dir ->
                // The design's folder row: "{N} items" — the
                // direct-children count, not the indexed-video
                // count the app used to show.
                val count = childCounts[dir.absolutePath] ?: 0
                add(
                    BrowseRow(
                        name = dir.name,
                        info = "$count items",
                        kind = BrowseRowKind.FOLDER,
                        icon = Icons.Filled.Folder,
                        onClick = { onStack(stack + dir) }
                    )
                )
            }
            media.forEach { item ->
                add(
                    BrowseRow(
                        name = item.name,
                        info = browseMediaInfo(item),
                        kind = if (item.isVideo) BrowseRowKind.VIDEO else BrowseRowKind.AUDIO,
                        icon = if (item.isVideo) Icons.Filled.VideoFile else Icons.Filled.AudioFile,
                        onClick = {
                            if (item.isVideo) onOpen(item, media) else onPlayFromStart(item, media)
                        }
                    )
                )
            }
            // Non-media files are shown, not hidden (the
            // design's "file" type): a lightbulb row, the kind
            // + size, and NOT clickable.
            others.forEach { item ->
                add(
                    BrowseRow(
                        name = item.name,
                        info = "${containerOf(item)} · ${formatSize(item.sizeBytes)}",
                        kind = BrowseRowKind.OTHER,
                        icon = Icons.Filled.Lightbulb,
                        onClick = null
                    )
                )
            }
        }
    }
    M3BrowseFolderView(
        // The design's folder-view title is the STORAGE name
        // ("Internal storage"), not the current folder — the
        // breadcrumb already shows where you are.
        title = folderDisplayName(stack.first()),
        crumbs = stack.map { folderDisplayName(it) },
        onCrumb = { i -> onStack(stack.take(i + 1)) },
        onUp = { onStack(stack.dropLast(1)) },
        rows = rows,
        loading = false,
        error = null
    )
}

// ---------- SAF folder backend ----------

@Composable
private fun M3BrowseSafFolderView(
    stack: List<Uri>,
    onStack: (List<Uri>) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlayFromStart: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val folder = stack.last()
    var contents by remember {
        mutableStateOf<Pair<List<Uri>, List<VideoItem>>>(emptyList<Uri>() to emptyList())
    }
    var others by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(folder) {
        contents = emptyList<Uri>() to emptyList()
        others = emptyList()
        contents = withContext(Dispatchers.IO) { MediaLibrary.listSafFolder(context, folder) }
        others = withContext(Dispatchers.IO) { MediaLibrary.listSafOtherFiles(context, folder) }
    }
    LaunchedEffect(contents) {
        val uris = contents.first.distinct()
        if (uris.isNotEmpty()) {
            names = withContext(Dispatchers.IO) {
                uris.associate { it.toString() to MediaLibrary.safFolderName(context, it) }
            }
        }
    }
    fun dirName(uri: Uri): String = names[uri.toString()] ?: "Folder"
    // The root of the stack keeps its picked name; subfolders use
    // the names loaded above (falling back to "Folder" while they
    // load — the row updates when the name arrives).
    var rootName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stack.first()) {
        rootName = withContext(Dispatchers.IO) {
            MediaLibrary.safFolderName(context, stack.first())
        }
    }
    fun crumbName(uri: Uri): String =
        if (uri == stack.first()) (rootName ?: "Folder") else dirName(uri)
    val (dirs, media) = contents
    val rows = remember(dirs, media, names, others) {
        buildList {
            dirs.forEach { dir ->
                add(
                    BrowseRow(
                        name = dirName(dir),
                        info = "Folder",
                        kind = BrowseRowKind.FOLDER,
                        icon = Icons.Filled.Folder,
                        onClick = { onStack(stack + dir) }
                    )
                )
            }
            media.forEach { item ->
                add(
                    BrowseRow(
                        name = item.name,
                        info = browseMediaInfo(item),
                        kind = if (item.isVideo) BrowseRowKind.VIDEO else BrowseRowKind.AUDIO,
                        icon = if (item.isVideo) Icons.Filled.VideoFile else Icons.Filled.AudioFile,
                        onClick = {
                            if (item.isVideo) onOpen(item, media) else onPlayFromStart(item, media)
                        }
                    )
                )
            }
            // Non-media files are shown, not hidden (the
            // design's "file" type): a lightbulb row, the kind
            // + size, and NOT clickable.
            others.forEach { item ->
                add(
                    BrowseRow(
                        name = item.name,
                        info = "${containerOf(item)} · ${formatSize(item.sizeBytes)}",
                        kind = BrowseRowKind.OTHER,
                        icon = Icons.Filled.Lightbulb,
                        onClick = null
                    )
                )
            }
        }
    }
    M3BrowseFolderView(
        // The design's folder-view title is the storage name —
        // here, the picked folder's own name.
        title = crumbName(stack.first()),
        crumbs = stack.map { crumbName(it) },
        onCrumb = { i -> onStack(stack.take(i + 1)) },
        onUp = { onStack(stack.dropLast(1)) },
        rows = rows,
        loading = false,
        error = null
    )
}

// ---------- SMB folder backend ----------

@Composable
private fun M3BrowseSmbFolderView(
    server: SmbServer,
    path: String,
    onPath: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlayFromStart: (VideoItem, List<VideoItem>) -> Unit
) {
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(server, path) {
        entries = emptyList()
        error = null
        loading = true
        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(15_000L) { runCatching { SmbNetwork.listFolder(server, path) } }
        }
        loading = false
        when {
            result == null -> error = "Timed out — check the server is on and on the same Wi-Fi."
            result.isFailure -> error = "Couldn't reach this server — check the address, share name, and password."
            else -> entries = result.getOrThrow()
        }
    }
    val media = remember(entries) {
        entries.filter { !it.isDir && VideoFilter.isMediaFile(it.name) }
            .map {
                VideoItem(
                    path = it.url, name = it.name,
                    sizeBytes = it.size, dateAddedSec = it.lastModified / 1000
                )
            }
    }
    val rows = remember(entries, media) {
        buildList {
            entries.filter { it.isDir }.forEach { entry ->
                add(
                    BrowseRow(
                        name = entry.name,
                        info = "Folder",
                        kind = BrowseRowKind.FOLDER,
                        icon = Icons.Filled.Folder,
                        onClick = { onPath(entry.url) }
                    )
                )
            }
            media.forEach { item ->
                add(
                    BrowseRow(
                        name = item.name,
                        info = browseMediaInfo(item),
                        kind = if (item.isVideo) BrowseRowKind.VIDEO else BrowseRowKind.AUDIO,
                        icon = if (item.isVideo) Icons.Filled.VideoFile else Icons.Filled.AudioFile,
                        onClick = {
                            if (item.isVideo) onOpen(item, media) else onPlayFromStart(item, media)
                        }
                    )
                )
            }
            // Non-media files are shown, not hidden (the
            // design's "file" type): a lightbulb row, the kind
            // + size, and NOT clickable.
            entries.filter { !it.isDir && !VideoFilter.isMediaFile(it.name) }.forEach { entry ->
                add(
                    BrowseRow(
                        name = entry.name,
                        info = "${entry.name.substringAfterLast('.', "").uppercase().ifBlank { "FILE" }} · ${formatSize(entry.size)}",
                        kind = BrowseRowKind.OTHER,
                        icon = Icons.Filled.Lightbulb,
                        onClick = null
                    )
                )
            }
        }
    }
    val segments = remember(path, server) {
        path.trimEnd('/').removePrefix(server.url().trimEnd('/'))
            .split('/').filter { it.isNotEmpty() }
    }
    M3BrowseFolderView(
        // The design's folder-view title is the server name,
        // not the current folder — the breadcrumb shows the path.
        title = "${server.host} / ${server.share}",
        crumbs = listOf("${server.host} / ${server.share}") + segments,
        onCrumb = { i ->
            if (i == 0) onPath(server.url())
            else onPath(server.url().trimEnd('/') + "/" + segments.take(i).joinToString("/") + "/")
        },
        onUp = {
            val parent = parentSmbPath(path, server)
            if (parent == null) onClose() else onPath(parent)
        },
        rows = rows,
        loading = loading,
        error = error
    )
}

// ---------- FTP/SFTP folder backend ----------

@Composable
private fun M3BrowseFtpFolderView(
    server: FtpServer,
    path: String,
    onPath: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlayFromStart: (VideoItem, List<VideoItem>) -> Unit
) {
    var entries by remember { mutableStateOf<List<FtpEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(server, path) {
        entries = emptyList()
        error = null
        loading = true
        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(15_000L) { runCatching { FtpNetwork.listFolder(server, path) } }
        }
        loading = false
        when {
            result == null -> error = "Timed out — check the server is on and on the same Wi-Fi."
            result.isFailure -> error = "Couldn't reach this server — check the address, port, and password."
            else -> entries = result.getOrThrow()
        }
    }
    val media = remember(entries) {
        entries.filter { !it.isDir && VideoFilter.isMediaFile(it.name) }
            .map {
                VideoItem(
                    path = ftpPlayUrl(server, it.path), name = it.name,
                    sizeBytes = it.size, dateAddedSec = it.lastModified / 1000
                )
            }
    }
    val rows = remember(entries, media) {
        buildList {
            entries.filter { it.isDir }.forEach { entry ->
                add(
                    BrowseRow(
                        name = entry.name,
                        info = "Folder",
                        kind = BrowseRowKind.FOLDER,
                        icon = Icons.Filled.Folder,
                        onClick = { onPath(entry.path) }
                    )
                )
            }
            media.forEach { item ->
                add(
                    BrowseRow(
                        name = item.name,
                        info = browseMediaInfo(item),
                        kind = if (item.isVideo) BrowseRowKind.VIDEO else BrowseRowKind.AUDIO,
                        icon = if (item.isVideo) Icons.Filled.VideoFile else Icons.Filled.AudioFile,
                        onClick = {
                            if (item.isVideo) onOpen(item, media) else onPlayFromStart(item, media)
                        }
                    )
                )
            }
            // Non-media files are shown, not hidden (the
            // design's "file" type): a lightbulb row, the kind
            // + size, and NOT clickable.
            entries.filter { !it.isDir && !VideoFilter.isMediaFile(it.name) }.forEach { entry ->
                add(
                    BrowseRow(
                        name = entry.name,
                        info = "${entry.name.substringAfterLast('.', "").uppercase().ifBlank { "FILE" }} · ${formatSize(entry.size)}",
                        kind = BrowseRowKind.OTHER,
                        icon = Icons.Filled.Lightbulb,
                        onClick = null
                    )
                )
            }
        }
    }
    val segments = remember(path) {
        path.trimEnd('/').split('/').filter { it.isNotEmpty() }
    }
    M3BrowseFolderView(
        // The design's folder-view title is the server label,
        // not the current folder — the breadcrumb shows the path.
        title = server.label(),
        crumbs = listOf(server.label()) + segments,
        onCrumb = { i ->
            if (i == 0) onPath(server.rootPath())
            else onPath("/" + segments.take(i).joinToString("/") + "/")
        },
        onUp = {
            val parent = parentFtpPath(path)
            if (parent == null) onClose() else onPath(parent)
        },
        rows = rows,
        loading = loading,
        error = error
    )
}
