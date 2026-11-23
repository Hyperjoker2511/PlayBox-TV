package com.playbox.tv

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1.4.0: the crash-note handler goes in before anything
        // else can throw — it is the only witness a native death
        // leaves behind (see CrashNote.kt).
        CrashLog.install(this)
        handleViewIntent(intent)
        // Batch 2 (DLNA): keep the renderer discoverable while the app
        // is installed — the service itself stays alive in the background.
        startService(Intent(this, CastService::class.java))
        // The permission request itself lives in PlayBoxApp (rememberLauncherForActivityResult),
        // behind a plain-words rationale shown on first launch — no silent system prompt.
        setContent { PlayBoxApp() }
    }

    // Patch 7 (PiP): leaving the app with the Home key while a video
    // is playing shrinks it into a picture-in-picture window instead
    // of stopping it. pipPlaying is set from PlayBoxApp.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipPlaying.value) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    // Batch 2 ("Open with PlayBox"): a file shared to us via ACTION_VIEW
    // lands here; PlayBoxApp picks it up and plays it.
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { viewUri.value = it }
        }
    }
}

// Batch 2: the URI of a file another app asked us to play (null normally).
private val viewUri = mutableStateOf<Uri?>(null)

// Patch 7 (PiP): true while a video is on screen — MainActivity's
// onUserLeaveHint reads this to decide whether to enter PiP.
private val pipPlaying = mutableStateOf(false)

private val permissionTick = mutableStateOf(0)

private fun mediaPermissionDenied(context: Context): Boolean {
    val perms = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return perms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
}

@Composable
private fun PermissionRationaleScreen(onAllow: () -> Unit, onNotNow: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("PlayBox needs to see your videos", fontSize = 36.sp, color = White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Allow media access so the Videos and Music tabs can list your files. " +
                "PlayBox only reads file names and lengths — nothing leaves your TV. " +
                "You can still browse folders and USB without it.",
            fontSize = 22.sp, color = Muted
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton("Allow access", onClick = onAllow)
            TvButton("Not now", onClick = onNotNow)
        }
    }
}

/** Tab order for the slide direction: Home 0, Search 1, Library 2,
 * Settings 3. Positive = forward (the new screen slides in from the
 * right), negative = back, zero = same tab. (unit-tested) */
internal fun tabSlideDirection(from: Tab, to: Tab): Int {
    val order = listOf(Tab.Home, Tab.Search, Tab.Library, Tab.Settings)
    return (order.indexOf(to) - order.indexOf(from)).coerceIn(-1, 1)
}

@Composable
private fun PlayBoxApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Home) }
    var details by remember { mutableStateOf<VideoItem?>(null) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var playList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    // Patch 3: videos the user queued from a Details page — merged into
    // the play list when playback starts, then cleared.
    var queued by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    // Batch 1.3 (B3): the list the video was opened from, so "Up next"
    // follows the folder/USB list instead of a random MediaStore video.
    var detailList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    // Batch 1.3 (B1): "Play from start" passes through to the player.
    var playFromStart by remember { mutableStateOf(false) }
    // Batch 2 (DLNA): a cast video plays at 1x — a sender expects normal
    // speed, not the user's default. Null = use the saved default.
    var playStartSpeed by remember { mutableStateOf<Float?>(null) }
    var search by remember { mutableStateOf("") }

    // Batch 1.3 (B4): Library navigation state lives here, not inside
    // LibraryScreen — opening a video no longer resets mode/folder/scroll.
    var libraryMode by remember { mutableStateOf("Videos") }
    var libraryFolder by remember { mutableStateOf<File?>(null) }
    var librarySafFolder by remember { mutableStateOf<Uri?>(null) }
    val libraryListState = rememberLazyListState()
    // Batch 2: the Network browse state lives here too.
    var networkServer by remember { mutableStateOf<SmbServer?>(null) }
    var networkPath by remember { mutableStateOf<String?>(null) }
    // Patch 7: the FTP/SFTP browse state, same pattern as SMB.
    var ftpServer by remember { mutableStateOf<FtpServer?>(null) }
    var ftpPath by remember { mutableStateOf<String?>(null) }

    // Batch 1.3 (B8): reload the MediaStore lists when media changes
    // (USB plugged in) or when the user presses Refresh.
    var refreshNonce by remember { mutableStateOf(0) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refreshNonce++ }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    // Batch 2 ("Open with PlayBox"): play the file another app shared
    // with us, then clear the request so it does not replay on recompose.
    val pendingView = viewUri.value
    LaunchedEffect(pendingView) {
        if (pendingView != null) {
            MediaLibrary.videoItemFromUri(context, pendingView)?.let { item ->
                playing = item; playList = listOf(item); playFromStart = false; playStartSpeed = null
            }
            viewUri.value = null
        }
    }

    // Batch 2 (DLNA): a video cast from a phone opens the player for it,
    // at normal speed. Cleared after handling, like viewUri above.
    val pendingCast = CastInbox.item.value
    LaunchedEffect(pendingCast) {
        if (pendingCast != null) {
            playing = pendingCast; playList = listOf(pendingCast); playFromStart = false; playStartSpeed = 1f
            CastInbox.item.value = null
        }
    }

    // Patch 2: "Recently played" — recorded whenever a video starts,
    // whichever way it was opened (Home, Details, cast, share).
    LaunchedEffect(playing) {
        playing?.let { LibraryStores.recordRecent(context, it.path) }
    }

    // Patch 7: the engine override — the gear's "Switch engine" for
    // one file (path + engine). It only applies to that path, so
    // opening another file goes back to the default engine.
    var engineOverride by remember { mutableStateOf<Pair<String, PlayerEngine>?>(null) }
    // 1.4.5 Patch B: the path an automatic engine fallback already
    // fired for. A fallback runs once per file — Media3 failing
    // switches to VLC, VLC failing switches to Media3, but neither
    // fires twice for the same file, so the two engines cannot
    // ping-pong on a file neither can play. A new file resets it
    // (the path differs), and a manual gear switch is unaffected.
    var fallbackDoneFor by remember { mutableStateOf<String?>(null) }
    // 1.4.2 (Nova, now official): the whole-app design — Nova (from
    // scratch, the default) or Qwen (the kept option). Kept in state
    // so switching it in Settings re-renders the scaffold
    // immediately; saved to prefs so it survives a restart.
    var appDesign by remember { mutableStateOf(appDesign(context)) }
    // Patch 7 (PiP): MainActivity's onUserLeaveHint reads this.
    LaunchedEffect(playing) { pipPlaying.value = playing != null }
    val activity = LocalContext.current as? Activity
    val enterPip: () -> Unit = {
        runCatching {
            activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    val tick by permissionTick
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var audio by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var libraryLoaded by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    // First launch: explain why before asking, instead of a bare system prompt.
    val permPrefs = remember { context.getSharedPreferences("playbox_permission", Context.MODE_PRIVATE) }
    var showRationale by remember {
        mutableStateOf(!permPrefs.getBoolean("asked", false) && mediaPermissionDenied(context))
    }
    val requestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permPrefs.edit().putBoolean("asked", true).apply()
        showRationale = false
        permissionTick.value++
    }
    LaunchedEffect(tick, refreshNonce) {
        // Debounce: a USB mount fires several observer callbacks at once;
        // the delay collapses them into one reload.
        delay(400)
        permissionDenied = mediaPermissionDenied(context)
        videos = withContext(Dispatchers.IO) { MediaLibrary.listVideosFromMediaStore(context) }
        audio = withContext(Dispatchers.IO) { MediaLibrary.listAudioFromMediaStore(context) }
        libraryLoaded = true
    }
    val all = remember(videos, audio) { videos + audio }

    // 1.4.0: the crash gate — if the last run died (a saved Java
    // note, or a stale VLC marker with no note = a native death),
    // the note screen is the first thing shown. Read once per cold
    // start; Continue clears the records and lets the app through.
    val crashNote = remember { CrashLog.note(context) }
    val staleVlcDeath = remember { hasStaleVlcAttempt(context) }
    var crashGateDone by remember { mutableStateOf(false) }
    val showCrashGate = !crashGateDone && (crashNote != null || staleVlcDeath)

    MaterialTheme(colorScheme = darkColorScheme(primary = Primary, surface = Bg, background = Bg)) {
        Box(modifier = Modifier.fillMaxSize().background(Bg)) {
            if (showCrashGate) {
                CrashNoteScreen(
                    note = crashNote,
                    lastStep = CrashLog.lastStep(context),
                    onContinue = {
                        CrashLog.clear(context)
                        clearVlcAttempt(context)
                        crashGateDone = true
                    }
                )
            } else if (showRationale) {
                PermissionRationaleScreen(
                    onAllow = {
                        val perms = if (Build.VERSION.SDK_INT >= 33) {
                            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        requestLauncher.launch(perms)
                    },
                    onNotNow = {
                        permPrefs.edit().putBoolean("asked", true).apply()
                        showRationale = false
                    }
                )
            } else if (playing != null) {
                // Patch 1.3.2 (crash guard): if the last VLC open
                // killed the process, the stale marker is still set —
                // force Media3 this time instead of crashing again.
                // The gear's explicit engine choice still wins.
                val vlcAvailable = !hasStaleVlcAttempt(context)
                // 1.4.0: Media3 is the default again (see engineFor);
                // the gear's override wins, then the file's own
                // Details-page choice, then the Settings default.
                // 1.4.6 Patch D: HDR files go to VLC for tone
                // mapping — detected once per path (cached) from the
                // file header, so a local file costs one fast read.
                val isHdr = remember(playing!!.path) { HdrDetect.isHdr(playing!!.path) }
                val engine = engineFor(
                    playing!!,
                    engineOverride?.takeIf { it.first == playing!!.path }?.second
                        ?: chosenEngine(context, playing!!.path),
                    vlcAvailable,
                    defaultEnginePref(context),
                    hdr = isHdr
                )
                // 1.4.0 (crash fix): the breadcrumb and the VLC
                // attempt marker are written HERE — before the
                // player screen composes and the engine is created.
                // The 1.3.2 marker was written in a later setup
                // step, so a death during engine creation left no
                // marker and the guard never fired.
                LaunchedEffect(playing!!.path, engine) {
                    CrashLog.step(context, "engine chosen: $engine for ${playing!!.name}")
                    if (engine == PlayerEngine.VLC) markVlcAttempt(context)
                }
                if (engine == PlayerEngine.VLC) {
                    VlcPlayerScreen(
                        item = playing!!,
                        queue = playList.ifEmpty { all },
                        fromStart = playFromStart,
                        startSpeed = playStartSpeed,
                        onExit = { playing = null; playFromStart = false; playStartSpeed = null },
                        onPlayNext = { next -> playing = next; playFromStart = false; playStartSpeed = null },
                        onSwitchEngine = { engineOverride = playing!!.path to PlayerEngine.MEDIA3 },
                        onEnterPip = enterPip,
                        // Patch 1.3.1: the VLC engine could not start —
                        // open the same file on Media3 instead of crashing.
                        // 1.4.5 Patch B: guarded — fires once per file,
                        // so it cannot loop back to Media3 after a
                        // Media3→VLC fallback for the same file.
                        onEngineFailed = {
                            if (fallbackDoneFor != playing!!.path) {
                                fallbackDoneFor = playing!!.path
                                engineOverride = playing!!.path to PlayerEngine.MEDIA3
                            }
                        }
                    )
                } else {
                    PlayerScreen(
                        item = playing!!,
                        queue = playList.ifEmpty { all },
                        fromStart = playFromStart,
                        startSpeed = playStartSpeed,
                        onExit = { playing = null; playFromStart = false; playStartSpeed = null },
                        onPlayNext = { next -> playing = next; playFromStart = false; playStartSpeed = null },
                        onSwitchEngine = { engineOverride = playing!!.path to PlayerEngine.VLC },
                        onEnterPip = enterPip,
                        // 1.4.5 Patch B: Media3 failed to play this
                        // file — retry it on LibVLC instead of
                        // showing an error. Guarded to once per file,
                        // so the two engines cannot ping-pong.
                        onEngineFailed = {
                            if (fallbackDoneFor != playing!!.path) {
                                fallbackDoneFor = playing!!.path
                                engineOverride = playing!!.path to PlayerEngine.VLC
                            }
                        }
                    )
                }
            } else if (details != null) {
                // 1.4.2 (Nova, now official): the Details screen
                // follows the app design — Nova's stacked actions and
                // list rows, Aura's backdrop + More Like This row, or
                // Qwen's glass chips and tile strip.
                if (appDesign == AppDesign.NOVA) {
                    NovaDetailsScreen(
                        item = details!!,
                        list = detailList.ifEmpty { all },
                        onBack = { details = null },
                        onPlay = { item, fromStart ->
                            playing = item
                            playList = mergeQueue(detailList.ifEmpty { all }, queued)
                            queued = emptyList()
                            playFromStart = fromStart
                            details = null
                        },
                        onQueue = { item -> queued = mergeQueue(queued, listOf(item)) },
                        onOpen = { item -> details = item }
                    )
                } else if (appDesign == AppDesign.AURA) {
                    AuraDetailsScreen(
                        item = details!!,
                        list = detailList.ifEmpty { all },
                        onBack = { details = null },
                        onPlay = { item, fromStart ->
                            playing = item
                            playList = mergeQueue(detailList.ifEmpty { all }, queued)
                            queued = emptyList()
                            playFromStart = fromStart
                            details = null
                        },
                        onQueue = { item -> queued = mergeQueue(queued, listOf(item)) },
                        onOpen = { item -> details = item }
                    )
                } else {
                    DetailsScreen(
                        item = details!!,
                        list = detailList.ifEmpty { all },
                        onBack = { details = null },
                        onPlay = { item, fromStart ->
                            playing = item
                            playList = mergeQueue(detailList.ifEmpty { all }, queued)
                            queued = emptyList()
                            playFromStart = fromStart
                            details = null
                        },
                        onQueue = { item -> queued = mergeQueue(queued, listOf(item)) },
                        onOpen = { item -> details = item }
                    )
                }
            } else if (appDesign == AppDesign.NOVA) {
                // 1.4.2 (Nova, now official): the rail on wide screens
                // (TV, phone landscape), the tab strip on narrow ones
                // (phone portrait). The content switches with a fade
                // + a small rise (Nova motion), not Qwen's slide.
                // The Player button resumes the most recently watched
                // video, like Qwen's Player pill.
                val onPlayer: () -> Unit = {
                    val times = resumeTimes(context)
                    val recent = all.maxByOrNull { times[ResumeStore.keyFor(it.path)] ?: 0L }
                    val item = recent ?: all.firstOrNull()
                    if (item != null) {
                        playing = item; playList = all; playFromStart = false; playStartSpeed = null
                    }
                }
                val novaContent: @Composable (Tab) -> Unit = { currentTab ->
                    when (currentTab) {
                        Tab.Home -> NovaHomeScreen(
                            videos = videos, audio = audio,
                            loading = !libraryLoaded, permissionDenied = permissionDenied,
                            onOpen = { item, list -> details = item; detailList = list },
                            onPlay = { item, list -> playing = item; playList = list; playFromStart = false; playStartSpeed = null },
                            onLibrary = { mode -> libraryMode = mode; tab = Tab.Library },
                            onFolders = { tab = Tab.Library },
                            onRefresh = { refreshNonce++ }
                        )
                        Tab.Search -> NovaSearchScreen(
                            items = all, search = search, onSearch = { search = it },
                            onOpen = { item, list -> details = item; detailList = list }
                        )
                        // Explore is Aura's browse view (1.4.5). Nova has
                        // no Explore tab, so it falls back to Library if
                        // the design is switched while on Explore.
                        Tab.Explore -> NovaLibraryScreen(
                            videos = videos, audio = audio, loading = !libraryLoaded,
                            mode = libraryMode, onMode = { libraryMode = it },
                            folder = libraryFolder, onFolder = { libraryFolder = it },
                            safFolder = librarySafFolder, onSafFolder = { librarySafFolder = it },
                            listState = libraryListState,
                            networkServer = networkServer, onNetworkServer = { networkServer = it },
                            networkPath = networkPath, onNetworkPath = { networkPath = it },
                            ftpServer = ftpServer, onFtpServer = { ftpServer = it },
                            ftpPath = ftpPath, onFtpPath = { ftpPath = it },
                            onOpen = { item, list -> details = item; detailList = list },
                            onRefresh = { refreshNonce++ }
                        )
                        Tab.Library -> NovaLibraryScreen(
                            videos = videos, audio = audio, loading = !libraryLoaded,
                            mode = libraryMode, onMode = { libraryMode = it },
                            folder = libraryFolder, onFolder = { libraryFolder = it },
                            safFolder = librarySafFolder, onSafFolder = { librarySafFolder = it },
                            listState = libraryListState,
                            networkServer = networkServer, onNetworkServer = { networkServer = it },
                            networkPath = networkPath, onNetworkPath = { networkPath = it },
                            ftpServer = ftpServer, onFtpServer = { ftpServer = it },
                            ftpPath = ftpPath, onFtpPath = { ftpPath = it },
                            onOpen = { item, list -> details = item; detailList = list },
                            onRefresh = { refreshNonce++ }
                        )
                        Tab.Settings -> NovaSettingsScreen(
                            currentDesign = appDesign,
                            onDesignChange = { appDesign = it; saveAppDesign(context, it) }
                        )
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(NovaBg)) {
                    if (maxWidth >= 700.dp) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            NovaRail(tab = tab, onTab = { tab = it }, onPlayer = onPlayer)
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith
                                            (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.98f))
                                    },
                                    label = "novaTabSwitch"
                                ) { currentTab -> novaContent(currentTab) }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            NovaTabStrip(tab = tab, onTab = { tab = it }, onPlayer = onPlayer)
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith
                                            (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.98f))
                                    },
                                    label = "novaTabSwitch"
                                ) { currentTab -> novaContent(currentTab) }
                            }
                        }
                    }
                }
            } else if (appDesign == AppDesign.AURA) {
                // 1.4.5 Patch 2 (Aura, the third design): the expanding
                // left rail on wide screens (TV, phone landscape), the
                // bottom nav on narrow ones (phone portrait). Five
                // rail items — Home / Search / Explore / Library /
                // Settings — the Aura file's navigation. The content
                // switches with a fade + a small rise (Aura motion),
                // not Qwen's slide. The Player button resumes the
                // most recently watched video, like Nova and Qwen.
                val onPlayer: () -> Unit = {
                    val times = resumeTimes(context)
                    val recent = all.maxByOrNull { times[ResumeStore.keyFor(it.path)] ?: 0L }
                    val item = recent ?: all.firstOrNull()
                    if (item != null) {
                        playing = item; playList = all; playFromStart = false; playStartSpeed = null
                    }
                }
                val auraContent: @Composable (Tab) -> Unit = { currentTab ->
                    when (currentTab) {
                        Tab.Home -> AuraHomeScreen(
                            videos = videos, audio = audio,
                            loading = !libraryLoaded, permissionDenied = permissionDenied,
                            onOpen = { item, list -> details = item; detailList = list },
                            onPlay = { item, list -> playing = item; playList = list; playFromStart = false; playStartSpeed = null },
                            onLibrary = { mode -> libraryMode = mode; tab = Tab.Library },
                            onFolders = { tab = Tab.Library },
                            onRefresh = { refreshNonce++ }
                        )
                        Tab.Search -> AuraSearchScreen(
                            items = all, search = search, onSearch = { search = it },
                            onOpen = { item, list -> details = item; detailList = list }
                        )
                        Tab.Explore -> AuraExploreScreen(
                            videos = videos, audio = audio,
                            onOpen = { item, list -> details = item; detailList = list }
                        )
                        Tab.Library -> AuraLibraryScreen(
                            videos = videos, audio = audio, loading = !libraryLoaded,
                            mode = libraryMode, onMode = { libraryMode = it },
                            folder = libraryFolder, onFolder = { libraryFolder = it },
                            safFolder = librarySafFolder, onSafFolder = { librarySafFolder = it },
                            listState = libraryListState,
                            networkServer = networkServer, onNetworkServer = { networkServer = it },
                            networkPath = networkPath, onNetworkPath = { networkPath = it },
                            ftpServer = ftpServer, onFtpServer = { ftpServer = it },
                            ftpPath = ftpPath, onFtpPath = { ftpPath = it },
                            onOpen = { item, list -> details = item; detailList = list },
                            onRefresh = { refreshNonce++ }
                        )
                        Tab.Settings -> AuraSettingsScreen(
                            currentDesign = appDesign,
                            onDesignChange = { appDesign = it; saveAppDesign(context, it) }
                        )
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AuraSurface)) {
                    if (maxWidth >= 700.dp) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            AuraRail(tab = tab, onTab = { tab = it }, onPlayer = onPlayer)
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith
                                            (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.98f))
                                    },
                                    label = "auraTabSwitch"
                                ) { currentTab -> auraContent(currentTab) }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith
                                            (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.98f))
                                    },
                                    label = "auraTabSwitch"
                                ) { currentTab -> auraContent(currentTab) }
                            }
                            AuraBottomNav(tab = tab, onTab = { tab = it })
                        }
                    }
                }
            } else {
                // Batch 3 (Qwen): one ambient glow layer at the top.
                // FIX (2026-11-23): the glow used to be a 320.dp box in
                // this Column — on a phone in landscape (~390dp tall) it
                // ate the whole screen and the content got zero height, so
                // every tab looked blank with the nav at the bottom. It is
                // now a background overlay that takes no layout space,
                // sized as a fraction of the screen so it scales from a
                // 32-inch TV down to a phone.
                // 1.4.3 (enhanced): on wide screens (TV) the top pill bar
                // is replaced by the Qwen left rail (QwenRail); the phone
                // keeps the pill bar. The Player action and the tab
                // content are shared by both layouts.
                val qwenPlayer: () -> Unit = {
                    val times = resumeTimes(context)
                    val recent = all.maxByOrNull { times[ResumeStore.keyFor(it.path)] ?: 0L }
                    val item = recent ?: all.firstOrNull()
                    if (item != null) {
                        playing = item; playList = all; playFromStart = false; playStartSpeed = null
                    }
                }
                val qwenContent: @Composable () -> Unit = {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                // 1.4.0 (Nocturne TV motion): the tab
                                // switch rides the shared Soft spring —
                                // direction-aware slide + fade + a
                                // small scale settle (in from 1.02,
                                // out to 0.98). Springs follow the
                                // velocity, so a second tab press
                                // mid-flight redirects the motion
                                // instead of queueing. Reduce motion
                                // swaps it for a plain fade.
                                if (isReduceMotion(context)) {
                                    fadeIn(Motion.soft()) togetherWith fadeOut(Motion.soft())
                                } else {
                                    val dir = tabSlideDirection(initialState, targetState)
                                    val enter = slideInHorizontally(Motion.soft()) { if (dir >= 0) it / 4 else -it / 4 } +
                                        fadeIn(Motion.soft()) +
                                        scaleIn(Motion.soft(), initialScale = 1.02f)
                                    val exit = slideOutHorizontally(Motion.soft()) { if (dir >= 0) -it / 4 else it / 4 } +
                                        fadeOut(Motion.soft()) +
                                        scaleOut(Motion.soft(), targetScale = 0.98f)
                                    enter togetherWith exit
                                }
                            },
                            label = "tabSwitch"
                        ) { currentTab ->
                        when (currentTab) {
                            Tab.Home -> HomeScreen(
                                videos = videos, audio = audio,
                                loading = !libraryLoaded, permissionDenied = permissionDenied,
                                onOpen = { item, list -> details = item; detailList = list },
                                onPlay = { item, list -> playing = item; playList = list; playFromStart = false; playStartSpeed = null },
                                onLibrary = { mode -> libraryMode = mode; tab = Tab.Library },
                                onFolders = { tab = Tab.Library },
                                onRefresh = { refreshNonce++ }
                            )
                            Tab.Search -> SearchScreen(
                                items = all, search = search, onSearch = { search = it },
                                onOpen = { item, list -> details = item; detailList = list }
                            )
                            // Explore is Aura's browse view (1.4.5). Qwen
                            // has no Explore tab, so it falls back to
                            // Library if the design is switched while on
                            // Explore.
                            Tab.Explore -> LibraryScreen(
                                videos = videos, audio = audio, loading = !libraryLoaded,
                                mode = libraryMode, onMode = { libraryMode = it },
                                folder = libraryFolder, onFolder = { libraryFolder = it },
                                safFolder = librarySafFolder, onSafFolder = { librarySafFolder = it },
                                listState = libraryListState,
                                networkServer = networkServer, onNetworkServer = { networkServer = it },
                                networkPath = networkPath, onNetworkPath = { networkPath = it },
                                ftpServer = ftpServer, onFtpServer = { ftpServer = it },
                                ftpPath = ftpPath, onFtpPath = { ftpPath = it },
                                onOpen = { item, list -> details = item; detailList = list },
                                onRefresh = { refreshNonce++ }
                            )
                            Tab.Library -> LibraryScreen(
                                videos = videos, audio = audio, loading = !libraryLoaded,
                                mode = libraryMode, onMode = { libraryMode = it },
                                folder = libraryFolder, onFolder = { libraryFolder = it },
                                safFolder = librarySafFolder, onSafFolder = { librarySafFolder = it },
                                listState = libraryListState,
                                networkServer = networkServer, onNetworkServer = { networkServer = it },
                                networkPath = networkPath, onNetworkPath = { networkPath = it },
                                ftpServer = ftpServer, onFtpServer = { ftpServer = it },
                                ftpPath = ftpPath, onFtpPath = { ftpPath = it },
                                onOpen = { item, list -> details = item; detailList = list },
                                onRefresh = { refreshNonce++ }
                            )
                            Tab.Settings -> SettingsScreen(
                                currentDesign = appDesign,
                                onDesignChange = { appDesign = it; saveAppDesign(context, it) }
                            )
                        }
                        }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 900.dp
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.35f)
                                .align(Alignment.TopCenter).background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x298B5CF6), Color.Transparent)
                                    )
                                )
                        ) {}
                        if (wide) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                QwenRail(tab = tab, onTab = { tab = it }, onPlayer = qwenPlayer)
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 32.dp, vertical = 24.dp)) {
                                    qwenContent()
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TopNav(
                                    tab = tab, onTab = { tab = it },
                                    onPlayer = qwenPlayer,
                                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp)
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
                                    qwenContent()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
