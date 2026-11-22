package com.playbox.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar

/*
 * PlayBox TV — Batch 1 UI, adapted from the user's full Material 3 design
 * (design export: workspace/goals/playbox-tv/design/, React/Vite + Tailwind).
 * Design tokens: Material 3, seed violet #7B5CFF, dark surface #121318.
 * Stage is 1920x1080 in the design, scaled to fit — in Compose this is
 * dp/sp + weight-based rows, so the same layout works on a 32-inch TV
 * and on any other size. No hard-coded pixel sizes.
 *
 * Adapted to local files only. The design's demo catalogue (movies,
 * series, profiles, streaming) is NOT built: there is no sign-in, no
 * profiles screen, no fake catalogue. Home / Library / Search show the
 * user's real local videos, music and USB folders, with honest empty
 * states when there is nothing there yet.
 */

private enum class Tab { Home, Search, Library, Settings }

private val Primary = Color(0xFF7B5CFF)
private val PrimaryContainer = Color(0xFF4A2E8A)
private val SecondaryContainer = Color(0xFF3A2E5C)
private val Bg = Color(0xFF121318)
private val Surface = Color(0xFF1A1B22)
private val SurfaceHigh = Color(0xFF23242E)
private val White = Color.White
private val Muted = Color(0xFFA1A1AA)

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
}

private fun resumePositions(context: Context): Map<String, Long> {
    val prefs = context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE)
    val out = mutableMapOf<String, Long>()
    prefs.all.forEach { (k, v) -> if (v is Long) out[k] = v }
    return out
}

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(perms)
        }
        setContent { PlayBoxApp() }
    }
}

@Composable
private fun PlayBoxApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Home) }
    var details by remember { mutableStateOf<VideoItem?>(null) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var playList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    val videos = remember { MediaLibrary.listVideosFromMediaStore(context) }
    val audio = remember { MediaLibrary.listAudioFromMediaStore(context) }
    val all = remember(videos, audio) { videos + audio }

    MaterialTheme(colorScheme = darkColorScheme(primary = Primary, surface = Bg, background = Bg)) {
        Box(modifier = Modifier.fillMaxSize().background(Bg)) {
            if (playing != null) {
                PlayerScreen(
                    item = playing!!,
                    queue = playList.ifEmpty { all },
                    onExit = { playing = null },
                    onPlayNext = { next -> playing = next }
                )
            } else if (details != null) {
                DetailsScreen(
                    item = details!!,
                    onBack = { details = null },
                    onPlay = { playing = it; playList = all; details = null }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Rail(tab = tab, onTab = { tab = it })
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (tab) {
                            Tab.Home -> HomeScreen(
                                videos = videos, audio = audio,
                                onOpen = { details = it },
                                onFolders = { tab = Tab.Library }
                            )
                            Tab.Search -> SearchScreen(
                                items = all, search = search, onSearch = { search = it },
                                onOpen = { details = it }
                            )
                            Tab.Library -> LibraryScreen(
                                videos = videos, audio = audio,
                                onOpen = { details = it }
                            )
                            Tab.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Shared TV controls ---------- */

@Composable
private fun TvTile(text: String, sub: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .width(220.dp)
            .background(if (focused) PrimaryContainer else Surface, RoundedCornerShape(24.dp))
            .border(if (focused) 4.dp else 0.dp, if (focused) Primary else Color.Transparent, RoundedCornerShape(24.dp))
            .focusRequester(req).onFocusChanged { focused = it.isFocused }.focusable()
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Text(text, fontSize = 22.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(sub, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .background(if (focused) White else Surface, RoundedCornerShape(28.dp))
            .border(if (focused) 4.dp else 0.dp, if (focused) Primary else Color.Transparent, RoundedCornerShape(28.dp))
            .focusRequester(req).onFocusChanged { focused = it.isFocused }.focusable()
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Text(text, fontSize = 20.sp, color = if (focused) Color.Black else White, maxLines = 1)
    }
}

@Composable
private fun Rail(tab: Tab, onTab: (Tab) -> Unit) {
    val items = listOf(
        Tab.Home to "Home", Tab.Search to "Search",
        Tab.Library to "Library", Tab.Settings to "Settings"
    )
    Column(
        modifier = Modifier.width(140.dp).fillMaxHeight().background(Bg).padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("▶", fontSize = 28.sp, color = Primary)
        Text("PlayBox", fontSize = 16.sp, color = White)
        Spacer(modifier = Modifier.height(48.dp))
        items.forEach { (t, label) ->
            var focused by remember { mutableStateOf(false) }
            val req = remember { FocusRequester() }
            val active = tab == t
            Column(
                modifier = Modifier
                    .width(116.dp)
                    .background(if (active) SecondaryContainer else Color.Transparent, RoundedCornerShape(28.dp))
                    .border(if (focused) 4.dp else 0.dp, if (focused) Primary else Color.Transparent, RoundedCornerShape(28.dp))
                    .focusRequester(req).onFocusChanged { focused = it.isFocused }.focusable()
                    .clickable { onTab(t) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, fontSize = 20.sp, color = if (active) White else Muted)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("You", fontSize = 18.sp, color = Muted)
    }
}

/* ---------- Home / Search / Library / Settings ---------- */

@Composable
private fun HomeScreen(videos: List<VideoItem>, audio: List<VideoItem>, onOpen: (VideoItem) -> Unit, onFolders: () -> Unit) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val continueWatching = videos.filter { resume.containsKey(it.path.hashCode().toString()) }
    val hero = continueWatching.firstOrNull() ?: videos.firstOrNull()
    var filter by remember { mutableStateOf("All") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(start = 32.dp, top = 32.dp, end = 32.dp)) {
        item {
            Text("${greeting()}", fontSize = 22.sp, color = Primary)
            if (hero != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(hero.name, fontSize = 48.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (hero.durationMs > 0) TimeFormatter.format(hero.durationMs) else "Local file",
                    fontSize = 20.sp, color = Muted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(if (resume.containsKey(hero.path.hashCode().toString())) "Resume" else "Play") { onOpen(hero) }
                    TvButton("Folders / USB") { onFolders() }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text("No videos yet", fontSize = 40.sp, color = White)
                Text("Plug in a USB drive, or copy a video to this TV, and it will show up here.", fontSize = 20.sp, color = Muted)
                Spacer(modifier = Modifier.height(16.dp))
                TvButton("Folders / USB") { onFolders() }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("All", "Videos", "Music").forEach { f ->
                    TvButton(if (filter == f) "● $f" else f) { filter = f }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (continueWatching.isNotEmpty()) {
            item {
                Text("Continue watching", fontSize = 28.sp, color = White)
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continueWatching) { item ->
                        TvTile(item.name, TimeFormatter.format(resume[item.path.hashCode().toString()] ?: 0L)) { onOpen(item) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        item {
            Text("All videos", fontSize = 28.sp, color = White)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (videos.isEmpty() && audio.isEmpty()) {
            item { Text("Nothing here yet. Your local and USB files will appear here.", fontSize = 20.sp, color = Muted) }
        } else {
            val shown = when (filter) {
                "Videos" -> videos
                "Music" -> audio
                else -> videos + audio
            }
            items(shown.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { item ->
                        TvTile(item.name, if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null) { onOpen(item) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SearchScreen(items: List<VideoItem>, search: String, onSearch: (String) -> Unit, onOpen: (VideoItem) -> Unit) {
    val shown = if (search.isBlank()) items else items.filter { it.name.contains(search, ignoreCase = true) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        item {
            Text("Search", fontSize = 36.sp, color = White)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = search, onValueChange = onSearch, label = { Text("Search your files") }, modifier = Modifier.fillMaxWidth(0.5f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("${shown.size} files", fontSize = 18.sp, color = Muted)
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (shown.isEmpty()) {
            item { Text("No matches. Try a shorter word, or check Folders / USB.", fontSize = 20.sp, color = Muted) }
        } else {
            items(shown.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { item -> TvTile(item.name, if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null) { onOpen(item) } }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LibraryScreen(videos: List<VideoItem>, audio: List<VideoItem>, onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("Videos") }
    var folder by remember { mutableStateOf<File?>(null) }
    val roots = remember { MediaLibrary.storageRoots() }
    val (dirs, media) = remember(folder) { val f = folder; if (f == null) emptyList<File>() to emptyList() else MediaLibrary.listFolder(f) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        item {
            Text("Library", fontSize = 36.sp, color = White)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Videos", "Music", "Folders / USB").forEach { m -> TvButton(if (mode == m) "● $m" else m) { mode = m; folder = null } }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        when (mode) {
            "Folders / USB" -> {
                if (folder == null) {
                    items(roots) { root ->
                        Row(modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).clickable { folder = root }.padding(20.dp)) {
                            Text(root.absolutePath, fontSize = 20.sp, color = White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (roots.isEmpty()) item { Text("No storage found. Plug in a USB drive and open this screen again.", fontSize = 20.sp, color = Muted) }
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
                    item { Text("No ${mode.lowercase()} found yet.", fontSize = 20.sp, color = Muted) }
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

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    var skip by remember { mutableStateOf(prefs.getInt("skip_seconds", 10)) }
    var speed by remember { mutableStateOf(prefs.getFloat("default_speed", 1f)) }
    var subsOn by remember { mutableStateOf(prefs.getBoolean("subtitles_on", true)) }

    fun save() {
        prefs.edit().putInt("skip_seconds", skip).putFloat("default_speed", speed).putBoolean("subtitles_on", subsOn).apply()
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        item {
            Text("Settings", fontSize = 36.sp, color = White)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Playback", fontSize = 24.sp, color = White)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Skip: ${skip}s") { skip = if (skip == 10) 30 else 10; save() }
                TvButton("Speed: ${speed}x") { speed = if (speed == 1f) 1.5f else if (speed == 1.5f) 2f else 1f; save() }
                TvButton(if (subsOn) "Subtitles: On" else "Subtitles: Off") { subsOn = !subsOn; save() }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("About", fontSize = 24.sp, color = White)
            Spacer(modifier = Modifier.height(12.dp))
            Text("PlayBox TV 1.0.0 — Batch 1, working video player for Jio TV.", fontSize = 20.sp, color = White)
            Text("Local and USB video and audio. Seek, fast-forward, rewind, speed, subtitles and audio tracks.", fontSize = 20.sp, color = Muted)
            Text("No sign-in. No ads in Batch 1. No Google Play Services.", fontSize = 20.sp, color = Muted)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Batch 2 will add the LibVLC engine for more formats, plus network streams and SMB/NAS.", fontSize = 20.sp, color = Muted)
        }
    }
}

@Composable
private fun DetailsScreen(item: VideoItem, onBack: () -> Unit, onPlay: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val saved = remember { context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE).getLong(item.path.hashCode().toString(), 0L) }
    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        TvButton("Back") { onBack() }
        Spacer(modifier = Modifier.height(24.dp))
        Text(item.name, fontSize = 40.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            listOfNotNull(
                if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                if (item.sizeBytes > 0) "${item.sizeBytes / (1024 * 1024)} MB" else null,
                if (item.isVideo) "Video" else "Audio"
            ).joinToString("  •  "),
            fontSize = 20.sp, color = Muted
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(if (saved > 5_000L) "Resume at ${TimeFormatter.format(saved)}" else "Play") { onPlay(item) }
            TvButton("Play from start") { onPlay(item) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Remote: OK to play, Back to go back.", fontSize = 18.sp, color = Muted)
    }
}

/* ---------- Player: clean full-screen, all functions in one bottom row ---------- */

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(item: VideoItem, queue: List<VideoItem>, onExit: () -> Unit, onPlayNext: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val skipSec = settings.getInt("skip_seconds", 10)
    val skipMs = skipSec * 1_000L
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(settings.getFloat("default_speed", 1f)) }
    var status by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    var subsOn by remember { mutableStateOf(settings.getBoolean("subtitles_on", true)) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) durationMs = duration
                    if (state == Player.STATE_ENDED) ended = true
                }
            })
        }
    }

    DisposableEffect(item) {
        val prefs = context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE)
        val saved = prefs.getLong(item.path.hashCode().toString(), 0L)
        val subtitleFile = try {
            val f = File(item.path)
            if (f.exists() && subsOn) SubtitleFinder.findForVideo(f) else null
        } catch (_: Exception) { null }
        val subtitleConfigs = if (subtitleFile != null) {
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                    .setMimeType(SubtitleFinder.subtitleMimeType(subtitleFile))
                    .setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            )
        } else emptyList()
        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(item.path)).setSubtitleConfigurations(subtitleConfigs).build())
        player.prepare()
        val resumeAt = if (ResumeStore.shouldResume(saved, item.durationMs)) saved else 0L
        if (resumeAt > 0) { player.seekTo(resumeAt); status = "Resumed at ${TimeFormatter.format(resumeAt)}" }
        player.play()
        onDispose {
            try {
                val pos = player.currentPosition
                val dur = player.duration
                if (pos > 5_000L && dur > 0L && pos < dur - 10_000L) prefs.edit().putLong(item.path.hashCode().toString(), pos).apply()
                else prefs.edit().remove(item.path.hashCode().toString()).apply()
            } catch (_: Exception) { }
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (true) {
            try { positionMs = player.currentPosition; durationMs = player.duration } catch (_: Exception) { }
            delay(500)
        }
    }
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && panel == null && !ended) { delay(5000); showControls = false }
    }

    fun seekBy(delta: Long) {
        val target = SeekHelper.clampSeek(positionMs, delta, durationMs)
        player.seekTo(target); positionMs = target; showControls = true
    }
    fun cycleSpeed() {
        val next = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
        speed = next; player.setPlaybackSpeed(next); settings.edit().putFloat("default_speed", next).apply()
    }
    fun cycleAudioTrack() {
        try {
            val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (groups.isEmpty()) { status = "No audio tracks found"; return }
            val group = groups.first()
            if (group.length <= 1) { status = "Only 1 audio track"; return }
            val current = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
            val next = (current + 1) % group.length
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, next)).build()
            status = "Audio track ${next + 1} of ${group.length}"
        } catch (e: Exception) { status = "Audio switch failed" }
    }

    val nextItem = queue.getOrNull(queue.indexOf(item) + 1)

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { seekBy(-skipMs); true }
                    Key.DirectionRight -> { seekBy(skipMs); true }
                    Key.MediaFastForward -> { seekBy(30_000L); true }
                    Key.MediaRewind -> { seekBy(-30_000L); true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (player.isPlaying) player.pause() else player.play(); showControls = true; true
                    }
                    Key.DirectionCenter, Key.Enter -> { showControls = !showControls; true }
                    Key.Back -> { if (panel != null) panel = null else onExit(); true }
                    else -> false
                }
            }
            .clickable { showControls = !showControls }
    ) {
        AndroidView(factory = { ctx -> SurfaceView(ctx).also { player.setVideoSurfaceView(it) } }, modifier = Modifier.fillMaxSize())

        if (showControls && !ended) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xCC121318), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(TimeFormatter.format(positionMs), fontSize = 20.sp, color = White)
                    Text(
                        if (durationMs > 0) "-${TimeFormatter.format(durationMs - positionMs)}  ·  ${TimeFormatter.format(durationMs)}" else "",
                        fontSize = 20.sp, color = Muted
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                WaveProgress(progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(if (isPlaying) "Pause" else "Play") { if (player.isPlaying) player.pause() else player.play() }
                    TvButton("-${skipSec}s") { seekBy(-skipMs) }
                    TvButton("+${skipSec}s") { seekBy(skipMs) }
                    TvButton("${speed}x") { cycleSpeed() }
                    TvButton("Subtitles") { panel = "subs" }
                    TvButton("Audio") { cycleAudioTrack() }
                    TvButton("Quality") { panel = "quality" }
                    TvButton("Exit") { onExit() }
                }
                if (status.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status, fontSize = 16.sp, color = Muted)
                }
            }
        }

        if (panel != null) {
            val options = when (panel) {
                "subs" -> listOf("Subtitles: On", "Subtitles: Off")
                "quality" -> listOf("Auto", "Original quality")
                else -> emptyList()
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight()
                    .background(SurfaceHigh, RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)).padding(24.dp)
            ) {
                Text(if (panel == "subs") "Subtitles" else "Quality", fontSize = 28.sp, color = White)
                Spacer(modifier = Modifier.height(16.dp))
                options.forEach { opt ->
                    TvButton(opt) {
                        if (panel == "subs") { subsOn = opt.contains("On"); settings.edit().putBoolean("subtitles_on", subsOn).apply() }
                        panel = null
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                TvButton("Close") { panel = null }
            }
        }

        if (ended) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(SurfaceHigh, RoundedCornerShape(32.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (nextItem != null) "Up next" else "You finished", fontSize = 22.sp, color = Primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(nextItem?.name ?: item.name, fontSize = 28.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (nextItem != null) TvButton("Play now") { onPlayNext(nextItem) }
                    TvButton("Back") { onExit() }
                }
            }
        }
    }
}

@Composable
private fun WaveProgress(progress: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        val w = size.width
        val h = size.height
        val playedW = w * progress
        drawLine(Color(0xFF444444), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 10f)
        if (playedW > 0) {
            val path = Path()
            path.moveTo(0f, h / 2)
            var x = 0f
            var up = true
            while (x < playedW) {
                val nx = (x + 24f).coerceAtMost(playedW)
                path.quadraticTo(x + 12f, if (up) h / 2 - 10f else h / 2 + 10f, nx, h / 2)
                x = nx; up = !up
            }
            drawPath(path, Primary, style = Stroke(width = 9f))
            drawCircle(Primary, radius = 22f, center = Offset(playedW, h / 2))
        }
    }
}
