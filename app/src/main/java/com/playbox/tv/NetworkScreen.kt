package com.playbox.tv

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Batch 2: Network — play a stream by URL, and browse SMB / NAS servers
 * (a PC or phone shared folder) and play videos straight from them.
 *
 * The server + folder being browsed live in PlayBoxApp (like the Library
 * folder state), so opening a video and coming back does not lose your
 * place. Browsing runs on Dispatchers.IO with a 15s timeout — a dead
 * server must not freeze the TV.
 */
@Composable
internal fun NetworkScreen(
    server: SmbServer?,
    onServer: (SmbServer?) -> Unit,
    path: String?,
    onPath: (String?) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val lastUrlPrefs = remember { context.getSharedPreferences("playbox_last_url", Context.MODE_PRIVATE) }
    var urlText by remember { mutableStateOf(lastUrlPrefs.getString("last", "") ?: "") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf(SmbNetwork.servers(context)) }
    var showAddForm by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(server, path) {
        entries = emptyList()
        browseError = null
        val s = server
        val p = path
        if (s != null && p != null) {
            loading = true
            val result = withContext(Dispatchers.IO) {
                withTimeoutOrNull(15_000L) { runCatching { SmbNetwork.listFolder(s, p) } }
            }
            loading = false
            when {
                result == null -> browseError = "Timed out — check the server is on and on the same Wi-Fi."
                result.isFailure -> browseError = "Couldn't reach this server — check the address, share name, and password."
                else -> entries = result.getOrThrow()
            }
        }
    }

    fun parentPath(url: String, srv: SmbServer): String? {
        if (url.trimEnd('/') == srv.url().trimEnd('/')) return null
        return url.trimEnd('/').substringBeforeLast('/', "") + "/"
    }

    fun playUrl() {
        val u = urlText.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            urlError = "That doesn't look like a link — it needs to start with http:// or https://"
            return
        }
        urlError = null
        lastUrlPrefs.edit().putString("last", u).apply()
        val uri = Uri.parse(u)
        val name = listOfNotNull(uri.host, uri.lastPathSegment).joinToString("/").ifBlank { u }
        val item = VideoItem(path = u, name = name)
        onOpen(item, listOf(item))
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        item {
            Text("Network", fontSize = 36.sp, color = White)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Play from URL", fontSize = 24.sp, color = White)
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = urlText,
                onValueChange = { urlText = it; urlError = null },
                placeholder = { Text("https://…  (.mp4, .m3u8, .mpd)", color = Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                    focusedTextColor = White, unfocusedTextColor = White,
                    cursorColor = Primary, focusedIndicatorColor = Primary,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Play this link") { playUrl() }
            }
            urlError?.let { Spacer(modifier = Modifier.height(8.dp)); Text(it, fontSize = 18.sp, color = Muted) }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (server != null && path != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Back") {
                        val parent = parentPath(path, server)
                        if (parent == null) { onServer(null); onPath(null) } else onPath(parent)
                    }
                    TvButton("Close server") { onServer(null); onPath(null) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("${server.host} / ${server.share}  —  $path", fontSize = 20.sp, color = Muted)
                Spacer(modifier = Modifier.height(16.dp))
                if (loading) Text("Loading…", fontSize = 20.sp, color = Muted)
                browseError?.let { Text(it, fontSize = 20.sp, color = Muted) }
                Spacer(modifier = Modifier.height(8.dp))
            }
            val media = entries.filter { !it.isDir && VideoFilter.isMediaFile(it.name) }
                .map { VideoItem(path = it.url, name = it.name, sizeBytes = it.size, dateAddedSec = it.lastModified / 1000) }
            items(entries.filter { it.isDir }, key = { it.url }) { entry ->
                Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onPath(entry.url) }.padding(20.dp)) {
                    Text("Folder: ${entry.name}", fontSize = 20.sp, color = White)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(media, key = { it.path }) { item ->
                Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { onOpen(item, media) }.padding(20.dp)) {
                    Text(if (item.likelyUnsupported) "${item.name}  ·  May not play" else item.name, fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (!loading && browseError == null && entries.isEmpty()) {
                item { Text("This folder is empty.", fontSize = 20.sp, color = Muted) }
            }
        } else {
            item {
                Text("Shared folders (SMB / NAS)", fontSize = 24.sp, color = White)
                Spacer(modifier = Modifier.height(12.dp))
                if (servers.isEmpty()) Text("No servers yet — add your PC or NAS below.", fontSize = 20.sp, color = Muted)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(servers, key = { it.host + "/" + it.share }) { srv ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.weight(1f).background(Surface, RoundedCornerShape(16.dp)).clickable { onServer(srv); onPath(srv.url()) }.padding(20.dp)) {
                        Text("${srv.host} / ${srv.share}", fontSize = 20.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TvButton("Remove") {
                        SmbNetwork.removeServer(context, srv)
                        servers = SmbNetwork.servers(context)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                if (showAddForm) {
                    TextField(value = host, onValueChange = { host = it }, placeholder = { Text("Address — e.g. 192.168.1.10 or MY-PC", color = Muted) }, singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedTextColor = White, unfocusedTextColor = White, cursorColor = Primary, focusedIndicatorColor = Primary, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(value = share, onValueChange = { share = it }, placeholder = { Text("Share name — e.g. Movies", color = Muted) }, singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedTextColor = White, unfocusedTextColor = White, cursorColor = Primary, focusedIndicatorColor = Primary, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(value = user, onValueChange = { user = it }, placeholder = { Text("Username (leave empty for guest)", color = Muted) }, singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedTextColor = White, unfocusedTextColor = White, cursorColor = Primary, focusedIndicatorColor = Primary, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(value = pass, onValueChange = { pass = it }, placeholder = { Text("Password", color = Muted) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedTextColor = White, unfocusedTextColor = White, cursorColor = Primary, focusedIndicatorColor = Primary, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    formError?.let { Text(it, fontSize = 18.sp, color = Muted); Spacer(modifier = Modifier.height(8.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Save server") {
                            val h = host.trim()
                            val sh = share.trim()
                            if (h.isEmpty() || sh.isEmpty()) {
                                formError = "Address and share name are needed."
                            } else {
                                val srv = SmbServer(h, sh, user.trim(), pass)
                                SmbNetwork.saveServer(context, srv)
                                servers = SmbNetwork.servers(context)
                                showAddForm = false
                                host = ""; share = ""; user = ""; pass = ""
                                formError = null
                                onServer(srv); onPath(srv.url())
                            }
                        }
                        TvButton("Cancel") { showAddForm = false; formError = null }
                    }
                } else {
                    TvButton("Add a server") { showAddForm = true }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Share a folder on your PC or NAS, then add it here once — PlayBox remembers it.", fontSize = 18.sp, color = Muted)
                }
            }
        }
    }
}
