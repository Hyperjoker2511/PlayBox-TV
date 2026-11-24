package com.playbox.tv

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- NOVA Settings (2026-11-23) ----------
// One column, not two panes: every section in a single scrolling
// list, in the order the user reaches for them — Playback, Subtitles,
// Audio, Network, Appearance, About. Same settings and same prefs
// keys as the Qwen Settings, so switching designs never loses a
// setting. Appearance holds the App design switcher (Nova / Qwen)
// and the four player looks (Nova / Lumen / Liquid Glass / Prism).

private fun novaAppVersion(context: Context): Pair<String?, Long> = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName to info.longVersionCode
} catch (_: Exception) {
    null to 0L
}

@Composable
internal fun NovaSettingsScreen(
    currentDesign: AppDesign,
    onDesignChange: (AppDesign) -> Unit,
    railMode: RailMode = RailMode.DOCKED,
    onRailModeChange: (RailMode) -> Unit = {},
    labelMode: RailLabelMode = RailLabelMode.FOCUSED,
    onLabelModeChange: (RailLabelMode) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }

    var skip by remember { mutableStateOf(prefs.getInt("skip_seconds", 10)) }
    var speed by remember { mutableStateOf(prefs.getFloat("default_speed", 1f)) }
    var subsOn by remember { mutableStateOf(prefs.getBoolean("subtitles_on", true)) }
    var resumeOn by remember { mutableStateOf(isResumeOn(context)) }
    var subSize by remember { mutableStateOf(subStyle(context).sizeSp) }
    var subColor by remember { mutableStateOf(context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE).getString("color", "white") ?: "white") }
    var subBg by remember { mutableStateOf(context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE).getString("background", "black") ?: "black") }
    var eqOn by remember { mutableStateOf(prefs.getBoolean("eq_on", false)) }
    var loudness by remember { mutableStateOf(prefs.getInt("loudness_pct", 0)) }
    var smbServers by remember { mutableStateOf(SmbNetwork.servers(context)) }
    var reduceMotion by remember { mutableStateOf(isReduceMotion(context)) }
    var uiStyle by remember { mutableStateOf(playerUiStyle(context)) }
    // 1.4.2 (Nova in the 1.4.x line): the default player engine, same
    // pref as the Qwen Settings — Media3 unless changed.
    var defaultEngine by remember { mutableStateOf(defaultEnginePref(context)) }
    // 1.4.3 (enhanced): the surface finish — Solid (flat, safe) or
    // Liquid (light glass). Same pref the Nova cards read.
    var surface by remember { mutableStateOf(surfaceStyle(context)) }

    fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Settings", fontSize = 28.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            NovaSectionHeader("Playback")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Default speed", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    item { NovaChip("${s}x", active = speed == s) { speed = s; save { putFloat("default_speed", s) } } }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("New videos start at this speed.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Skip", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 30).forEach { s ->
                    item { NovaChip("${s}s", active = skip == s) { skip = s; save { putInt("skip_seconds", s) } } }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("How far −10s / +10s and the remote FF/RW keys jump.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            NovaButton(if (subsOn) "Subtitles: On" else "Subtitles: Off", onClick = { subsOn = !subsOn; save { putBoolean("subtitles_on", subsOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("Subtitles are on by default for new videos.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            NovaButton(if (resumeOn) "Resume: On" else "Resume: Off", onClick = { resumeOn = !resumeOn; save { putBoolean("resume_on", resumeOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("When off, every video starts from the beginning.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Player", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { NovaChip("Media3", active = defaultEngine == PlayerEngine.MEDIA3) { defaultEngine = PlayerEngine.MEDIA3; save { putString("default_engine", "media3") } } }
                item { NovaChip("VLC", active = defaultEngine == PlayerEngine.VLC) { defaultEngine = PlayerEngine.VLC; save { putString("default_engine", "vlc") } } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Media3 plays the common formats. VLC takes over on its own for FTP/SFTP files and formats Media3 cannot play — and any file can be given its own player on its Details page.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            NovaSectionHeader("Subtitles")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Size", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("S" to 18f, "M" to 24f, "L" to 32f, "XL" to 40f).forEach { (label, size) ->
                    item {
                        NovaChip(label, active = subSize == size) {
                            subSize = size
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putFloat("size_sp", size).apply()
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Colour", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("white", "yellow", "cyan", "green").forEach { c ->
                    item {
                        NovaChip(c.replaceFirstChar { it.uppercase() }, active = subColor == c) {
                            subColor = c
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putString("color", c).apply()
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Background", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("black", "none").forEach { b ->
                    item {
                        NovaChip(b.replaceFirstChar { it.uppercase() }, active = subBg == b) {
                            subBg = b
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putString("background", b).apply()
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("These are the same subtitle settings as in the player's gear.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            NovaSectionHeader("Audio")
            Spacer(modifier = Modifier.height(12.dp))
            NovaButton(if (eqOn) "Equalizer: On" else "Equalizer: Off", onClick = { eqOn = !eqOn; save { putBoolean("eq_on", eqOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("The bands and presets live in the player's gear.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Loudness", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaButton("−", onClick = { loudness = (loudness - 10).coerceAtLeast(0); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
                Text("$loudness%", fontSize = 18.sp, color = NovaWhite, modifier = Modifier.width(64.dp))
                NovaButton("+", onClick = { loudness = (loudness + 10).coerceAtMost(100); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Boosts quiet audio. 0% is off — this is the night-mode control.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Equalizer and loudness apply to the next video you play.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            NovaSectionHeader("Network")
            Spacer(modifier = Modifier.height(12.dp))
            Text("SMB / NAS servers", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            if (smbServers.isEmpty()) {
                Text("No servers yet — add one from Library → Network.", fontSize = 16.sp, color = NovaMuted)
            } else {
                smbServers.forEach { server ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(formatSmbServer(server), fontSize = 17.sp, color = NovaWhite, modifier = Modifier.weight(1f))
                        NovaButton("Remove", onClick = {
                            SmbNetwork.removeServer(context, server)
                            smbServers = SmbNetwork.servers(context)
                        }, modifier = Modifier.height(44.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text("Add more servers from Library → Network.", fontSize = 15.sp, color = NovaMuted)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("DLNA cast", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Text("On — your phone can cast videos to PlayBox TV. The receiver starts with the app.", fontSize = 16.sp, color = NovaWhite)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Play from URL", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Text("In Library → Network. HTTP, HTTPS, HLS, DASH, RTSP, RTMP and NFS links.", fontSize = 16.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            NovaSectionHeader("Appearance")
            Spacer(modifier = Modifier.height(12.dp))
            Text("App design", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    NovaChip("Nova", active = currentDesign == AppDesign.NOVA) { onDesignChange(AppDesign.NOVA) }
                }
                item {
                    NovaChip("Qwen", active = currentDesign == AppDesign.QWEN) { onDesignChange(AppDesign.QWEN) }
                }
                item {
                    NovaChip("Aura", active = currentDesign == AppDesign.AURA) { onDesignChange(AppDesign.AURA) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Nova is the new design, built from scratch — a left rail, lists instead of grids, flat solid surfaces. Qwen is the earlier glass design, kept as an option. Aura is the Material 3 lavender design, the third option. Switching takes effect immediately.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Surface", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    NovaChip("Solid", active = surface == SurfaceStyle.SOLID) { surface = SurfaceStyle.SOLID; saveSurfaceStyle(context, SurfaceStyle.SOLID) }
                }
                item {
                    NovaChip("Liquid", active = surface == SurfaceStyle.LIQUID) { surface = SurfaceStyle.LIQUID; saveSurfaceStyle(context, SurfaceStyle.LIQUID) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Solid is flat with no blur — the safe default for the 2 GB box. Liquid is a light glass look (translucent surfaces, no heavy blur). If Liquid ever lags, switch back to Solid.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Navigation rail", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    NovaChip("Docked", active = railMode == RailMode.DOCKED) { onRailModeChange(RailMode.DOCKED) }
                }
                item {
                    NovaChip("Floating", active = railMode == RailMode.FLOATING) { onRailModeChange(RailMode.FLOATING) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Docked is always there and the content sits beside it. Floating is a capsule over the content — press ◀ to open it, it hides a few seconds after you leave it. Applies on wide screens (TV, landscape); narrow screens keep the top tabs.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Menu labels", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    NovaChip("Off", active = labelMode == RailLabelMode.OFF) { onLabelModeChange(RailLabelMode.OFF) }
                }
                item {
                    NovaChip("When focused", active = labelMode == RailLabelMode.FOCUSED) { onLabelModeChange(RailLabelMode.FOCUSED) }
                }
                item {
                    NovaChip("Always", active = labelMode == RailLabelMode.ALWAYS) { onLabelModeChange(RailLabelMode.ALWAYS) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Off shows icons only. When focused shows the label on the item you are on. Always shows every label and keeps the rail expanded.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Player UI", fontSize = 18.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    PlayerUiStyle.NOVA to "Nova",
                    PlayerUiStyle.LUMEN to "Lumen",
                    PlayerUiStyle.LIQUID_GLASS to "Liquid Glass",
                    PlayerUiStyle.PRISM to "Prism TV",
                    PlayerUiStyle.AURA to "Aura"
                ).forEach { (style, label) ->
                    item {
                        NovaChip(label, active = uiStyle == style) {
                            uiStyle = style
                            savePlayerUiStyle(context, style)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Five player looks, same player underneath. Nova is the full-width solid bar. Lumen is the slim floating capsule with a mint play button. Liquid Glass is the full-width sheet with an orange seek bar. Prism TV is the floating capsule with a mint play button. Aura is the lavender bottom UI with a symmetric transport.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(20.dp))
            NovaButton(if (reduceMotion) "Reduce motion: On" else "Reduce motion: Off", onClick = { reduceMotion = !reduceMotion; save { putBoolean("reduce_motion", reduceMotion) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("Skips the Search stretch and the focus zoom. The app still animates, just less.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Text("More themes arrive after Jio approval — Aurora is the first.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            NovaSectionHeader("About")
            Spacer(modifier = Modifier.height(12.dp))
            val (versionName, versionCode) = novaAppVersion(context)
            Text("Zeph Player", fontSize = 24.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Version ${settingsVersionLabel(versionName, versionCode)}", fontSize = 18.sp, color = NovaWhite)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Local and USB video and audio. Seek, fast-forward, rewind, speed, subtitles and audio tracks.", fontSize = 16.sp, color = NovaMuted)
            Text("Player: Media3 / ExoPlayer 1.6.1 and LibVLC. DLNA cast from your phone. SMB / NAS folders.", fontSize = 16.sp, color = NovaMuted)
            Text("No sign-in. No ads. No Google Play Services.", fontSize = 16.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
