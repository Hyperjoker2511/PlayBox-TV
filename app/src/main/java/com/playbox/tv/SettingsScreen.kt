package com.playbox.tv

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Patch 6 (Qwen Settings, two-pane) ----------
// Left pane: the categories. Right pane: the category's settings.
// Every setting is at most two remote actions away: pick the category,
// then the setting itself.

private const val SETTINGS_PREFS = "playbox_settings"

/** Resume is on by default; when off, playback always starts at 0
 * and the Resume buttons hide. */
internal fun isResumeOn(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean("resume_on", true)

/** Reduce motion is off by default; when on, the Search entry stretch
 * and the focus zoom are skipped. */
internal fun isReduceMotion(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean("reduce_motion", false)

/** Resume applies only when the setting is on and the saved position
 * is worth resuming. (unit-tested) */
internal fun shouldResumeAt(savedMs: Long, durationMs: Long, resumeOn: Boolean): Boolean =
    resumeOn && ResumeStore.shouldResume(savedMs, durationMs)

/** "192.168.1.5 / Movies" for an SMB server row. (unit-tested) */
internal fun formatSmbServer(server: SmbServer): String = "${server.host} / ${server.share}"

/** "1.1.0 (6)" for the About pane. (unit-tested) */
internal fun settingsVersionLabel(versionName: String?, versionCode: Long): String =
    "${versionName ?: "?.?"} ($versionCode)"

private fun appVersion(context: Context): Pair<String?, Long> = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName to info.longVersionCode
} catch (_: Exception) {
    null to 0L
}

@Composable
internal fun SettingsScreen(
    currentDesign: AppDesign = AppDesign.QWEN,
    onDesignChange: (AppDesign) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var category by remember { mutableStateOf("Playback") }
    val firstCategoryFocus = remember { FocusRequester() }

    // Playback
    var skip by remember { mutableStateOf(prefs.getInt("skip_seconds", 10)) }
    var speed by remember { mutableStateOf(prefs.getFloat("default_speed", 1f)) }
    var subsOn by remember { mutableStateOf(prefs.getBoolean("subtitles_on", true)) }
    var resumeOn by remember { mutableStateOf(isResumeOn(context)) }
    // Subtitles style (shared with the gear sheet)
    var subSize by remember { mutableStateOf(subStyle(context).sizeSp) }
    var subColor by remember { mutableStateOf(context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE).getString("color", "white") ?: "white") }
    var subBg by remember { mutableStateOf(context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE).getString("background", "black") ?: "black") }
    // Audio (shared with the gear sheet)
    var eqOn by remember { mutableStateOf(prefs.getBoolean("eq_on", false)) }
    var loudness by remember { mutableStateOf(prefs.getInt("loudness_pct", 0)) }
    // Network
    var smbServers by remember { mutableStateOf(SmbNetwork.servers(context)) }
    // Appearance
    var reduceMotion by remember { mutableStateOf(isReduceMotion(context)) }
    var uiStyle by remember { mutableStateOf(playerUiStyle(context)) }

    fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left pane: the categories.
        LazyColumn(
            modifier = Modifier.width(320.dp).fillMaxHeight().padding(end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Settings", fontSize = 36.sp, color = White) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            listOf("Playback", "Subtitles", "Audio", "Network", "Appearance", "About").forEachIndexed { i, name ->
                item {
                    GlassButton(
                        name,
                        active = category == name,
                        modifier = if (i == 0) Modifier.focusRequester(firstCategoryFocus) else Modifier
                    ) { category = name }
                }
            }
        }
        // Right pane: the selected category.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (category) {
                "Playback" -> {
                    item {
                        SettingsSection("Default speed") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                                    item { GlassButton("${s}x", active = speed == s) { speed = s; save { putFloat("default_speed", s) } } }
                                }
                            }
                            Text("New videos start at this speed.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        SettingsSection("Skip") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(10, 30).forEach { s ->
                                    item { GlassButton("${s}s", active = skip == s) { skip = s; save { putInt("skip_seconds", s) } } }
                                }
                            }
                            Text("How far −10s / +10s and the remote FF/RW keys jump.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        SettingsSection("Subtitles") {
                            GlassButton(if (subsOn) "Subtitles: On" else "Subtitles: Off") {
                                subsOn = !subsOn; save { putBoolean("subtitles_on", subsOn) }
                            }
                            Text("Subtitles are on by default for new videos.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        SettingsSection("Resume") {
                            GlassButton(if (resumeOn) "Resume: On" else "Resume: Off") {
                                resumeOn = !resumeOn; save { putBoolean("resume_on", resumeOn) }
                            }
                            Text("When off, every video starts from the beginning.", fontSize = 16.sp, color = Muted)
                        }
                    }
                }
                "Subtitles" -> {
                    item {
                        SettingsSection("Size") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("S" to 18f, "M" to 24f, "L" to 32f, "XL" to 40f).forEach { (label, size) ->
                                    item {
                                        GlassButton(label, active = subSize == size) {
                                            subSize = size
                                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                                .edit().putFloat("size_sp", size).apply()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SettingsSection("Colour") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("white", "yellow", "cyan", "green").forEach { c ->
                                    item {
                                        GlassButton(c.replaceFirstChar { it.uppercase() }, active = subColor == c) {
                                            subColor = c
                                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                                .edit().putString("color", c).apply()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SettingsSection("Background") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("black", "none").forEach { b ->
                                    item {
                                        GlassButton(b.replaceFirstChar { it.uppercase() }, active = subBg == b) {
                                            subBg = b
                                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                                .edit().putString("background", b).apply()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("These are the same subtitle settings as in the player's gear.", fontSize = 16.sp, color = Muted)
                    }
                }
                "Audio" -> {
                    item {
                        SettingsSection("Equalizer") {
                            GlassButton(if (eqOn) "Equalizer: On" else "Equalizer: Off") {
                                eqOn = !eqOn; save { putBoolean("eq_on", eqOn) }
                            }
                            Text("The bands and presets live in the player's gear.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        SettingsSection("Loudness") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−") { loudness = (loudness - 10).coerceAtLeast(0); save { putInt("loudness_pct", loudness) } }
                                Text("$loudness%", fontSize = 20.sp, color = White, modifier = Modifier.width(70.dp))
                                GlassButton("+") { loudness = (loudness + 10).coerceAtMost(100); save { putInt("loudness_pct", loudness) } }
                            }
                            Text("Boosts quiet audio. 0% is off — this is the night-mode control.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        Text("Equalizer and loudness apply to the next video you play.", fontSize = 16.sp, color = Muted)
                    }
                }
                "Network" -> {
                    item {
                        SettingsSection("SMB / NAS servers") {
                            if (smbServers.isEmpty()) {
                                Text("No servers yet — add one from Library → Network.", fontSize = 18.sp, color = Muted)
                            } else {
                                smbServers.forEach { server ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(formatSmbServer(server), fontSize = 20.sp, color = White, modifier = Modifier.weight(1f))
                                        GlassButton("Remove") {
                                            SmbNetwork.removeServer(context, server)
                                            smbServers = SmbNetwork.servers(context)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                Text("Add more servers from Library → Network.", fontSize = 16.sp, color = Muted)
                            }
                        }
                    }
                    item {
                        SettingsSection("DLNA cast") {
                            Text("On — your phone can cast videos to PlayBox TV.", fontSize = 20.sp, color = White)
                            Text("The receiver starts with the app.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        SettingsSection("Play from URL") {
                            Text("In Library → Network. HTTP, HTTPS, HLS and DASH links.", fontSize = 18.sp, color = Muted)
                        }
                    }
                }
                "Appearance" -> {
                    item {
                        SettingsSection("App design") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                item {
                                    GlassButton("Nova", active = currentDesign == AppDesign.NOVA) {
                                        onDesignChange(AppDesign.NOVA)
                                    }
                                }
                                item {
                                    GlassButton("Qwen", active = currentDesign == AppDesign.QWEN) {
                                        onDesignChange(AppDesign.QWEN)
                                    }
                                }
                            }
                            Text("Nova is the new design, built from scratch — a left rail, lists instead of grids, flat solid surfaces. Qwen is the earlier glass design, kept as an option. Switching takes effect immediately.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        SettingsSection("Player UI") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                item {
                                    GlassButton("Nova", active = uiStyle == PlayerUiStyle.NOVA) {
                                        uiStyle = PlayerUiStyle.NOVA
                                        savePlayerUiStyle(context, PlayerUiStyle.NOVA)
                                    }
                                }
                                item {
                                    GlassButton("Lumen", active = uiStyle == PlayerUiStyle.LUMEN) {
                                        uiStyle = PlayerUiStyle.LUMEN
                                        savePlayerUiStyle(context, PlayerUiStyle.LUMEN)
                                    }
                                }
                                item {
                                    GlassButton("Liquid Glass", active = uiStyle == PlayerUiStyle.LIQUID_GLASS) {
                                        uiStyle = PlayerUiStyle.LIQUID_GLASS
                                        savePlayerUiStyle(context, PlayerUiStyle.LIQUID_GLASS)
                                    }
                                }
                                item {
                                    GlassButton("Prism TV", active = uiStyle == PlayerUiStyle.PRISM) {
                                        uiStyle = PlayerUiStyle.PRISM
                                        savePlayerUiStyle(context, PlayerUiStyle.PRISM)
                                    }
                                }
                            }
                            Text("Four player looks, same player underneath. Nova is the full-width solid bar. Lumen is the slim floating capsule with the full transport row and a mint play button. Liquid Glass is the full-width sheet with an orange seek bar and Prev / Next; Prism TV is the floating capsule with a mint play button.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        SettingsSection("Reduce motion") {
                            GlassButton(if (reduceMotion) "Reduce motion: On" else "Reduce motion: Off") {
                                reduceMotion = !reduceMotion; save { putBoolean("reduce_motion", reduceMotion) }
                            }
                            Text("Skips the Search stretch and the focus zoom. The app still animates, just less.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        Text("More themes arrive after Jio approval — Aurora is the first.", fontSize = 16.sp, color = Muted)
                    }
                }
                "About" -> {
                    item {
                        val (versionName, versionCode) = appVersion(context)
                        Text("PlayBox TV", fontSize = 28.sp, color = White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Version ${settingsVersionLabel(versionName, versionCode)}", fontSize = 20.sp, color = White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Local and USB video and audio. Seek, fast-forward, rewind, speed, subtitles and audio tracks.", fontSize = 20.sp, color = Muted)
                        Text("Player: Media3 / ExoPlayer 1.6.1. DLNA cast from your phone. SMB / NAS folders.", fontSize = 20.sp, color = Muted)
                        Text("No sign-in. No ads. No Google Play Services.", fontSize = 20.sp, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 22.sp, color = White)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
