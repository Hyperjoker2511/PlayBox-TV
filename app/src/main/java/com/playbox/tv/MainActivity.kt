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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
        // Patch 4: the DLNA renderer starts with the app and stops
        // when it closes (unless "Keep discoverable when closed" is
        // on in Settings). It no longer stays alive in the background
        // after the app is gone — that was the "active 9 hr" bug.
        // M25 (2.0.1): only start it when the "Cast receiver"
        // switch is on (Settings); off means no receiver at all.
        if (castReceiverOn(this)) {
            startService(Intent(this, CastService::class.java))
        }
        // The permission request itself lives in PlayBoxApp (rememberLauncherForActivityResult),
        // behind a plain-words rationale shown on first launch — no silent system prompt.
        // P1 (one design system): one font across the whole app —
        // Roboto Flex, via LocalTextStyle, so every Text that
        // does not name its own font uses AppFont.
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = AppFont)) {
                PlayBoxApp()
            }
        }
    }

    // 2.0.1 (C2): NO automatic PiP on Home. Patch 7 entered PiP
    // whenever anything was playing, but the PiP window has no
    // Pause/Stop controls, and entering PiP fires ON_PAUSE (not
    // ON_STOP) so the players' ON_STOP pause never ran — the video
    // kept playing in an unstoppable mini window. Now Home just
    // backgrounds the app: the Activity reaches ON_STOP and the
    // players pause video there (music keeps playing by design).
    // Manual PiP from the player's PiP button (enterPip) still works.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
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
        Text("Zeph needs to see your videos", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Allow media access so the Videos and Music tabs can list your files. " +
                "Zeph only reads file names and lengths — nothing leaves your TV. " +
                "You can still browse folders and USB without it.",
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            M3TvButton("Allow access", onClick = onAllow)
            M3TvButton("Not now", onClick = onNotNow)
        }
    }
}

/** Tab order for the slide direction (2.0.0 rail order): Home 0,
 * Video 1, Audio 2, Browse 3, Playlists 4, Settings 5, Search 6.
 * Positive = forward (the new screen slides in from the right),
 * negative = back, zero = same tab. (unit-tested) */
internal fun tabSlideDirection(from: Tab, to: Tab): Int {
    val order = listOf(Tab.Home, Tab.Video, Tab.Audio, Tab.Browse, Tab.Playlists, Tab.Settings, Tab.Search)
    return (order.indexOf(to) - order.indexOf(from)).coerceIn(-1, 1)
}

@Composable
private fun PlayBoxApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Home) }
    var details by remember { mutableStateOf<VideoItem?>(null) }
    // M23 (2.0.1): return focus to the rail when Details closes.
    // Closing Details unmounted the focused Play FAB, leaving
    // focus nowhere — the remote did nothing until the user
    // somehow re-focused. This only fires on the close
    // transition (was open, now closed), not on first launch.
    val railTabFocus = remember { FocusRequester() }
    var prevDetails by remember { mutableStateOf<VideoItem?>(null) }
    LaunchedEffect(details) {
        if (prevDetails != null && details == null) {
            runCatching { railTabFocus.requestFocus() }
        }
        prevDetails = details
    }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var playList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    // Patch 5: the full Now Playing screen is showing (audio only).
    // When false and audio is playing, the MiniPlayer shows over the
    // tabs instead. Video ignores this — the video player always
    // shows full-screen.
    var nowPlayingOpen by remember { mutableStateOf(false) }
    // Patch 5: the sleep timer, shared by Now Playing and the
    // MiniPlayer so collapsing does not lose a set timer.
    val audioSleep = remember { AudioSleepState() }
    // 2.0.0 (fix pass): the player screens report their playing
    // state up here, so the Audio screen's EqBars show the real
    // paused state instead of animating forever. The shuffle
    // mode is a persistent setting (store.tsx: shuffle is a
    // boolean + setShuffle); the queue honouring lands in
    // Patch 5's Now Playing, which also reads this state.
    var playerIsPlaying by remember { mutableStateOf(false) }
    var shuffleOn by remember { mutableStateOf(false) }
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
    // Patch 4 (exit confirm): Back on Home asks "Exit Zeph Player?"
    // instead of leaving the app half-alive in the background.
    var showExitDialog by remember { mutableStateOf(false) }
    // Patch 4 (Jio): which legal screen is open from Settings —
    // null = none, "privacy" or "licences".
    var settingsPage by remember { mutableStateOf<String?>(null) }

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
                playing = item; playList = listOf(item); playFromStart = false; playStartSpeed = null; nowPlayingOpen = true
            }
            viewUri.value = null
        }
    }

    // Batch 2 (DLNA): a video cast from a phone opens the player for it,
    // at normal speed. Cleared after handling, like viewUri above.
    val pendingCast = CastInbox.item.value
    LaunchedEffect(pendingCast) {
        if (pendingCast != null) {
            playing = pendingCast; playList = listOf(pendingCast); playFromStart = false; playStartSpeed = 1f; nowPlayingOpen = true
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
    // v7: the rail is docked-only (the design has no floating mode)
    // and its labels default to ALWAYS (the design always draws it
    // expanded). Kept in state so changing it in Settings re-renders
    // the rail immediately; saved to prefs so it survives a restart.
    var railLabelMode by remember { mutableStateOf(railLabelMode(context)) }
    // v7 (Sources "Add a source"): the Network screen's add-server
    // form, opened from Sources. A nonce — each tap opens the form
    // again even if it was already shown once.
    var networkAddFormRequest by remember { mutableStateOf(0) }
    // v7 (Home SearchBar mic): the mic on Home opens Search AND
    // starts voice input there. A nonce — each tap starts a new
    // listen, even if Search is already open.
    var voiceSearchRequest by remember { mutableStateOf(0) }
    // v5 (1.9.0): the theme choice — preset + custom seed. Kept in
    // state so changing it in Settings re-renders the whole app at
    // once; saved to prefs so it survives a restart.
    var theme by remember { mutableStateOf(themePreset(context)) }
    var themeSeedHex by remember { mutableStateOf(themeSeed(context)) }
    // 2.0.0 fix pass: dark/light mode (the design's Settings has a
    // Dark/Light segmented control; the switch itself is wired in
    // Patch 8's Settings rebuild — this state + the light schemes
    // are here now so the mode exists).
    var themeDarkMode by remember { mutableStateOf(themeDark(context)) }
    // Patch 7 (PiP): MainActivity's onUserLeaveHint reads this.
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
    // 2.0.9 (review C1+D5): when the guard fired, name the suspect
    // — the file and decoding mode from the last VLC attempt — so
    // the crash note can tell the owner exactly what died.
    val vlcDeath = remember { if (staleVlcDeath) lastVlcAttempt(context) else null }
    var crashGateDone by remember { mutableStateOf(false) }
    val showCrashGate = !crashGateDone && (crashNote != null || staleVlcDeath)

    // Patch 4 (clean exit): Exit means fully exit — stop the DLNA
    // renderer and the playback service, then finish the activity.
    // No background, no restart. When "Keep discoverable when
    // closed" is on, the renderer is left running on purpose.
    val exitApp: () -> Unit = {
        if (!keepDiscoverable(context)) {
            context.stopService(Intent(context, CastService::class.java))
        }
        context.stopService(Intent(context, PlaybackService::class.java))
        activity?.finish()
    }
    // Patch 5: the engine for the playing item, computed once here
    // so the routing (video player vs Now Playing) and the player
    // both use the same value. Audio on Media3 opens Now Playing;
    // audio that needs VLC (FTP/RTSP/NFS/unsupported/HDR) still
    // opens the video player, as before.
    // `playing` is a delegated property, so `playing != null` does
    // NOT smart-cast it — capture it in a plain val first, and use
    // that val wherever the code reads a member after a null check.
    val playingItem = playing
    val playingVlcAvailable = !hasStaleVlcAttempt(context)
    val playingHdr = remember(playingItem?.path) { playingItem?.let { HdrDetect.isHdr(it.path) } ?: false }
    val playingEngine = if (playingItem != null) engineFor(
        playingItem,
        engineOverride?.takeIf { it.first == playingItem.path }?.second
            ?: chosenEngine(context, playingItem.path),
        playingVlcAvailable,
        defaultEnginePref(context),
        hdr = playingHdr
    ) else PlayerEngine.MEDIA3
    // 1.4.0 (crash fix): the breadcrumb and the VLC attempt marker
    // are written here — before any player screen composes and the
    // engine is created.
    LaunchedEffect(playingItem?.path, playingEngine) {
        if (playingItem != null) {
            CrashLog.step(context, "engine chosen: $playingEngine for ${playingItem.name}")
            // 2.0.9 (review C1): name the suspect here too — this
            // mark runs before the player screen composes.
            if (playingEngine == PlayerEngine.VLC) markVlcAttempt(context, playingItem.path, hwDecPref(context))
        }
    }
    // Patch 4 (exit confirm): Back only asks on Home. Back from
    // Details, the player, Library or Settings just goes back to
    // Home — the player and details screens have their own
    // BackHandlers, and this one is only enabled on a tab screen.
    BackHandler(enabled = playing == null && details == null && settingsPage == null && !showCrashGate && !showRationale) {
        if (tab != Tab.Home) {
            tab = Tab.Home
        } else {
            showExitDialog = true
        }
    }
    // Patch 5: Back while audio plays in the MiniPlayer asks to
    // exit (the same clean-exit dialog as Back on Home). Now
    // Playing has its own Back (collapse to the MiniPlayer) and
    // the video player has its own Back (exit the player).
    // M19 (2.0.1): Back's meaning used to change whenever audio
    // played — on any tab it asked "Exit?" instead of going Home.
    // Now: on a non-Home tab Back goes Home first (like the tab
    // handler); only on Home does it ask to exit.
    BackHandler(enabled = playingItem != null && !playingItem.isVideo && playingEngine == PlayerEngine.MEDIA3 && !nowPlayingOpen && details == null && settingsPage == null) {
        if (tab != Tab.Home) {
            tab = Tab.Home
        } else {
            showExitDialog = true
        }
    }

    // M3 redesign Phase 1: the whole app now runs under ZephTheme —
    // the new Material 3 expressive design system (M3Theme.kt). The
    // old screens keep rendering (they use their own palettes) until
    // each one is rebuilt in M3, phase by phase. v5: the theme choice
    // (preset + custom seed) and M3 Expressive motion scheme live here.
    ZephTheme(preset = theme, seedHex = themeSeedHex, dark = themeDarkMode) {
        Box(modifier = Modifier.fillMaxSize().background(Bg)) {
            if (showExitDialog) {
                ExitConfirmDialog(
                    onExit = { showExitDialog = false; exitApp() },
                    onStay = { showExitDialog = false }
                )
            } else if (showCrashGate) {
                CrashNoteScreen(
                    note = crashNote,
                    lastStep = CrashLog.lastStep(context),
                    vlcDeath = vlcDeath,
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
            } else if (playingItem != null && (playingItem.isVideo || playingEngine != PlayerEngine.MEDIA3 || nowPlayingOpen)) {
                if (!playing!!.isVideo && playingEngine == PlayerEngine.MEDIA3 && nowPlayingOpen) {
                    // Patch 5: audio on Media3 opens Now Playing,
                    // not the video player (audit A2). Audio that
                    // needs VLC falls through to the video player below.
                    M3NowPlayingScreen(
                        item = playing!!,
                        queue = playList.ifEmpty { all },
                        shuffleOn = shuffleOn,
                        onShuffleChange = { shuffleOn = it },
                        sleep = audioSleep,
                        onPlayNext = { next -> playing = next; playFromStart = false; playStartSpeed = null },
                        onExit = { playing = null; nowPlayingOpen = false; playFromStart = false; playStartSpeed = null },
                        onCollapse = { nowPlayingOpen = false },
                        onEngineFailed = {
                            if (fallbackDoneFor != playing!!.path) {
                                fallbackDoneFor = playing!!.path
                                engineOverride = playing!!.path to PlayerEngine.VLC
                            }
                        },
                        onPlayingChange = { playerIsPlaying = it }
                    )
                } else if (playingEngine == PlayerEngine.VLC) {
                    VlcPlayerScreen(
                        item = playing!!,
                        queue = playList.ifEmpty { all },
                        fromStart = playFromStart,
                        startSpeed = playStartSpeed,
                        onExit = { playing = null; playFromStart = false; playStartSpeed = null },
                        onPlayNext = { next -> playing = next; playFromStart = false; playStartSpeed = null },
                        onSwitchEngine = { engineOverride = playing!!.path to PlayerEngine.MEDIA3 },
                        onEnterPip = enterPip,
                        // v5: the dock's engine door — Auto clears the
                        // override (the default + format rules decide),
                        // Media3 / VLC switch to that engine now.
                        onEngineDoor = { eng ->
                            engineOverride = if (eng == null) null else playing!!.path to eng
                        },
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
                        },
                        // 1.9.1 (P11): "See all" in the dock's Up Next
                        // exits the player and opens the Library.
                        onSeeAllUpNext = { playing = null; tab = Tab.Video },
                        onPlayingChange = { playerIsPlaying = it }
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
                        // v5: the dock's engine door — Auto clears the
                        // override (the default + format rules decide),
                        // Media3 / VLC switch to that engine now.
                        onEngineDoor = { eng ->
                            engineOverride = if (eng == null) null else playing!!.path to eng
                        },
                        // 1.4.5 Patch B: Media3 failed to play this
                        // file — retry it on LibVLC instead of
                        // showing an error. Guarded to once per file,
                        // so the two engines cannot ping-pong.
                        onEngineFailed = {
                            if (fallbackDoneFor != playing!!.path) {
                                fallbackDoneFor = playing!!.path
                                engineOverride = playing!!.path to PlayerEngine.VLC
                            }
                        },
                        // 1.9.1 (P11): "See all" in the dock's Up Next
                        // exits the player and opens the Library.
                        onSeeAllUpNext = { playing = null; tab = Tab.Video },
                        onPlayingChange = { playerIsPlaying = it }
                    )
                }
            } else if (settingsPage == "privacy") {
                // Patch 4 (Jio): Privacy Policy, opened from Settings → About.
                M3PrivacyPolicyScreen(onBack = { settingsPage = null })
            } else if (settingsPage == "licences") {
                // Patch 4 (Jio): open-source licences, opened from Settings → About.
                M3LicencesScreen(onBack = { settingsPage = null })
            } else {
                // 2.0.0: library modes map to tabs — "Music" opens
                // Audio, everything else opens Video (folders and
                // network move to Browse in Patch 4). The rail's
                // onTab sets the mode the same way, so the tab and
                // the mode never disagree.
                val goLibraryMode: (String) -> Unit = { mode ->
                    libraryMode = mode
                    tab = if (mode == "Music") Tab.Audio else Tab.Video
                }
                val m3Content: @Composable (Tab) -> Unit = { currentTab ->
                    when (currentTab) {
                        Tab.Home -> M3HomeScreen(
                            videos = videos, audio = audio,
                            loading = !libraryLoaded, permissionDenied = permissionDenied,
                            onOpen = { item, list -> details = item; detailList = list },
                            onPlay = { item, list -> playing = item; playList = list; playFromStart = false; playStartSpeed = null; nowPlayingOpen = true },
                            onPlayFromStart = { item, list -> playing = item; playList = list; playFromStart = true; playStartSpeed = null; nowPlayingOpen = true },
                            onLibrary = { mode -> goLibraryMode(mode) },
                            onFolders = { tab = Tab.Browse },
                            onRefresh = { refreshNonce++ },
                            onSettings = { tab = Tab.Settings }
                        )
                        Tab.Search -> M3SearchScreen(
                            items = all, search = search, onSearch = { search = it },
                            voiceRequest = voiceSearchRequest,
                            onOpen = { item, list -> details = item; detailList = list },
                            // Patch 6: a song plays straight into Now
                            // Playing (the design's playTracks); a
                            // video opens Details.
                            onPlayFromStart = { item, list -> playing = item; playList = list; playFromStart = true; playStartSpeed = null; nowPlayingOpen = true }
                        )
                        // 2.0.0 (Patch 4): Browse is the design's
                        // screen — storage cards, network rows and
                        // one folder view for local + network. The
                        // old Sources screen is deleted.
                        Tab.Browse -> M3BrowseScreen(
                            videos = videos, audio = audio,
                            onOpen = { item, list -> details = item; detailList = list },
                            onPlayFromStart = { item, list -> playing = item; playList = list; playFromStart = true; playStartSpeed = null; nowPlayingOpen = true },
                            networkServer = networkServer, onNetworkServer = { networkServer = it },
                            networkPath = networkPath, onNetworkPath = { networkPath = it },
                            ftpServer = ftpServer, onFtpServer = { ftpServer = it },
                            ftpPath = ftpPath, onFtpPath = { ftpPath = it },
                            addFormRequest = networkAddFormRequest,
                            onAddNetworkServer = { networkAddFormRequest++ }
                        )
                        // 2.0.0 (Patch 3): the Audio tab is the
                        // design's Music screen.
                        Tab.Audio -> M3AudioScreen(
                            audio = audio, loading = !libraryLoaded,
                            playing = playing,
                            isPlaying = playing != null && playerIsPlaying,
                            shuffleOn = shuffleOn,
                            onShuffleChange = { shuffleOn = it },
                            onPlayFromStart = { item, list -> playing = item; playList = list; playFromStart = true; playStartSpeed = null; nowPlayingOpen = true }
                        )
                        // 2.0.0 (Patch 4): the Video tab is the
                        // design's screen now — folder/network
                        // browsing lives in Browse, so the old
                        // library-mode bridge is gone. Playlists
                        // still uses the library screen until
                        // Patch 5 rebuilds it.
                        Tab.Video -> M3VideoScreen(
                            videos = videos, loading = !libraryLoaded,
                            onOpen = { item, list -> details = item; detailList = list },
                            onSearch = { tab = Tab.Search }
                        )
                        // 2.0.0 (Patch 5): the Playlists tab is the
                        // design's screen now — grid + detail +
                        // New-playlist dialog, backed by LibraryStores.
                        Tab.Playlists -> M3PlaylistsScreen(
                            audio = audio, videos = videos, loading = !libraryLoaded,
                            playing = playing,
                            isPlaying = playing != null && playerIsPlaying,
                            onPlayAudio = { item, list -> playing = item; playList = list; playFromStart = false; playStartSpeed = null; nowPlayingOpen = true },
                            onOpen = { item, list -> details = item; detailList = list }
                        )
                        Tab.Settings -> M3SettingsScreen(
                            onPrivacy = { settingsPage = "privacy" },
                            onLicences = { settingsPage = "licences" },
                            theme = theme,
                            onThemeChange = { theme = it; saveThemePreset(context, it) },
                            themeSeedHex = themeSeedHex,
                            onThemeSeedChange = { themeSeedHex = it; saveThemeSeed(context, it) },
                            themeDarkMode = themeDarkMode,
                            onThemeDarkChange = { themeDarkMode = it; saveThemeDark(context, it) }
                        )
                    }
                }
                // v7: the tab content lives in a SharedTransitionLayout
                // so a video cover can morph into the Details hero
                // (S14), and Details opens as an overlay BESIDE the
                // rail (D1) instead of replacing the whole screen.
                // The overlay keeps the last item while it exits, so
                // the hero can morph back to its card.
                var lastDetails by remember { mutableStateOf<VideoItem?>(null) }
                LaunchedEffect(details) { if (details != null) lastDetails = details }
                val tabSwitch: AnimatedContentTransitionScope<Tab>.() -> ContentTransform = {
                    // 2.0.0 design: fade-through 420ms — opacity holds
                    // at 0 for the first 35% (147ms), then fades in;
                    // the screen scales 0.94→1 on the
                    // emphasized-decelerate easing. The old screen
                    // unmounts instantly — the design has no
                    // screen-exit animation (fix pass: the app used
                    // to add a 200ms fade-out).
                    (fadeIn(
                        keyframes {
                            durationMillis = Motion.ScreenFadeThroughMs
                            0f at 0
                            0f at 147
                            1f at Motion.ScreenFadeThroughMs
                        }
                    ) + scaleIn(
                        Motion.screenFadeThrough(),
                        initialScale = 0.94f
                    )) togetherWith ExitTransition.None
                }
                val detailsPlay: (VideoItem, Boolean) -> Unit = { item, fromStart ->
                    playing = item
                    nowPlayingOpen = true
                    playList = mergeQueue(detailList.ifEmpty { all }, queued)
                    queued = emptyList()
                    playFromStart = fromStart
                    details = null
                }
                // 2.0.0: one rail, every screen size — the phone gets
                // the same compact 88dp rail as the TV (the §11
                // correction); it scrolls on short screens. The
                // rail reads the theme directly (fix pass: it used
                // to be frozen on the Violet palette) and the wifi
                // icon is always shown (the design shows it, 18dp).
                SharedTransitionLayout(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // S14: the SharedTransitionScope lives only as this
                    // lambda's receiver — provide it as the app's own
                    // local so the cover/hero helpers can read it.
                    CompositionLocalProvider(LocalZephSharedTransitionScope provides this) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        RailLayout(
                            labelMode = railLabelMode,
                            tab = tab,
                            onTab = { t ->
                                when (t) {
                                    Tab.Video -> libraryMode = "Videos"
                                    Tab.Audio -> libraryMode = "Music"
                                    Tab.Playlists -> libraryMode = "All"
                                    else -> {}
                                }
                                tab = t
                            },
                            onSearch = { tab = Tab.Search },
                            selectedTabFocus = railTabFocus
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = tabSwitch,
                                    label = "m3TabSwitch"
                                ) { currentTab ->
                                    CompositionLocalProvider(LocalTabAnimatedVisibilityScope provides this) {
                                        m3Content(currentTab)
                                    }
                                }
                                // Patch 5: audio on Media3, collapsed —
                                // the MiniPlayer over the tab content.
                                // Left 112dp from the screen edge is
                                // 24dp inside the content area, past
                                // the 88dp rail; right 24dp, bottom 12dp.
                                val miniPlaying = playing
                                if (miniPlaying != null && !miniPlaying.isVideo && playingEngine == PlayerEngine.MEDIA3 && !nowPlayingOpen) {
                                    Box(
                                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                                    ) {
                                        M3MiniPlayer(
                                            item = miniPlaying,
                                            queue = playList.ifEmpty { all },
                                            shuffleOn = shuffleOn,
                                            sleep = audioSleep,
                                            onPlayNext = { next -> playing = next; playFromStart = false; playStartSpeed = null },
                                            onExpand = { nowPlayingOpen = true },
                                            onPlayingChange = { playerIsPlaying = it },
                                            // M20 (2.0.1): the MiniPlayer's Stop button
                                            // calls onStop after halting the player —
                                            // clear the playing item so the bar itself
                                            // goes away instead of showing a stopped track.
                                            onStop = { playing = null }
                                        )
                                    }
                                }
                                DetailsOverlay(
                                    details = details, lastDetails = lastDetails,
                                    detailList = detailList, all = all, queued = queued,
                                    onPlay = detailsPlay,
                                    onQueue = { item -> queued = mergeQueue(queued, listOf(item)) },
                                    onOpen = { item -> details = item },
                                    onBack = { details = null },
                                    onSeeAll = { details = null; tab = Tab.Video }
                                )
                            }
                        }
                    }
                    } // LocalZephSharedTransitionScope
                }
            }
        }
    }
}

/** v7 (D1/S14): Details opens as an overlay over the tab content —
 * the rail (or the bottom tab strip on a phone) stays visible, and
 * the hero morphs from the video cover that opened it. The last
 * item is kept while the overlay exits, so the hero can morph back
 * to its card. A plain composable: the hero reads the shared
 * transition from the composition local, it needs no scope here. */
@Composable
private fun DetailsOverlay(
    details: VideoItem?,
    lastDetails: VideoItem?,
    detailList: List<VideoItem>,
    all: List<VideoItem>,
    queued: List<VideoItem>,
    onPlay: (VideoItem, Boolean) -> Unit,
    onQueue: (VideoItem) -> Unit,
    onOpen: (VideoItem) -> Unit,
    onBack: () -> Unit,
    // 1.9.1 (D12): "See all" in Up next — exits the overlay and
    // opens the Library. It lives here as a callback because this
    // composable owns neither the details state nor the tab.
    onSeeAll: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = details != null,
        enter = fadeIn(Motion.soft()) + scaleIn(Motion.soft(), initialScale = 0.98f),
        exit = fadeOut(Motion.soft()) + scaleOut(Motion.soft(), targetScale = 0.98f),
        modifier = Modifier.fillMaxSize()
    ) {
        // The AnimatedVisibilityScope is this lambda's receiver —
        // capture it before the Box, whose own scope would shadow it.
        val animScope = this
        val item = lastDetails ?: return@AnimatedVisibility
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            M3DetailsScreen(
                item = item,
                list = detailList.ifEmpty { all },
                onBack = onBack,
                onPlay = onPlay,
                onQueue = onQueue,
                onOpen = onOpen,
                onSeeAll = onSeeAll,
                animatedVisibilityScope = animScope
            )
        }
    }
}

/** Patch 4 (exit confirm): "Exit Zeph Player?" — shown only when
 * Back is pressed on Home. Exit closes the app fully. Stay just
 * closes this dialog. Back while the dialog is open also means
 * Stay.
 *
 * 2.0.3: the old body line ("The app closes fully — no
 * background, no cast receiver left running.") is gone. It read
 * like the app was missing background play and Cast, which is
 * not what it meant — so the dialog is now just the question
 * and the two buttons, nothing confusing. */
@Composable
private fun ExitConfirmDialog(onExit: () -> Unit, onStay: () -> Unit) {
    BackHandler { onStay() }
    // m12 (2.0.1): autofocus the safe default ("Stay") so the
    // dialog opens with a focused button, not an unfocused row.
    // (Back also means Stay, and Exit is one press away.)
    val stayFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stayFocus.requestFocus() } }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Exit Zeph Player?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                M3TvButton("Exit", onClick = onExit)
                M3TvButton("Stay", modifier = Modifier.focusRequester(stayFocus), onClick = onStay)
            }
        }
    }
}
