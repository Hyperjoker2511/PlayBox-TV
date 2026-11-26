package com.playbox.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ---------- ZEPH Settings (M3 redesign, Phase 7) ----------
// One column, not two panes: every section in a single scrolling
// list, in the order the user reaches for them — Playback, Subtitles,
// Audio, Network, Appearance, About. Same settings and same prefs
// keys as the old Settings, so switching to the M3 design never
// loses a setting.
// The App design switcher, the Surface (Solid / Liquid) choice and
// the Player UI picker are GONE — there is one design and one
// player dock now. The navigation rail and menu labels stay, because
// they control the rail, not the design.

private fun m3AppVersion(context: Context): Pair<String?, Long> = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName to info.longVersionCode
} catch (_: Exception) {
    null to 0L
}

@Composable
internal fun M3SettingsScreen(
    railMode: RailMode = RailMode.DOCKED,
    onRailModeChange: (RailMode) -> Unit = {},
    labelMode: RailLabelMode = RailLabelMode.FOCUSED,
    onLabelModeChange: (RailLabelMode) -> Unit = {},
    // Patch 4 (Jio): open the in-app Privacy Policy and Licences screens.
    onPrivacy: () -> Unit = {},
    onLicences: () -> Unit = {},
    // v5 (1.9.0): the theme choice — preset + custom seed. MainActivity
    // holds it in state so changing it re-renders the whole app.
    theme: String = ThemePresetViolet,
    onThemeChange: (String) -> Unit = {},
    themeSeedHex: String = "#6750A4",
    onThemeSeedChange: (String) -> Unit = {}
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
    // Patch 4 (Jio): "Keep discoverable when closed" — OFF by default.
    var keepDisc by remember { mutableStateOf(keepDiscoverable(context)) }
    var reduceMotion by remember { mutableStateOf(isReduceMotion(context)) }
    // 1.4.2 (Nova in the 1.4.x line): the default player engine, same
    // pref as the old Settings — Media3 unless changed.
    var defaultEngine by remember { mutableStateOf(defaultEnginePref(context)) }
    // v5 (1.9.0): the custom seed hex the user types, + a validation note.
    var seedInput by remember(themeSeedHex) { mutableStateOf(themeSeedHex) }
    var seedError by remember { mutableStateOf<String?>(null) }

    fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Settings", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            ZephSectionTitle("Playback")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Default speed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    item { ZephFilterChip(label = "${s}x", selected = speed == s, onClick = { speed = s; save { putFloat("default_speed", s) } }) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("New videos start at this speed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Skip", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 30).forEach { s ->
                    item { ZephFilterChip(label = "${s}s", selected = skip == s, onClick = { skip = s; save { putInt("skip_seconds", s) } }) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("How far −10s / +10s and the remote FF/RW keys jump.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            ZephTonalButton(text = if (subsOn) "Subtitles: On" else "Subtitles: Off", onClick = { subsOn = !subsOn; save { putBoolean("subtitles_on", subsOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("Subtitles are on by default for new videos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            ZephTonalButton(text = if (resumeOn) "Resume: On" else "Resume: Off", onClick = { resumeOn = !resumeOn; save { putBoolean("resume_on", resumeOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("When off, every video starts from the beginning.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Player", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ZephFilterChip(label = "Media3", selected = defaultEngine == PlayerEngine.MEDIA3, onClick = { defaultEngine = PlayerEngine.MEDIA3; save { putString("default_engine", "media3") } }) }
                item { ZephFilterChip(label = "VLC", selected = defaultEngine == PlayerEngine.VLC, onClick = { defaultEngine = PlayerEngine.VLC; save { putString("default_engine", "vlc") } }) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Media3 plays the common formats. VLC takes over on its own for FTP/SFTP files and formats Media3 cannot play — and any file can be given its own player on its Details page.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            ZephSectionTitle("Subtitles")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Size", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("S" to 18f, "M" to 24f, "L" to 32f, "XL" to 40f).forEach { (label, size) ->
                    item {
                        ZephFilterChip(label = label, selected = subSize == size, onClick = {
                            subSize = size
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putFloat("size_sp", size).apply()
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Colour", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("white", "yellow", "cyan", "green").forEach { c ->
                    item {
                        ZephFilterChip(label = c.replaceFirstChar { it.uppercase() }, selected = subColor == c, onClick = {
                            subColor = c
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putString("color", c).apply()
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Background", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("black", "none").forEach { b ->
                    item {
                        ZephFilterChip(label = b.replaceFirstChar { it.uppercase() }, selected = subBg == b, onClick = {
                            subBg = b
                            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                                .edit().putString("background", b).apply()
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("These are the same subtitle settings as in the player's gear.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            ZephSectionTitle("Audio")
            Spacer(modifier = Modifier.height(12.dp))
            ZephTonalButton(text = if (eqOn) "Equalizer: On" else "Equalizer: Off", onClick = { eqOn = !eqOn; save { putBoolean("eq_on", eqOn) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("The bands and presets live in the player's gear.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Loudness", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZephTonalButton(text = "−", onClick = { loudness = (loudness - 10).coerceAtLeast(0); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
                Text("$loudness%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(64.dp))
                ZephTonalButton(text = "+", onClick = { loudness = (loudness + 10).coerceAtMost(100); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Boosts quiet audio. 0% is off — this is the night-mode control.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Equalizer and loudness apply to the next video you play.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            ZephSectionTitle("Network")
            Spacer(modifier = Modifier.height(12.dp))
            Text("SMB / NAS servers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            if (smbServers.isEmpty()) {
                Text("No servers yet — add one from Library → Network.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                smbServers.forEach { server ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(formatSmbServer(server), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        ZephTonalButton(text = "Remove", onClick = {
                            SmbNetwork.removeServer(context, server)
                            smbServers = SmbNetwork.servers(context)
                        }, modifier = Modifier.height(44.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text("Add more servers from Library → Network.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("DLNA cast", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("On — your phone can cast videos to Zeph Player. The receiver starts with the app.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            ZephTonalButton(text = if (keepDisc) "Keep discoverable when closed: On" else "Keep discoverable when closed: Off", onClick = {
                keepDisc = !keepDisc
                saveKeepDiscoverable(context, keepDisc)
            })
            Spacer(modifier = Modifier.height(8.dp))
            Text("Off (recommended): the cast receiver stops when the app closes, so nothing runs in the background. On: your phone can still find this TV after the app is closed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Play from URL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("In Library → Network. HTTP, HTTPS, HLS, DASH, RTSP, RTMP and NFS links.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            ZephSectionTitle("Theme", "Colour is your choice — Violet is the default")
            Spacer(modifier = Modifier.height(12.dp))
            // Preset swatches — tap a colour.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemePresets.forEach { (preset, label) ->
                    val selected = theme == preset
                    Box(
                        modifier = Modifier.size(44.dp)
                            .clip(CircleShape)
                            .background(presetPrimary(preset), CircleShape)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                            .clickable { onThemeChange(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check, contentDescription = "$label selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemePresets.forEach { (preset, label) ->
                    item {
                        ZephFilterChip(label = label, selected = theme == preset, onClick = { onThemeChange(preset) })
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            ZephTonalButton(
                text = if (theme == ThemePresetDynamic) "Dynamic colour: On" else "Dynamic colour: Off",
                onClick = {
                    onThemeChange(if (theme == ThemePresetDynamic) ThemePresetViolet else ThemePresetDynamic)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Dynamic follows your device's wallpaper colours (Android 12+). Off goes back to Violet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Custom colour", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZephSearchField(
                    value = seedInput,
                    onValueChange = { seedInput = it; seedError = null },
                    placeholder = "#6750A4",
                    modifier = Modifier.weight(1f)
                )
                ZephButton(text = "Apply", onClick = {
                    val hex = seedInput.trim()
                    if (Regex("^#?[0-9a-fA-F]{6}$").matches(hex)) {
                        val fixed = if (hex.startsWith("#")) hex else "#$hex"
                        onThemeSeedChange(fixed)
                        onThemeChange(ThemePresetCustom)
                        seedError = null
                    } else {
                        seedError = "Use 6 hex digits, like #6750A4."
                    }
                })
            }
            seedError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Any hex colour becomes the app's seed — the tonal scheme is derived from it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            ZephSectionTitle("Appearance")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Navigation rail", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    ZephFilterChip(label = "Docked", selected = railMode == RailMode.DOCKED, onClick = { onRailModeChange(RailMode.DOCKED) })
                }
                item {
                    ZephFilterChip(label = "Floating", selected = railMode == RailMode.FLOATING, onClick = { onRailModeChange(RailMode.FLOATING) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Docked is always there and the content sits beside it. Floating is a capsule over the content — press ◀ to open it, it hides a few seconds after you leave it. Applies on wide screens (TV, landscape); narrow screens keep the top tabs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Text("Menu labels", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    ZephFilterChip(label = "Off", selected = labelMode == RailLabelMode.OFF, onClick = { onLabelModeChange(RailLabelMode.OFF) })
                }
                item {
                    ZephFilterChip(label = "When focused", selected = labelMode == RailLabelMode.FOCUSED, onClick = { onLabelModeChange(RailLabelMode.FOCUSED) })
                }
                item {
                    ZephFilterChip(label = "Always", selected = labelMode == RailLabelMode.ALWAYS, onClick = { onLabelModeChange(RailLabelMode.ALWAYS) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Off shows icons only. When focused shows the label on the item you are on. Always shows every label and keeps the rail expanded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            ZephTonalButton(text = if (reduceMotion) "Reduce motion: On" else "Reduce motion: Off", onClick = { reduceMotion = !reduceMotion; save { putBoolean("reduce_motion", reduceMotion) } })
            Spacer(modifier = Modifier.height(8.dp))
            Text("Skips the Search stretch and the focus zoom. The app still animates, just less.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("The colour themes live in the Theme section above — presets, dynamic colour and a custom seed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            ZephSectionTitle("About")
            Spacer(modifier = Modifier.height(12.dp))
            val (versionName, versionCode) = m3AppVersion(context)
            Text("Zeph Player", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Version ${settingsVersionLabel(versionName, versionCode)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Local and USB video and audio. Seek, fast-forward, rewind, speed, subtitles and audio tracks.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Player: Media3 / ExoPlayer 1.6.1 and LibVLC. DLNA cast from your phone. SMB / NAS folders.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No sign-in. No ads. No Google Play Services.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZephTonalButton(text = "Privacy policy", onClick = onPrivacy)
                ZephTonalButton(text = "Open-source licences", onClick = onLicences)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
