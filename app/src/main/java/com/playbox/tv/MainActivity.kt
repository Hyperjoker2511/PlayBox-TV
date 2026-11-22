package com.playbox.tv

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
        // The permission request itself lives in PlayBoxApp (rememberLauncherForActivityResult),
        // behind a plain-words rationale shown on first launch — no silent system prompt.
        setContent { PlayBoxApp() }
    }
}

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

@Composable
private fun PlayBoxApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Home) }
    var details by remember { mutableStateOf<VideoItem?>(null) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var playList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    // Batch 1.3 (B3): the list the video was opened from, so "Up next"
    // follows the folder/USB list instead of a random MediaStore video.
    var detailList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    // Batch 1.3 (B1): "Play from start" passes through to the player.
    var playFromStart by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    // Batch 1.3 (B4): Library navigation state lives here, not inside
    // LibraryScreen — opening a video no longer resets mode/folder/scroll.
    var libraryMode by remember { mutableStateOf("Videos") }
    var libraryFolder by remember { mutableStateOf<File?>(null) }
    var librarySafFolder by remember { mutableStateOf<Uri?>(null) }
    val libraryListState = rememberLazyListState()

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

    MaterialTheme(colorScheme = darkColorScheme(primary = Primary, surface = Bg, background = Bg)) {
        Box(modifier = Modifier.fillMaxSize().background(Bg)) {
            if (showRationale) {
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
                PlayerScreen(
                    item = playing!!,
                    queue = playList.ifEmpty { all },
                    fromStart = playFromStart,
                    onExit = { playing = null; playFromStart = false },
                    onPlayNext = { next -> playing = next; playFromStart = false }
                )
            } else if (details != null) {
                DetailsScreen(
                    item = details!!,
                    onBack = { details = null },
                    onPlay = { item, fromStart -> playing = item; playList = detailList.ifEmpty { all }; playFromStart = fromStart; details = null }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Rail(tab = tab, onTab = { tab = it })
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (tab) {
                            Tab.Home -> HomeScreen(
                                videos = videos, audio = audio,
                                loading = !libraryLoaded, permissionDenied = permissionDenied,
                                onOpen = { item, list -> details = item; detailList = list },
                                onFolders = { tab = Tab.Library },
                                onRefresh = { refreshNonce++ }
                            )
                            Tab.Search -> SearchScreen(
                                items = all, search = search, onSearch = { search = it },
                                onOpen = { item, list -> details = item; detailList = list }
                            )
                            Tab.Library -> LibraryScreen(
                                videos = videos, audio = audio, loading = !libraryLoaded,
                                mode = libraryMode, onMode = { libraryMode = it },
                                folder = libraryFolder, onFolder = { libraryFolder = it },
                                safFolder = librarySafFolder, onSafFolder = { librarySafFolder = it },
                                listState = libraryListState,
                                onOpen = { item, list -> details = item; detailList = list },
                                onRefresh = { refreshNonce++ }
                            )
                            Tab.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
