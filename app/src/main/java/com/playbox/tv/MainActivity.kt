package com.playbox.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.withContext

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
    var search by remember { mutableStateOf("") }

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
    LaunchedEffect(tick) {
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
                                loading = !libraryLoaded, permissionDenied = permissionDenied,
                                onOpen = { details = it },
                                onFolders = { tab = Tab.Library }
                            )
                            Tab.Search -> SearchScreen(
                                items = all, search = search, onSearch = { search = it },
                                onOpen = { details = it }
                            )
                            Tab.Library -> LibraryScreen(
                                videos = videos, audio = audio, loading = !libraryLoaded,
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

